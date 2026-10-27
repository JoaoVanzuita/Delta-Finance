package com.deltafinance.connections.application;

import com.deltafinance.accounts.application.AccountImportService;
import com.deltafinance.accounts.application.AccountQueryService;
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
import com.deltafinance.integrations.infrastructure.PluggyProperties;
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
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.ImportStats;
import com.deltafinance.transactions.application.TransactionImportService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro e manutenção de conexões (itemIds).
 *
 * <p>Cada item é validado no provedor antes de ser salvo. O ID local é estável; o itemId pode ser
 * atualizado sem trocar o vínculo de contas/transações. Remover uma conexão interrompe novas
 * sincronizações e preserva o histórico.
 */
@Service
public class ConnectionService {

  private static final String CONNECTION_TABLE = "pluggy_connection";
  private static final String ITEM_ID_COLUMN = "external_item_id";

  private final PluggyConnectionRepository connectionRepository;
  private final IntegrationService integrationService;
  private final PluggyAuthClient authClient;
  private final PluggyDataClient dataClient;
  private final PluggyProperties properties;
  private final AccountImportService accountImportService;
  private final TransactionImportService transactionImportService;
  private final AccountQueryService accountQueryService;
  private final CryptoService cryptoService;
  private final IndexHmacService hmacService;
  private final AuditService auditService;
  private final ItemReconciliationService itemReconciliationService;
  private final SyncJobService syncJobService;
  private final SyncRunRepository syncRunRepository;
  private final SyncStateService stateService;
  private final InternalMatchService internalMatchService;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  public ConnectionService(
      PluggyConnectionRepository connectionRepository,
      IntegrationService integrationService,
      PluggyAuthClient authClient,
      PluggyDataClient dataClient,
      PluggyProperties properties,
      AccountImportService accountImportService,
      TransactionImportService transactionImportService,
      AccountQueryService accountQueryService,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      AuditService auditService,
      ItemReconciliationService itemReconciliationService,
      SyncJobService syncJobService,
      SyncRunRepository syncRunRepository,
      SyncStateService stateService,
      InternalMatchService internalMatchService,
      JdbcTemplate jdbcTemplate,
      Clock clock) {
    this.connectionRepository = connectionRepository;
    this.integrationService = integrationService;
    this.authClient = authClient;
    this.dataClient = dataClient;
    this.properties = properties;
    this.accountImportService = accountImportService;
    this.transactionImportService = transactionImportService;
    this.accountQueryService = accountQueryService;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.auditService = auditService;
    this.itemReconciliationService = itemReconciliationService;
    this.syncJobService = syncJobService;
    this.syncRunRepository = syncRunRepository;
    this.stateService = stateService;
    this.internalMatchService = internalMatchService;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  public List<ConnectionView> list(UUID userId) {
    return connectionRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
        .map(this::toView)
        .toList();
  }

  public ConnectionView add(UUID userId, String itemId) {
    String normalizedItemId = requireItemId(itemId);
    PluggyIntegration integration = integrationService.requireIntegration(userId);
    if (!integration.hasCredentials()) {
      throw new ConflictException(
          ErrorCode.INTEGRATION_INCOMPLETE, "Salve as credenciais da integração primeiro.");
    }

    byte[] itemHash = hmacService.externalIdHash("pluggy", normalizedItemId);
    if (connectionRepository
        .findByIntegrationIdAndExternalItemIdHash(integration.getId(), itemHash)
        .isPresent()) {
      throw new ConflictException(ErrorCode.ITEM_ALREADY_CONNECTED, "Este item já está conectado.");
    }
    long current =
        connectionRepository.countByIntegrationIdAndStatusNot(
            integration.getId(), ConnectionStatus.ITEM_UNAVAILABLE);
    if (current >= properties.getConnectionLimit()) {
      throw new ConflictException(
          ErrorCode.CONNECTION_LIMIT_REACHED,
          "Limite de "
              + properties.getConnectionLimit()
              + " conexões da modalidade Meu Pluggy atingido (verificado em "
              + properties.getConnectionLimitCheckedAt()
              + ").");
    }

    PluggyModels.PluggyItem item = fetchItem(integration, normalizedItemId);
    Instant startedAt = clock.now();
    PluggyConnection connection = new PluggyConnection(userId, integration.getId());
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
    connection.activate(
        itemEnc, itemHash, institutionEnc, item.consentExpiresAt(), item.nextAutoSyncAt());
    connectionRepository.save(connection);
    SyncRun run =
        new SyncRun(userId, integration.getId(), connection.getId(), startedAt, "connection-add");

    SyncStateService.SyncFailure itemFailure = stateService.fromItem(item, startedAt);
    if (itemFailure != null) {
      stateService.apply(integration, connection, itemFailure, startedAt);
      connectionRepository.save(connection);
      integrationService.save(integration);
      finishRun(run, itemFailure.runStatus(), 0, 0, 0, 0, 0, itemFailure.errorCode());
      auditService.record(
          userId, AuditEventType.CONNECTION_ADDED, "pluggy_connection", connection.getId());
      return toView(connection);
    }

    try {
      accountImportService.importAccounts(integration, connection.getId(), normalizedItemId);
      ImportStats stats = transactionImportService.importInitial(integration, connection.getId());
      connection.recordSync(clock.now(), ConnectionStatus.SYNCED);
      connectionRepository.save(connection);
      integration.markActive();
      integration.recordSyncSuccess(clock.now());
      integrationService.save(integration);
      try {
        internalMatchService.suggest(userId);
      } catch (RuntimeException ignored) {
        // A importação continua válida mesmo se a geração de sugestões falhar.
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
        userId, AuditEventType.CONNECTION_ADDED, "pluggy_connection", connection.getId());
    return toView(connection);
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

  public ConnectionView updateItemId(UUID userId, UUID connectionId, String itemId) {
    PluggyConnection connection = itemReconciliationService.reconcile(userId, connectionId, itemId);
    return toView(connection);
  }

  /**
   * Remove uma conexão. Por padrão preserva contas, transações e histórico operacional; com {@code
   * deleteHistory} apaga os dados financeiros vinculados e os registros operacionais da conexão.
   */
  @Transactional
  public void remove(UUID userId, UUID connectionId, boolean deleteHistory) {
    PluggyConnection connection =
        connectionRepository
            .findByIdAndUserId(connectionId, userId)
            .orElseThrow(NotFoundException::new);
    syncJobService.cancelActiveForConnection(userId, connectionId);

    int deletedAccounts = 0;
    if (deleteHistory) {
      deletedAccounts =
          jdbcTemplate.update(
              "delete from financial_account where user_id = ? and connection_id = ?",
              userId,
              connectionId);
      jdbcTemplate.update(
          "delete from sync_job where user_id = ? and connection_id = ?", userId, connectionId);
      jdbcTemplate.update(
          "delete from sync_run where user_id = ? and connection_id = ?", userId, connectionId);
      jdbcTemplate.update(
          "delete from pluggy_connection where user_id = ? and id = ?", userId, connectionId);
    } else {
      connection.markUnavailable();
      connectionRepository.save(connection);
      // A conexão deixa de sincronizar; as contas importadas permanecem para histórico, mas saem
      // dos saldos correntes para não duplicar valores de uma conta já coberta por outra conexão.
      jdbcTemplate.update(
          "update financial_account set status = 'UNAVAILABLE', updated_at = ?"
              + " where user_id = ? and connection_id = ?",
          java.sql.Timestamp.from(clock.now()),
          userId,
          connectionId);
    }

    integrationService
        .findMeuPluggy(userId)
        .ifPresent(
            integration -> {
              long remaining =
                  connectionRepository
                      .findByIntegrationIdOrderByCreatedAtAsc(integration.getId())
                      .stream()
                      .filter(
                          item ->
                              item.getStatus() == ConnectionStatus.ACTIVE
                                  || item.getStatus() == ConnectionStatus.SYNCED)
                      .count();
              if (remaining == 0) {
                integration.markIncomplete();
                integrationService.save(integration);
              }
            });
    auditService.record(
        userId,
        AuditEventType.CONNECTION_REMOVED,
        "pluggy_connection",
        connection.getId(),
        java.util.Map.of(
            "deleteHistory", deleteHistory, "deletedAccounts", Math.max(0, deletedAccounts)));
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

  private ConnectionView toView(PluggyConnection connection) {
    String itemId = null;
    if (connection.getExternalItemIdEnc() != null) {
      itemId =
          cryptoService.decryptString(
              connection.getExternalItemIdEnc(),
              new CryptoContext(
                  connection.getUserId(), CONNECTION_TABLE, ITEM_ID_COLUMN, connection.getId()));
    }
    String institution = null;
    if (connection.getInstitutionNameEnc() != null) {
      institution =
          cryptoService.decryptString(
              connection.getInstitutionNameEnc(),
              new CryptoContext(
                  connection.getUserId(),
                  CONNECTION_TABLE,
                  "institution_name",
                  connection.getId()));
    }
    return new ConnectionView(
        connection.getId(),
        maskItemId(itemId),
        institution,
        connection.getStatus().name(),
        connection.getConsentExpiresAt(),
        connection.getLastSyncAt(),
        connection.getLastErrorCode(),
        accountQueryService.countByConnection(connection.getId()));
  }

  private static String maskItemId(String itemId) {
    if (itemId == null || itemId.isBlank()) {
      return null;
    }
    if (itemId.length() <= 8) {
      return "••••";
    }
    return itemId.substring(0, 4) + "…" + itemId.substring(itemId.length() - 4);
  }

  private static String requireItemId(String itemId) {
    if (itemId == null || itemId.isBlank()) {
      throw new ValidationException("itemId", "Informe o itemId da conexão.");
    }
    return itemId.trim();
  }
}
