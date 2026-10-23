package com.deltafinance.integrations.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento recebido do provedor.
 *
 * <p>Somente HMACs são persistidos; o payload bruto é descartado após o processamento. A unicidade
 * {@code (integration_id, provider_event_id)} garante idempotência contra replay.
 */
@Entity
@Table(name = "webhook_event")
public class WebhookEvent extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "integration_id", nullable = false)
  private UUID integrationId;

  @Column(name = "connection_id")
  private UUID connectionId;

  @Column(name = "provider_event_id", nullable = false, length = 200)
  private String providerEventId;

  @Column(name = "event_type", length = 120)
  private String eventType;

  @Column(name = "item_id_hash", columnDefinition = "bytea")
  private byte[] itemIdHash;

  @Column(name = "payload_hash", nullable = false, columnDefinition = "bytea")
  private byte[] payloadHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private WebhookEventStatus status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected WebhookEvent() {
    // Hibernate
  }

  public WebhookEvent(
      UUID userId,
      UUID integrationId,
      UUID connectionId,
      String providerEventId,
      String eventType,
      byte[] itemIdHash,
      byte[] payloadHash,
      Instant receivedAt) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
    this.connectionId = connectionId;
    this.providerEventId = Objects.requireNonNull(providerEventId, "providerEventId");
    this.eventType = eventType;
    this.itemIdHash = itemIdHash;
    this.payloadHash = Objects.requireNonNull(payloadHash, "payloadHash");
    this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    this.status = WebhookEventStatus.RECEIVED;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getIntegrationId() {
    return integrationId;
  }

  public UUID getConnectionId() {
    return connectionId;
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public String getEventType() {
    return eventType;
  }

  public byte[] getItemIdHash() {
    return itemIdHash;
  }

  public byte[] getPayloadHash() {
    return payloadHash;
  }

  public WebhookEventStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public void markProcessed(Instant at) {
    this.status = WebhookEventStatus.PROCESSED;
    this.processedAt = Objects.requireNonNull(at, "at");
    this.errorCode = null;
  }

  public void markFailed(String errorCode) {
    this.status = WebhookEventStatus.FAILED;
    this.errorCode = errorCode;
    this.attempts++;
  }

  public void markIgnored(String errorCode) {
    this.status = WebhookEventStatus.IGNORED;
    this.errorCode = errorCode;
  }

  public void prepareProcessing() {
    this.status = WebhookEventStatus.RECEIVED;
    this.errorCode = null;
  }
}
