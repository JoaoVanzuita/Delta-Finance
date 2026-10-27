package com.deltafinance.connections.application;

import com.deltafinance.accounts.application.AccountImportService;
import com.deltafinance.connections.config.SyncProperties;
import com.deltafinance.connections.domain.ConnectionStatus;
import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.connections.domain.SyncRun;
import com.deltafinance.connections.domain.SyncRunRepository;
import com.deltafinance.connections.domain.SyncRunStatus;
import com.deltafinance.integrations.domain.IntegrationStatus;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyDataClient;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
import com.deltafinance.reconciliation.application.InternalMatchService;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.sync.SyncCancellationRegistry;
import com.deltafinance.shared.sync.SyncCancelledException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.ImportStats;
import com.deltafinance.transactions.application.TransactionImportService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executa uma leitura completa de uma conexão.
 *
 * <p>Fluxo: estado do item → metadados de consentimento → contas/cartões → transações da janela.
 * Nenhuma falha apaga dados. Lista vazia produz {@code EMPTY} e não infere remoção. A leitura não
 * força atualização na instituição de origem nem promete sincronização em tempo real.
 */
@Service
public class PluggySyncService {

  private static final Logger LOG = LoggerFactory.getLogger(PluggySyncService.class);
  private static final String CONNECTION_TABLE = "pluggy_connection";

  private final AccountImportService accountImportService;
  private final TransactionImportService transactionImportService;
  private final PluggyAuthClient authClient;
  private final PluggyDataClient dataClient;
  private final SyncStateService stateService;
  private final SyncRunRepository syncRunRepository;
  private final PluggyIntegrationRepository integrationRepository;
  private final PluggyConnectionRepository connectionRepository;
  private final CryptoService cryptoService;
  private final SyncMetrics metrics;
  private final AuditService auditService;
  private final InternalMatchService internalMatchService;
  private final SyncProperties properties;
  private final SyncCancellationRegistry cancellationRegistry;
  private final Clock clock;

  public PluggySyncService(
      AccountImportService accountImportService,
      TransactionImportService transactionImportService,
      PluggyAuthClient authClient,
      PluggyDataClient dataClient,
      SyncStateService stateService,
      SyncRunRepository syncRunRepository,
      PluggyIntegrationRepository integrationRepository,
      PluggyConnectionRepository connectionRepository,
      CryptoService cryptoService,
      SyncMetrics metrics,
      AuditService auditService,
      InternalMatchService internalMatchService,
      SyncProperties properties,
      SyncCancellationRegistry cancellationRegistry,
      Clock clock) {
    this.accountImportService = accountImportService;
    this.transactionImportService = transactionImportService;
    this.authClient = authClient;
    this.dataClient = dataClient;
    this.stateService = stateService;
    this.syncRunRepository = syncRunRepository;
    this.integrationRepository = integrationRepository;
    this.connectionRepository = connectionRepository;
    this.cryptoService = cryptoService;
    this.metrics = metrics;
    this.auditService = auditService;
    this.internalMatchService = internalMatchService;
    this.properties = properties;
    this.cancellationRegistry = cancellationRegistry;
    this.clock = clock;
  }

  /**
   * Sincroniza uma conexão.
   *
   * @param windowFrom início explícito da janela; quando nulo usa 12 meses na primeira carga e a
   *     janela diária com sobreposição nas seguintes
   */
  public SyncResult sync(
      PluggyIntegration integration,
      PluggyConnection connection,
      LocalDate windowFrom,
      String correlationId) {
    Instant startedAt = clock.now();
    ConnectionStatus previousConnectionStatus = connection.getStatus();
    IntegrationStatus previousIntegrationStatus = integration.getStatus();
    SyncRun run =
        new SyncRun(
            integration.getUserId(),
            integration.getId(),
            connection.getId(),
            startedAt,
            correlationId);
    ImportStats stats = ImportStats.empty();
    try {
      if (!connection.hasExternalItemId()) {
        throw new SyncAttemptException(
            new SyncStateService.SyncFailure(
                SyncRunStatus.AUTH_ERROR,
                ConnectionStatus.ITEM_UNAVAILABLE.name(),
                true,
                false,
                null,
                com.deltafinance.integrations.domain.IntegrationStatus.ITEM_UNAVAILABLE,
                ConnectionStatus.ITEM_UNAVAILABLE));
      }

      integration.recordSyncAttempt(startedAt);
      connection.markSyncing();
      // Persistido antes das chamadas externas: um crash não deixa a conexão em SYNCING.
      // Guarde a instância devolvida pelo merge para não reutilizar a versão otimista antiga.
      integration = integrationRepository.save(integration);
      connection = connectionRepository.save(connection);
      cancellationRegistry.checkpoint();

      String itemId = decryptItemId(connection);
      String apiKey = authClient.getApiKey(integration);
      PluggyModels.PluggyItem item;
      try {
        item = dataClient.getItem(apiKey, itemId);
      } catch (PluggyUnauthorizedException exception) {
        // Renovação transparente da API Key no servidor; a chave nunca sai do cache.
        apiKey = authClient.refreshApiKey(integration);
        item = dataClient.getItem(apiKey, itemId);
      }

      SyncStateService.SyncFailure itemFailure = stateService.fromItem(item, startedAt);
      if (itemFailure != null) {
        throw new SyncAttemptException(itemFailure);
      }

      byte[] institutionEnc =
          item.institutionName() == null || item.institutionName().isBlank()
              ? null
              : cryptoService.encrypt(
                  item.institutionName(),
                  new CryptoContext(
                      integration.getUserId(),
                      CONNECTION_TABLE,
                      "institution_name",
                      connection.getId()));
      connection.updateProviderMetadata(
          institutionEnc, item.consentExpiresAt(), item.nextAutoSyncAt());

      cancellationRegistry.checkpoint();
      accountImportService.importAccounts(integration, connection.getId(), itemId);
      cancellationRegistry.checkpoint();
      LocalDate from = windowFrom == null ? defaultWindowFrom(connection) : windowFrom;
      stats = transactionImportService.importRange(integration, connection.getId(), from);
      cancellationRegistry.checkpoint();

      Instant finishedAt = clock.now();
      connection.recordSync(finishedAt, ConnectionStatus.SYNCED);
      connectionRepository.save(connection);
      integration.recordSyncSuccess(finishedAt);
      integrationRepository.save(integration);

      SyncRunStatus status =
          stats.read() == 0
              ? SyncRunStatus.EMPTY
              : stats.failed() > 0 ? SyncRunStatus.PARTIAL : SyncRunStatus.SUCCESS;
      run.finish(
          status,
          stats.read(),
          stats.created(),
          stats.updated(),
          stats.skipped(),
          stats.failed(),
          stats.failed() > 0 ? "IMPORT_MISSING_FIELDS" : null,
          finishedAt);
      syncRunRepository.save(run);
      metrics.recordRun(run);
      // Mantém o dashboard e a tela de reconciliação coerentes sem exigir visita à tela.
      try {
        internalMatchService.suggest(integration.getUserId());
      } catch (RuntimeException suggestionFailure) {
        LOG.warn(
            "Falha ao gerar sugestões de movimento interno connectionId={}: {}",
            connection.getId(),
            suggestionFailure.getClass().getSimpleName());
      }
      auditService.record(
          integration.getUserId(),
          AuditEventType.SYNC_SUCCEEDED,
          "pluggy_connection",
          connection.getId(),
          Map.of("status", status.name(), "readCount", stats.read()));
      LOG.info(
          "Sincronização concluída connectionId={} status={} read={} created={} updated={} correlationId={}",
          connection.getId(),
          status,
          stats.read(),
          stats.created(),
          stats.updated(),
          correlationId);
      return new SyncResult(status, stats, null, run.getId(), run.getCorrelationId());
    } catch (SyncCancelledException cancelled) {
      return interrupted(integration, connection, run, stats, cancelled, correlationId);
    } catch (RuntimeException exception) {
      SyncStateService.SyncFailure failure =
          exception instanceof SyncAttemptException attempt
              ? attempt.failure()
              : stateService.fromException(exception);
      return fail(
          integration,
          connection,
          run,
          stats,
          failure,
          correlationId,
          exception,
          previousConnectionStatus,
          previousIntegrationStatus);
    }
  }

  private SyncResult interrupted(
      PluggyIntegration integration,
      PluggyConnection connection,
      SyncRun run,
      ImportStats stats,
      SyncCancelledException cancelled,
      String correlationId) {
    Instant finishedAt = clock.now();
    boolean timeout = cancelled.reason() == SyncCancellationRegistry.Reason.TIMEOUT;
    String errorCode = timeout ? "JOB_TIMEOUT" : "SYNC_CANCELLED";
    // Recarrega para não sobrescrever um estado alterado por desconexão/remoção concorrente.
    PluggyConnection currentConnection =
        connectionRepository
            .findByIdAndUserId(connection.getId(), integration.getUserId())
            .orElse(connection);
    PluggyIntegration currentIntegration =
        integrationRepository
            .findByIdAndUserId(integration.getId(), integration.getUserId())
            .orElse(integration);
    currentConnection.releaseSync();
    currentIntegration.releaseSync();
    integrationRepository.save(currentIntegration);
    connectionRepository.save(currentConnection);
    run.finish(
        timeout ? SyncRunStatus.TIMEOUT : SyncRunStatus.FAILED,
        stats.read(),
        stats.created(),
        stats.updated(),
        stats.skipped(),
        stats.failed(),
        errorCode,
        finishedAt);
    syncRunRepository.save(run);
    metrics.recordRun(run);
    auditService.record(
        integration.getUserId(),
        AuditEventType.SYNC_FAILED,
        "pluggy_connection",
        connection.getId(),
        Map.of("status", run.getStatus().name(), "code", errorCode));
    LOG.info(
        "Sincronização interrompida connectionId={} code={} correlationId={}",
        connection.getId(),
        errorCode,
        correlationId);
    return new SyncResult(
        run.getStatus(),
        stats,
        new SyncStateService.SyncFailure(
            run.getStatus(), errorCode, false, false, null, null, null),
        run.getId(),
        correlationId);
  }

  private SyncResult fail(
      PluggyIntegration integration,
      PluggyConnection connection,
      SyncRun run,
      ImportStats stats,
      SyncStateService.SyncFailure failure,
      String correlationId,
      RuntimeException cause,
      ConnectionStatus previousConnectionStatus,
      IntegrationStatus previousIntegrationStatus) {
    Instant finishedAt = clock.now();
    stateService.apply(integration, connection, failure, finishedAt);
    if (failure.connectionStatus() == null) {
      connection.restoreStatus(previousConnectionStatus);
    }
    if (failure.integrationStatus() == null) {
      integration.restoreStatus(previousIntegrationStatus);
    }
    integrationRepository.save(integration);
    connectionRepository.save(connection);

    run.finish(
        failure.runStatus(),
        stats.read(),
        stats.created(),
        stats.updated(),
        stats.skipped(),
        stats.failed(),
        failure.errorCode(),
        finishedAt);
    syncRunRepository.save(run);
    metrics.recordRun(run);
    auditService.record(
        integration.getUserId(),
        AuditEventType.SYNC_FAILED,
        "pluggy_connection",
        connection.getId(),
        Map.of("status", failure.runStatus().name(), "code", failure.errorCode()));
    // Log sanitizado: apenas IDs, estado e código; nunca corpo do provedor ou valor financeiro.
    LOG.warn(
        "Sincronização falhou connectionId={} code={} actionRequired={} correlationId={}",
        connection.getId(),
        failure.errorCode(),
        failure.actionRequired(),
        correlationId);
    if (LOG.isDebugEnabled() && cause != null) {
      LOG.debug("Detalhe interno da falha de sincronização {}", cause.getClass().getSimpleName());
    }
    return new SyncResult(failure.runStatus(), stats, failure, run.getId(), correlationId);
  }

  private LocalDate defaultWindowFrom(PluggyConnection connection) {
    if (connection.getLastSyncAt() == null) {
      return clock.today().minusMonths(properties.getInitialWindowMonths());
    }
    return clock.today().minusDays(properties.getDailyWindowDays());
  }

  private String decryptItemId(PluggyConnection connection) {
    return cryptoService.decryptString(
        connection.getExternalItemIdEnc(),
        new CryptoContext(
            connection.getUserId(), CONNECTION_TABLE, "external_item_id", connection.getId()));
  }

  /** Exceção interna que transporta uma decisão já mapeada. */
  static final class SyncAttemptException extends RuntimeException {
    private final SyncStateService.SyncFailure failure;

    SyncAttemptException(SyncStateService.SyncFailure failure) {
      super(failure.errorCode());
      this.failure = failure;
    }

    SyncStateService.SyncFailure failure() {
      return failure;
    }
  }
}
