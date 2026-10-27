package com.deltafinance.connections.application;

import com.deltafinance.accounts.application.AccountImportService;
import com.deltafinance.connections.domain.ConnectionStatus;
import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.connections.domain.SyncRun;
import com.deltafinance.connections.domain.SyncRunRepository;
import com.deltafinance.connections.domain.SyncRunStatus;
import com.deltafinance.integrations.application.IntegrationService;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyDataClient;
import com.deltafinance.integrations.infrastructure.PluggyException;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
import com.deltafinance.reconciliation.application.InternalMatchService;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.ImportStats;
import com.deltafinance.transactions.application.TransactionImportService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reconcilia a troca de itemId sem perder histórico.
 *
 * <p>O registro local da conexão (e portanto contas, transações e histórico de identidade) nunca
 * muda de ID. Apenas o itemId cifrado/HMAC é substituído, após validação no provedor, e a
 * importação idempotente confirma que os dados já conhecidos não são duplicados.
 */
@Service
public class ItemReconciliationService {

  private static final String CONNECTION_TABLE = "pluggy_connection";
  private static final String ITEM_ID_COLUMN = "external_item_id";

  private final PluggyConnectionRepository connectionRepository;
  private final IntegrationService integrationService;
  private final PluggyAuthClient authClient;
  private final PluggyDataClient dataClient;
  private final AccountImportService accountImportService;
  private final TransactionImportService transactionImportService;
  private final CryptoService cryptoService;
  private final IndexHmacService hmacService;
  private final AuditService auditService;
  private final SyncRunRepository syncRunRepository;
  private final SyncStateService stateService;
  private final InternalMatchService internalMatchService;
  private final Clock clock;

  public ItemReconciliationService(
      PluggyConnectionRepository connectionRepository,
      IntegrationService integrationService,
      PluggyAuthClient authClient,
      PluggyDataClient dataClient,
      AccountImportService accountImportService,
      TransactionImportService transactionImportService,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      AuditService auditService,
      SyncRunRepository syncRunRepository,
      SyncStateService stateService,
      InternalMatchService internalMatchService,
      Clock clock) {
    this.connectionRepository = connectionRepository;
    this.integrationService = integrationService;
    this.authClient = authClient;
    this.dataClient = dataClient;
    this.accountImportService = accountImportService;
    this.transactionImportService = transactionImportService;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.auditService = auditService;
    this.syncRunRepository = syncRunRepository;
    this.stateService = stateService;
    this.internalMatchService = internalMatchService;
    this.clock = clock;
  }

  /** Valida o novo item, atualiza o mesmo registro local e reimporta sem duplicar. */
  public PluggyConnection reconcile(UUID userId, UUID connectionId, String newItemId) {
    if (newItemId == null || newItemId.isBlank()) {
      throw new com.deltafinance.shared.errors.ValidationException(
          "itemId", "Informe o itemId da conexão.");
    }
    String normalizedItemId = newItemId.trim();
    PluggyConnection connection =
        connectionRepository
            .findByIdAndUserId(connectionId, userId)
            .orElseThrow(NotFoundException::new);
    PluggyIntegration integration = integrationService.requireIntegration(userId);

    byte[] newHash = hmacService.externalIdHash("pluggy", normalizedItemId);
    boolean changed = !hmacService.matches(connection.getExternalItemIdHash(), newHash);
    if (changed) {
      connectionRepository
          .findByIntegrationIdAndExternalItemIdHash(integration.getId(), newHash)
          .filter(other -> !other.getId().equals(connection.getId()))
          .ifPresent(
              other -> {
                throw new ConflictException(
                    ErrorCode.ITEM_ALREADY_CONNECTED, "Este item já está conectado.");
              });
    }

    // Valida antes de alterar qualquer campo persistido.
    PluggyModels.PluggyItem item = fetchItem(integration, normalizedItemId);
    Instant startedAt = clock.now();
    SyncRun run =
        new SyncRun(userId, integration.getId(), connection.getId(), startedAt, "item-reconcile");
    byte[] itemEnc =
        cryptoService.encrypt(
            normalizedItemId,
            new CryptoContext(userId, CONNECTION_TABLE, ITEM_ID_COLUMN, connection.getId()));
    byte[] institutionEnc =
        item.institutionName() == null || item.institutionName().isBlank()
            ? null
            : cryptoService.encrypt(
                item.institutionName(),
                new CryptoContext(
                    userId, CONNECTION_TABLE, "institution_name", connection.getId()));
    connection.updateItemId(itemEnc, newHash, institutionEnc);
    connection.updateProviderMetadata(
        institutionEnc, item.consentExpiresAt(), item.nextAutoSyncAt());

    SyncStateService.SyncFailure itemFailure = stateService.fromItem(item, startedAt);
    if (itemFailure != null) {
      stateService.apply(integration, connection, itemFailure, startedAt);
      connectionRepository.save(connection);
      integrationService.save(integration);
      finishRun(run, itemFailure.runStatus(), 0, 0, 0, 0, 0, itemFailure.errorCode());
      auditService.record(
          userId,
          AuditEventType.ITEM_ID_CHANGED,
          "pluggy_connection",
          connection.getId(),
          Map.of("changed", changed));
      return connection;
    }

    try {
      accountImportService.importAccounts(integration, connection.getId(), normalizedItemId);
      ImportStats stats =
          transactionImportService.importInitial(integration, connection.getId(), true);
      connection.recordSync(clock.now(), ConnectionStatus.SYNCED);
      connectionRepository.save(connection);
      integration.markActive();
      integration.recordSyncSuccess(clock.now());
      integrationService.save(integration);
      try {
        internalMatchService.suggest(userId);
      } catch (RuntimeException ignored) {
        // A reconexão continua válida mesmo se a geração de sugestões falhar.
      }
      finishRun(
          run,
          stats.read() == 0
              ? SyncRunStatus.EMPTY
              : stats.failed() > 0 ? SyncRunStatus.PARTIAL : SyncRunStatus.SUCCESS,
          stats.read(),
          stats.created(),
          stats.updated(),
          stats.skipped(),
          stats.failed(),
          stats.failed() > 0 ? "IMPORT_MISSING_FIELDS" : null);
    } catch (PluggyException exception) {
      SyncStateService.SyncFailure failure = stateService.fromException(exception);
      stateService.apply(integration, connection, failure, clock.now());
      connectionRepository.save(connection);
      integrationService.save(integration);
      finishRun(run, failure.runStatus(), 0, 0, 0, 0, 0, failure.errorCode());
      throw exception;
    }

    auditService.record(
        userId,
        AuditEventType.ITEM_ID_CHANGED,
        "pluggy_connection",
        connection.getId(),
        Map.of("changed", changed));
    return connection;
  }

  private void finishRun(
      SyncRun run,
      SyncRunStatus status,
      int read,
      int created,
      int updated,
      int skipped,
      int failed,
      String errorCode) {
    run.finish(status, read, created, updated, skipped, failed, errorCode, clock.now());
    syncRunRepository.save(run);
  }

  private PluggyModels.PluggyItem fetchItem(PluggyIntegration integration, String itemId) {
    String apiKey = authClient.getApiKey(integration);
    try {
      return dataClient.getItem(apiKey, itemId);
    } catch (PluggyUnauthorizedException exception) {
      apiKey = authClient.refreshApiKey(integration);
      return dataClient.getItem(apiKey, itemId);
    }
  }
}
