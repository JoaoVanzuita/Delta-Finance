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
 * Integração pessoal Meu Pluggy de um usuário.
 *
 * <p>Client ID/Secret são cifrados. A API Key temporária nunca é persistida nem exposta. O estado
 * {@link IntegrationStatus#ACTIVE} só é atribuído quando há credenciais válidas e ao menos uma
 * conexão validada.
 */
@Entity
@Table(name = "pluggy_integration")
public class PluggyIntegration extends BaseEntity {

  public static final String PROVIDER = "MEU_PLUGGY";

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "provider", nullable = false, length = 32)
  private String provider;

  @Column(name = "client_id_enc", columnDefinition = "bytea")
  private byte[] clientIdEnc;

  @Column(name = "client_secret_enc", columnDefinition = "bytea")
  private byte[] clientSecretEnc;

  @Column(name = "encryption_key_version")
  private Integer encryptionKeyVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private IntegrationStatus status;

  @Column(name = "last_sync_attempt_at")
  private Instant lastSyncAttemptAt;

  @Column(name = "last_successful_sync_at")
  private Instant lastSuccessfulSyncAt;

  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  protected PluggyIntegration() {
    // Hibernate
  }

  public PluggyIntegration(UUID userId) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.provider = PROVIDER;
    this.status = IntegrationStatus.INCOMPLETE;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getProvider() {
    return provider;
  }

  public byte[] getClientIdEnc() {
    return clientIdEnc;
  }

  public byte[] getClientSecretEnc() {
    return clientSecretEnc;
  }

  public Integer getEncryptionKeyVersion() {
    return encryptionKeyVersion;
  }

  public IntegrationStatus getStatus() {
    return status;
  }

  public Instant getLastSyncAttemptAt() {
    return lastSyncAttemptAt;
  }

  public Instant getLastSuccessfulSyncAt() {
    return lastSuccessfulSyncAt;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public boolean hasCredentials() {
    return clientIdEnc != null && clientSecretEnc != null;
  }

  public void updateCredentials(byte[] clientIdEnc, byte[] clientSecretEnc, int keyVersion) {
    this.clientIdEnc = Objects.requireNonNull(clientIdEnc, "clientIdEnc");
    this.clientSecretEnc = Objects.requireNonNull(clientSecretEnc, "clientSecretEnc");
    this.encryptionKeyVersion = keyVersion;
    this.lastErrorCode = null;
  }

  public void clearCredentials() {
    this.clientIdEnc = null;
    this.clientSecretEnc = null;
    this.encryptionKeyVersion = null;
  }

  public void markIncomplete() {
    this.status = IntegrationStatus.INCOMPLETE;
  }

  public void markActive() {
    this.status = IntegrationStatus.ACTIVE;
  }

  public void recordSyncAttempt(Instant at) {
    this.lastSyncAttemptAt = Objects.requireNonNull(at, "at");
    this.status = IntegrationStatus.SYNCING;
  }

  public void recordSyncSuccess(Instant at) {
    this.lastSuccessfulSyncAt = Objects.requireNonNull(at, "at");
    this.lastErrorCode = null;
    this.status = IntegrationStatus.SYNCED;
  }

  public void recordError(String errorCode, IntegrationStatus newStatus) {
    this.lastErrorCode = errorCode;
    this.status = newStatus;
  }
}
