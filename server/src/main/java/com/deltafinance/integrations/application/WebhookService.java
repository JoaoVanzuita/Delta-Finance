package com.deltafinance.integrations.application;

import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.integrations.domain.WebhookEvent;
import com.deltafinance.integrations.domain.WebhookEventRepository;
import com.deltafinance.integrations.domain.WebhookSubscription;
import com.deltafinance.integrations.domain.WebhookSubscriptionRepository;
import com.deltafinance.integrations.infrastructure.PluggyProperties;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.UnauthenticatedException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Webhooks opcionais por integração.
 *
 * <p>O endpoint só existe com a feature flag habilitada. O token do path e o segredo do header são
 * validados em tempo constante. O evento é apenas um sinal idempotente para consultar o provedor; o
 * payload bruto nunca é persistido e a sincronização programada continua sendo a reconciliação.
 */
@Service
public class WebhookService {

  private static final String TABLE = "webhook_subscription";
  private static final String SECRET_COLUMN = "secret";

  private final WebhookSubscriptionRepository subscriptionRepository;
  private final WebhookEventRepository eventRepository;
  private final PluggyIntegrationRepository integrationRepository;
  private final WebhookSyncPort webhookSyncPort;
  private final CryptoService cryptoService;
  private final IndexHmacService hmacService;
  private final PluggyProperties properties;
  private final AuditService auditService;
  private final Clock clock;

  public WebhookService(
      WebhookSubscriptionRepository subscriptionRepository,
      WebhookEventRepository eventRepository,
      PluggyIntegrationRepository integrationRepository,
      WebhookSyncPort webhookSyncPort,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      PluggyProperties properties,
      AuditService auditService,
      Clock clock) {
    this.subscriptionRepository = subscriptionRepository;
    this.eventRepository = eventRepository;
    this.integrationRepository = integrationRepository;
    this.webhookSyncPort = webhookSyncPort;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.properties = properties;
    this.auditService = auditService;
    this.clock = clock;
  }

  /** Registro/rotação de inscrição; disponível apenas com a feature flag habilitada. */
  public WebhookRegistration register(
      UUID userId, UUID integrationId, String token, String secret, String[] eventTypes) {
    requireEnabled();
    if (token == null || token.isBlank() || secret == null || secret.isBlank()) {
      throw new ValidationException("webhook", "Informe token e segredo do webhook.");
    }
    PluggyIntegration integration =
        integrationRepository
            .findByIdAndUserId(integrationId, userId)
            .orElseThrow(NotFoundException::new);
    byte[] tokenHash = hmacService.hmac("webhook-token", token.trim());
    String[] types = eventTypes == null ? new String[0] : eventTypes.clone();

    Optional<WebhookSubscription> existing =
        subscriptionRepository.findFirstByIntegrationIdAndUserIdOrderByCreatedAtAsc(
            integration.getId(), userId);
    WebhookSubscription subscription;
    if (existing.isPresent()) {
      subscription = existing.get();
      subscription.updateIdentity(
          tokenHash, encryptSecret(userId, subscription.getId(), secret), types);
    } else {
      UUID id = UUID.randomUUID();
      subscription =
          new WebhookSubscription(
              id, userId, integration.getId(), tokenHash, encryptSecret(userId, id, secret), types);
    }
    subscriptionRepository.save(subscription);
    return new WebhookRegistration(
        subscription.getId(), token.trim(), subscription.getEventTypes());
  }

  /**
   * Recebe e registra um evento idempotente.
   *
   * @throws UnauthenticatedException token/segredo inválidos
   * @throws ConflictException evento já recebido (replay)
   */
  public WebhookReceipt receive(
      String token,
      String secret,
      String eventId,
      String eventType,
      String itemId,
      String rawPayload) {
    requireEnabled();
    if (eventId == null || eventId.isBlank()) {
      throw new ValidationException("eventId", "Informe o eventId.");
    }
    if (token == null || token.isBlank() || secret == null || secret.isBlank()) {
      throw new UnauthenticatedException("Webhook não autenticado.");
    }
    byte[] tokenHash = hmacService.hmac("webhook-token", token);
    WebhookSubscription subscription =
        subscriptionRepository
            .resolveByProviderSubscriptionIdHash(tokenHash)
            .orElseThrow(() -> new UnauthenticatedException("Webhook não autenticado."));
    validateSecret(subscription, secret);

    PluggyIntegration integration =
        integrationRepository
            .findByIdAndUserId(subscription.getIntegrationId(), subscription.getUserId())
            .orElseThrow(() -> new UnauthenticatedException("Webhook não autenticado."));

    byte[] itemHash =
        itemId == null || itemId.isBlank() ? null : hmacService.externalIdHash("pluggy", itemId);
    UUID connectionId =
        itemHash == null
            ? null
            : webhookSyncPort.resolveConnection(integration.getId(), itemHash).orElse(null);
    byte[] payloadHash = hmacService.hmac("webhook-payload", rawPayload == null ? "" : rawPayload);

    Optional<WebhookEvent> existing =
        eventRepository.findByIntegrationIdAndProviderEventId(integration.getId(), eventId);
    if (existing.isPresent()) {
      return new WebhookReceipt(existing.get().getId(), true, false);
    }

    WebhookEvent event =
        new WebhookEvent(
            subscription.getUserId(),
            integration.getId(),
            connectionId,
            eventId,
            eventType,
            itemHash,
            payloadHash,
            clock.now());
    if (connectionId == null) {
      // Sinal para um item não vinculado a este usuário: registra e ignora, sem consultar dados.
      event.markIgnored("CONNECTION_NOT_FOUND");
    }
    try {
      event = eventRepository.save(event);
    } catch (DataIntegrityViolationException race) {
      return new WebhookReceipt(null, true, false);
    }

    boolean queued = false;
    if (connectionId != null) {
      webhookSyncPort.enqueueWebhookProcessing(
          subscription.getUserId(), integration.getId(), connectionId);
      queued = true;
    }
    auditService.record(
        subscription.getUserId(),
        AuditEventType.WEBHOOK_RECEIVED,
        "webhook_event",
        event.getId(),
        Map.of("queued", queued));
    return new WebhookReceipt(event.getId(), false, queued);
  }

  public boolean isEnabled() {
    return properties.isWebhooksEnabled();
  }

  private void requireEnabled() {
    if (!properties.isWebhooksEnabled()) {
      throw new NotFoundException();
    }
  }

  private void validateSecret(WebhookSubscription subscription, String providedSecret) {
    String expected =
        cryptoService.decryptString(
            subscription.getSecretEnc(),
            new CryptoContext(
                subscription.getUserId(), TABLE, SECRET_COLUMN, subscription.getId()));
    boolean matches =
        MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            providedSecret.getBytes(StandardCharsets.UTF_8));
    if (!matches) {
      throw new UnauthenticatedException("Webhook não autenticado.");
    }
  }

  private byte[] encryptSecret(UUID userId, UUID subscriptionId, String secret) {
    return cryptoService.encrypt(
        secret, new CryptoContext(userId, TABLE, SECRET_COLUMN, subscriptionId));
  }

  /** Inscrição retornada apenas no momento do registro (token write-once). */
  public record WebhookRegistration(UUID id, String token, String[] eventTypes) {}

  public record WebhookReceipt(UUID eventId, boolean duplicate, boolean queued) {}
}
