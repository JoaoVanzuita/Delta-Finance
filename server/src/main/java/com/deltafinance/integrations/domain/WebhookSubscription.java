package com.deltafinance.integrations.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Inscrição de webhook opcional por integração.
 *
 * <p>O identificador público da inscrição (token do path) e o segredo do header são persistidos
 * como HMAC/ciphertext; nenhum dos dois é retornado à interface.
 */
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscription extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "integration_id", nullable = false)
  private UUID integrationId;

  @Column(name = "provider_subscription_id_hash", nullable = false, columnDefinition = "bytea")
  private byte[] providerSubscriptionIdHash;

  @Column(name = "secret_enc", nullable = false, columnDefinition = "bytea")
  private byte[] secretEnc;

  @Column(name = "event_types", columnDefinition = "text[]")
  private String[] eventTypes;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private WebhookSubscriptionStatus status;

  protected WebhookSubscription() {
    // Hibernate
  }

  public WebhookSubscription(
      UUID userId,
      UUID integrationId,
      byte[] providerSubscriptionIdHash,
      byte[] secretEnc,
      String[] eventTypes) {
    init(userId, integrationId, providerSubscriptionIdHash, secretEnc, eventTypes);
  }

  public WebhookSubscription(
      UUID id,
      UUID userId,
      UUID integrationId,
      byte[] providerSubscriptionIdHash,
      byte[] secretEnc,
      String[] eventTypes) {
    super(id);
    init(userId, integrationId, providerSubscriptionIdHash, secretEnc, eventTypes);
  }

  private void init(
      UUID userId,
      UUID integrationId,
      byte[] providerSubscriptionIdHash,
      byte[] secretEnc,
      String[] eventTypes) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
    this.providerSubscriptionIdHash =
        Objects.requireNonNull(providerSubscriptionIdHash, "providerSubscriptionIdHash");
    this.secretEnc = Objects.requireNonNull(secretEnc, "secretEnc");
    this.eventTypes = eventTypes == null ? new String[0] : eventTypes.clone();
    this.status = WebhookSubscriptionStatus.ACTIVE;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getIntegrationId() {
    return integrationId;
  }

  public byte[] getProviderSubscriptionIdHash() {
    return providerSubscriptionIdHash;
  }

  public byte[] getSecretEnc() {
    return secretEnc;
  }

  public String[] getEventTypes() {
    return eventTypes == null ? new String[0] : eventTypes.clone();
  }

  public WebhookSubscriptionStatus getStatus() {
    return status;
  }

  public void updateSecret(byte[] secretEnc, String[] eventTypes) {
    this.secretEnc = Objects.requireNonNull(secretEnc, "secretEnc");
    this.eventTypes = eventTypes == null ? new String[0] : eventTypes.clone();
    this.status = WebhookSubscriptionStatus.ACTIVE;
  }

  /** Rotaciona token e segredo mantendo o mesmo registro local. */
  public void updateIdentity(
      byte[] providerSubscriptionIdHash, byte[] secretEnc, String[] eventTypes) {
    this.providerSubscriptionIdHash =
        Objects.requireNonNull(providerSubscriptionIdHash, "providerSubscriptionIdHash");
    updateSecret(secretEnc, eventTypes);
  }

  public void disable() {
    this.status = WebhookSubscriptionStatus.DISABLED;
  }

  @Override
  public String toString() {
    return "WebhookSubscription{id="
        + getId()
        + ", integrationId="
        + integrationId
        + ", status="
        + status
        + ", eventTypes="
        + Arrays.toString(getEventTypes())
        + '}';
  }
}
