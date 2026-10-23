package com.deltafinance.connections.application;

import com.deltafinance.accounts.application.AccountImportService;
import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.connections.domain.ConnectionStatus;
import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.integrations.application.IntegrationService;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyCredentialException;
import com.deltafinance.integrations.infrastructure.PluggyDataClient;
import com.deltafinance.integrations.infrastructure.PluggyException;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyProperties;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
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
import com.deltafinance.transactions.application.TransactionImportService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
    long current = connectionRepository.countByIntegrationId(integration.getId());
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

    try {
      accountImportService.importAccounts(integration, connection.getId(), normalizedItemId);
      transactionImportService.importInitial(integration, connection.getId());
      connection.recordSync(clock.now(), ConnectionStatus.SYNCED);
      connectionRepository.save(connection);
      integration.markActive();
      integration.recordSyncSuccess(clock.now());
      integrationService.save(integration);
    } catch (PluggyException exception) {
      ConnectionStatus status =
          exception instanceof PluggyCredentialException
                  || exception instanceof PluggyUnauthorizedException
              ? ConnectionStatus.CREDENTIAL_FAILURE
              : ConnectionStatus.ACTION_REQUIRED;
      connection.recordError("IMPORT_FAILED", status);
      connectionRepository.save(connection);
      integration.recordError(
          "IMPORT_FAILED",
          status == ConnectionStatus.CREDENTIAL_FAILURE
              ? com.deltafinance.integrations.domain.IntegrationStatus.CREDENTIAL_FAILURE
              : com.deltafinance.integrations.domain.IntegrationStatus.ACTION_REQUIRED);
      integrationService.save(integration);
      throw exception;
    }

    auditService.record(
        userId, AuditEventType.CONNECTION_ADDED, "pluggy_connection", connection.getId());
    return toView(connection);
  }

  public ConnectionView updateItemId(UUID userId, UUID connectionId, String itemId) {
    PluggyConnection connection = itemReconciliationService.reconcile(userId, connectionId, itemId);
    return toView(connection);
  }

  public void remove(UUID userId, UUID connectionId) {
    PluggyConnection connection =
        connectionRepository
            .findByIdAndUserId(connectionId, userId)
            .orElseThrow(NotFoundException::new);
    connection.markUnavailable();
    connectionRepository.save(connection);
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
        userId, AuditEventType.CONNECTION_REMOVED, "pluggy_connection", connection.getId());
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
