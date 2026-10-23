package com.deltafinance.connections.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro imutável de uma tentativa de sincronização.
 *
 * <p>É a fonte de verdade operacional do usuário: status, contadores e código de erro sanitizado.
 */
@Entity
@Table(name = "sync_run")
public class SyncRun extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "integration_id", nullable = false)
  private UUID integrationId;

  @Column(name = "connection_id")
  private UUID connectionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private SyncRunStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "read_count", nullable = false)
  private int readCount;

  @Column(name = "created_count", nullable = false)
  private int createdCount;

  @Column(name = "updated_count", nullable = false)
  private int updatedCount;

  @Column(name = "skipped_count", nullable = false)
  private int skippedCount;

  @Column(name = "failed_count", nullable = false)
  private int failedCount;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  protected SyncRun() {
    // Hibernate
  }

  public SyncRun(
      UUID userId, UUID integrationId, UUID connectionId, Instant startedAt, String correlationId) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
    this.connectionId = connectionId;
    this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    this.correlationId = correlationId;
    this.status = SyncRunStatus.FAILED;
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

  public SyncRunStatus getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public int getReadCount() {
    return readCount;
  }

  public int getCreatedCount() {
    return createdCount;
  }

  public int getUpdatedCount() {
    return updatedCount;
  }

  public int getSkippedCount() {
    return skippedCount;
  }

  public int getFailedCount() {
    return failedCount;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public void finish(
      SyncRunStatus status,
      int readCount,
      int createdCount,
      int updatedCount,
      int skippedCount,
      int failedCount,
      String errorCode,
      Instant finishedAt) {
    this.status = Objects.requireNonNull(status, "status");
    this.readCount = Math.max(0, readCount);
    this.createdCount = Math.max(0, createdCount);
    this.updatedCount = Math.max(0, updatedCount);
    this.skippedCount = Math.max(0, skippedCount);
    this.failedCount = Math.max(0, failedCount);
    this.errorCode = errorCode;
    this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
    this.durationMs = Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
  }
}
