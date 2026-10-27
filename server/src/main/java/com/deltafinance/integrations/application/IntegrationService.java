package com.deltafinance.integrations.application;

import com.deltafinance.integrations.domain.IntegrationStatus;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyProperties;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.ValidationException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Configuração pessoal do Meu Pluggy.
 *
 * <p>Credenciais novas só substituem as antigas após validação bem-sucedida no provedor. O Client
 * Secret é write-only: nunca retorna em DTO, log ou cache.
 */
@Service
public class IntegrationService {

  private static final String TABLE = "pluggy_integration";
  private static final String CLIENT_ID_COLUMN = "client_id";
  private static final String CLIENT_SECRET_COLUMN = "client_secret";

  private final PluggyIntegrationRepository integrationRepository;
  private final ConnectionCounter connectionCounter;
  private final PluggyAuthClient authClient;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final PluggyProperties properties;

  public IntegrationService(
      PluggyIntegrationRepository integrationRepository,
      ConnectionCounter connectionCounter,
      PluggyAuthClient authClient,
      CryptoService cryptoService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      PluggyProperties properties) {
    this.integrationRepository = integrationRepository;
    this.connectionCounter = connectionCounter;
    this.authClient = authClient;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.properties = properties;
  }

  public IntegrationView get(UUID userId) {
    PluggyIntegration integration = getOrCreate(userId);
    return view(integration);
  }

  /** Visão da integração sem criar registro quando ainda não existe. */
  public java.util.Optional<IntegrationView> findView(UUID userId) {
    return integrationRepository.findMeuPluggy(userId).map(this::view);
  }

  /**
   * Valida as credenciais no provedor e só então substitui as existentes.
   *
   * <p>Em caso de falha, a configuração anterior permanece intacta.
   */
  public IntegrationView saveCredentials(UUID userId, String clientId, String clientSecret) {
    if (clientId == null || clientId.isBlank()) {
      throw new ValidationException("clientId", "Informe o Client ID.");
    }
    if (clientSecret == null || clientSecret.isBlank()) {
      throw new ValidationException("clientSecret", "Informe o Client Secret.");
    }

    // Falha do provedor propaga antes de qualquer alteração local.
    authClient.validateCredentials(clientId.trim(), clientSecret);

    PluggyIntegration integration = getOrCreate(userId);
    authClient.invalidate(integration);
    integration.updateCredentials(
        cryptoService.encrypt(
            clientId.trim(),
            new CryptoContext(userId, TABLE, CLIENT_ID_COLUMN, integration.getId())),
        cryptoService.encrypt(
            clientSecret,
            new CryptoContext(userId, TABLE, CLIENT_SECRET_COLUMN, integration.getId())),
        cryptoService.activeKeyVersion());

    long connections = connectionCounter.countByIntegration(integration.getId());
    if (connections > 0) {
      integration.markActive();
    } else {
      integration.markIncomplete();
    }
    integrationRepository.save(integration);
    auditService.record(
        userId, AuditEventType.INTEGRATION_UPDATED, "pluggy_integration", integration.getId());
    return view(integration);
  }

  public ValidationView validate(String clientId, String clientSecret) {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      throw new ValidationException("clientId", "Informe Client ID e Client Secret.");
    }
    authClient.validateCredentials(clientId.trim(), clientSecret);
    return new ValidationView(true, "Credenciais válidas.", false);
  }

  /**
   * Desconecta a integração. Por padrão preserva o histórico e marca as conexões como
   * indisponíveis; a remoção definitiva de dados é tratada no fluxo de privacidade (T143).
   */
  public void disconnect(UUID userId) {
    var integration = integrationRepository.findMeuPluggy(userId);
    if (integration.isEmpty()) {
      return;
    }
    PluggyIntegration entity = integration.get();
    authClient.invalidate(entity);
    entity.clearCredentials();
    entity.markIncomplete();
    integrationRepository.save(entity);
    // Módulos não se conhecem: connections reage ao evento para interromper syncs.
    eventPublisher.publishEvent(new IntegrationDisconnectedEvent(userId, entity.getId()));
  }

  /** Persistência interna para serviços do mesmo fluxo (conexão/sincronização). */
  public void save(PluggyIntegration integration) {
    integrationRepository.save(integration);
  }

  public java.util.Optional<PluggyIntegration> findMeuPluggy(UUID userId) {
    return integrationRepository.findMeuPluggy(userId);
  }

  public PluggyIntegration requireIntegration(UUID userId) {
    return integrationRepository
        .findMeuPluggy(userId)
        .orElseThrow(
            () ->
                new ConflictException(
                    "INTEGRATION_REQUIRED", "Configure a integração Meu Pluggy primeiro."));
  }

  private PluggyIntegration getOrCreate(UUID userId) {
    return integrationRepository
        .findMeuPluggy(userId)
        .orElseGet(() -> integrationRepository.save(new PluggyIntegration(userId)));
  }

  private IntegrationView view(PluggyIntegration integration) {
    String masked = null;
    if (integration.getClientIdEnc() != null) {
      String clientId =
          cryptoService.decryptString(
              integration.getClientIdEnc(),
              new CryptoContext(
                  integration.getUserId(), TABLE, CLIENT_ID_COLUMN, integration.getId()));
      masked = mask(clientId);
    }
    long connections = connectionCounter.countByIntegration(integration.getId());
    IntegrationStatus status = integration.getStatus();
    if (connections == 0 && status == IntegrationStatus.SYNCED) {
      status = IntegrationStatus.INCOMPLETE;
    }
    return new IntegrationView(
        integration.getId(),
        integration.getProvider(),
        masked,
        status.name(),
        integration.getLastSyncAttemptAt(),
        integration.getLastSuccessfulSyncAt(),
        integration.getLastErrorCode(),
        connections,
        properties.getConnectionLimit(),
        properties.getConnectionLimitCheckedAt());
  }

  static String mask(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if (value.length() <= 8) {
      return "••••";
    }
    return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
  }
}
