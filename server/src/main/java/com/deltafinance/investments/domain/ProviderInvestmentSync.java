package com.deltafinance.investments.domain;

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
 * Diagnóstico de cobertura de investimentos por conexão.
 *
 * <p>Distingue "a instituição não compartilha investimentos" de "falha de leitura" e de "ainda não
 * verificado". Não contém valores financeiros.
 */
@Entity
@Table(name = "provider_investment_sync")
public class ProviderInvestmentSync extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "connection_id", nullable = false)
  private UUID connectionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false, length = 32)
  private ProviderInvestmentScope scope;

  @Column(name = "resource_count", nullable = false)
  private int resourceCount;

  @Column(name = "available_resource_count", nullable = false)
  private int availableResourceCount;

  @Column(name = "pending_resource_count", nullable = false)
  private int pendingResourceCount;

  @Column(name = "unavailable_resource_count", nullable = false)
  private int unavailableResourceCount;

  @Column(name = "resources_collected_at")
  private Instant resourcesCollectedAt;

  @Column(name = "last_synced_at", nullable = false)
  private Instant lastSyncedAt;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  protected ProviderInvestmentSync() {
    // Hibernate
  }

  public ProviderInvestmentSync(UUID userId, UUID connectionId) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
    this.scope = ProviderInvestmentScope.UNKNOWN;
  }

  public void apply(
      ProviderInvestmentScope scope,
      int resourceCount,
      int availableResourceCount,
      int pendingResourceCount,
      int unavailableResourceCount,
      Instant resourcesCollectedAt,
      Instant lastSyncedAt,
      String errorCode) {
    this.scope = Objects.requireNonNull(scope, "scope");
    this.resourceCount = resourceCount;
    this.availableResourceCount = availableResourceCount;
    this.pendingResourceCount = pendingResourceCount;
    this.unavailableResourceCount = unavailableResourceCount;
    this.resourcesCollectedAt = resourcesCollectedAt;
    this.lastSyncedAt = Objects.requireNonNull(lastSyncedAt, "lastSyncedAt");
    this.errorCode = errorCode;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getConnectionId() {
    return connectionId;
  }

  public ProviderInvestmentScope getScope() {
    return scope;
  }

  public int getResourceCount() {
    return resourceCount;
  }

  public int getAvailableResourceCount() {
    return availableResourceCount;
  }

  public int getPendingResourceCount() {
    return pendingResourceCount;
  }

  public int getUnavailableResourceCount() {
    return unavailableResourceCount;
  }

  public Instant getResourcesCollectedAt() {
    return resourcesCollectedAt;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
