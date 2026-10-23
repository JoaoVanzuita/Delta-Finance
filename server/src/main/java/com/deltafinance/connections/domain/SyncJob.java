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
 * Job durável da fila de sincronização.
 *
 * <p>O claim usa {@code FOR UPDATE SKIP LOCKED} na consulta nativa do repositório. O payload nunca
 * contém dados financeiros; apenas um HMAC opcional de integridade.
 */
@Entity
@Table(name = "sync_job")
public class SyncJob extends BaseEntity {

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "integration_id")
  private UUID integrationId;

  @Column(name = "connection_id")
  private UUID connectionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "job_type", nullable = false, length = 32)
  private SyncJobType jobType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private SyncJobStatus status;

  @Column(name = "run_after", nullable = false)
  private Instant runAfter;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "locked_by", length = 120)
  private String lockedBy;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "payload_hash", columnDefinition = "bytea")
  private byte[] payloadHash;

  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  protected SyncJob() {
    // Hibernate
  }

  public SyncJob(
      UUID userId,
      UUID integrationId,
      UUID connectionId,
      SyncJobType jobType,
      Instant runAfter,
      int maxAttempts) {
    this.userId = userId;
    this.integrationId = integrationId;
    this.connectionId = connectionId;
    this.jobType = Objects.requireNonNull(jobType, "jobType");
    this.status = SyncJobStatus.QUEUED;
    this.runAfter = Objects.requireNonNull(runAfter, "runAfter");
    this.maxAttempts = Math.max(1, maxAttempts);
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

  public SyncJobType getJobType() {
    return jobType;
  }

  public SyncJobStatus getStatus() {
    return status;
  }

  public Instant getRunAfter() {
    return runAfter;
  }

  public int getAttempts() {
    return attempts;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public String getLockedBy() {
    return lockedBy;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }

  public byte[] getPayloadHash() {
    return payloadHash;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public void attachPayloadHash(byte[] payloadHash) {
    this.payloadHash = payloadHash;
  }

  public void markRunning(String worker, Instant at) {
    this.status = SyncJobStatus.RUNNING;
    this.lockedBy = worker;
    this.lockedAt = Objects.requireNonNull(at, "at");
    this.attempts++;
  }

  public void markSucceeded() {
    this.status = SyncJobStatus.SUCCEEDED;
    this.lockedBy = null;
    this.lockedAt = null;
    this.lastErrorCode = null;
  }

  public void markFailed(String errorCode) {
    this.status = SyncJobStatus.FAILED;
    this.lockedBy = null;
    this.lockedAt = null;
    this.lastErrorCode = errorCode;
  }

  /** Reagenda mantendo o job no estado {@code RETRY}. */
  public void scheduleRetry(String errorCode, Instant nextRunAt) {
    this.status = SyncJobStatus.RETRY;
    this.lockedBy = null;
    this.lockedAt = null;
    this.lastErrorCode = errorCode;
    this.runAfter = Objects.requireNonNull(nextRunAt, "nextRunAt");
  }

  /** Devolve o job à fila sem consumir uma tentativa (lock ocupado). */
  public void release(Instant nextRunAt) {
    this.status = SyncJobStatus.RETRY;
    this.lockedBy = null;
    this.lockedAt = null;
    this.runAfter = Objects.requireNonNull(nextRunAt, "nextRunAt");
  }

  public boolean canRetry() {
    return attempts < maxAttempts;
  }

  public boolean isActive() {
    return status == SyncJobStatus.QUEUED
        || status == SyncJobStatus.RUNNING
        || status == SyncJobStatus.RETRY;
  }
}
