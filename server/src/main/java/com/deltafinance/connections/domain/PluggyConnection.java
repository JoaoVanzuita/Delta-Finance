package com.deltafinance.connections.domain;

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
 * Conexão (item) de uma integração.
 *
 * <p>O {@code id} local é estável: mudanças de itemId atualizam apenas os campos externos, mantendo
 * contas, transações e histórico vinculados a este registro.
 */
@Entity
@Table(name = "pluggy_connection")
public class PluggyConnection extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "integration_id", nullable = false)
  private UUID integrationId;

  @Column(name = "external_item_id_enc", columnDefinition = "bytea")
  private byte[] externalItemIdEnc;

  @Column(name = "external_item_id_hash", columnDefinition = "bytea")
  private byte[] externalItemIdHash;

  @Column(name = "institution_name_enc", columnDefinition = "bytea")
  private byte[] institutionNameEnc;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private ConnectionStatus status;

  @Column(name = "consent_expires_at")
  private Instant consentExpiresAt;

  @Column(name = "next_auto_sync_at")
  private Instant nextAutoSyncAt;

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures;

  protected PluggyConnection() {
    // Hibernate
  }

  public PluggyConnection(UUID userId, UUID integrationId) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
    this.status = ConnectionStatus.INCOMPLETE;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getIntegrationId() {
    return integrationId;
  }

  public byte[] getExternalItemIdEnc() {
    return externalItemIdEnc;
  }

  public byte[] getExternalItemIdHash() {
    return externalItemIdHash;
  }

  public byte[] getInstitutionNameEnc() {
    return institutionNameEnc;
  }

  public ConnectionStatus getStatus() {
    return status;
  }

  public Instant getConsentExpiresAt() {
    return consentExpiresAt;
  }

  public Instant getNextAutoSyncAt() {
    return nextAutoSyncAt;
  }

  public Instant getLastSyncAt() {
    return lastSyncAt;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public int getConsecutiveFailures() {
    return consecutiveFailures;
  }

  public boolean hasExternalItemId() {
    return externalItemIdEnc != null && externalItemIdHash != null;
  }

  public void activate(
      byte[] itemIdEnc,
      byte[] itemIdHash,
      byte[] institutionNameEnc,
      Instant consentExpiresAt,
      Instant nextAutoSyncAt) {
    this.externalItemIdEnc = Objects.requireNonNull(itemIdEnc, "itemIdEnc");
    this.externalItemIdHash = Objects.requireNonNull(itemIdHash, "itemIdHash");
    this.institutionNameEnc = institutionNameEnc;
    this.consentExpiresAt = consentExpiresAt;
    this.nextAutoSyncAt = nextAutoSyncAt;
    this.lastErrorCode = null;
    this.consecutiveFailures = 0;
    this.status = ConnectionStatus.ACTIVE;
  }

  public void updateItemId(byte[] itemIdEnc, byte[] itemIdHash, byte[] institutionNameEnc) {
    this.externalItemIdEnc = Objects.requireNonNull(itemIdEnc, "itemIdEnc");
    this.externalItemIdHash = Objects.requireNonNull(itemIdHash, "itemIdHash");
    this.institutionNameEnc = institutionNameEnc;
    this.lastErrorCode = null;
    this.status = ConnectionStatus.ACTIVE;
  }

  public void recordSync(Instant at, ConnectionStatus newStatus) {
    this.lastSyncAt = Objects.requireNonNull(at, "at");
    this.status = Objects.requireNonNull(newStatus, "newStatus");
    if (newStatus == ConnectionStatus.SYNCED || newStatus == ConnectionStatus.ACTIVE) {
      this.consecutiveFailures = 0;
      this.lastErrorCode = null;
    }
  }

  /** Sinaliza início de leitura sem alterar a data da última sincronização bem-sucedida. */
  public void markSyncing() {
    if (this.status != ConnectionStatus.AUTHORIZATION_EXPIRED
        && this.status != ConnectionStatus.ACTION_REQUIRED
        && this.status != ConnectionStatus.CREDENTIAL_FAILURE) {
      this.status = ConnectionStatus.SYNCING;
    }
  }

  /** Libera um estado {@code SYNCING} interrompido, preservando o último estado conhecido. */
  public void releaseSync() {
    if (this.status == ConnectionStatus.SYNCING) {
      this.status = this.lastSyncAt == null ? ConnectionStatus.ACTIVE : ConnectionStatus.SYNCED;
    }
  }

  /** Atualiza metadados fornecidos pelo item durante uma leitura bem-sucedida. */
  public void updateProviderMetadata(
      byte[] institutionNameEnc, Instant consentExpiresAt, Instant nextAutoSyncAt) {
    if (institutionNameEnc != null) {
      this.institutionNameEnc = institutionNameEnc;
    }
    this.consentExpiresAt = consentExpiresAt;
    this.nextAutoSyncAt = nextAutoSyncAt;
  }

  public void recordError(String errorCode, ConnectionStatus newStatus) {
    this.lastErrorCode = errorCode;
    this.status = newStatus;
    this.consecutiveFailures++;
  }

  /** Registra falha temporária sem rebaixar o último estado conhecido. */
  public void recordTransientError(String errorCode, Instant at) {
    this.lastErrorCode = errorCode;
    this.consecutiveFailures++;
  }

  /** Restaura o último estado conhecido após falha temporária. */
  public void restoreStatus(ConnectionStatus previousStatus) {
    if (previousStatus != null) {
      this.status = previousStatus;
    }
  }

  /** Remove a credencial de acesso (itemId) sem apagar o histórico local. */
  public void markUnavailable() {
    this.status = ConnectionStatus.ITEM_UNAVAILABLE;
    this.externalItemIdEnc = null;
    this.externalItemIdHash = null;
    this.lastErrorCode = null;
  }
}
