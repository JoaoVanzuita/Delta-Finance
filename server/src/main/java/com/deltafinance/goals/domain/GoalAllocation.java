package com.deltafinance.goals.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Alocação explícita de uma meta.
 *
 * <p>Exatamente um entre {@code allocatedAmountEnc} (valor fixo) e {@code allocationRatio}
 * (percentual do alvo, 0–1) é preenchido. A ordem ({@code ordinal}) define qual alocação percentual
 * recebe o resíduo de arredondamento.
 */
@Entity
@Table(name = "goal_allocation")
public class GoalAllocation extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "goal_id", nullable = false)
  private UUID goalId;

  @Column(name = "ordinal", nullable = false)
  private int ordinal;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 24)
  private AllocationSourceType sourceType;

  @Column(name = "source_id")
  private UUID sourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "allocation_mode", nullable = false, length = 16)
  private AllocationMode allocationMode;

  @Column(name = "allocated_amount_enc", columnDefinition = "bytea")
  private byte[] allocatedAmountEnc;

  @Column(name = "allocation_ratio", precision = 9, scale = 6)
  private BigDecimal allocationRatio;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  protected GoalAllocation() {
    // Hibernate
  }

  public GoalAllocation(UUID userId, UUID goalId, int ordinal) {
    this(UUID.randomUUID(), userId, goalId, ordinal);
  }

  public GoalAllocation(UUID id, UUID userId, UUID goalId, int ordinal) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.goalId = Objects.requireNonNull(goalId, "goalId");
    this.ordinal = ordinal;
  }

  public void apply(
      AllocationSourceType sourceType,
      UUID sourceId,
      AllocationMode allocationMode,
      byte[] allocatedAmountEnc,
      BigDecimal allocationRatio,
      LocalDate effectiveFrom) {
    this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
    this.sourceId = sourceId;
    this.allocationMode = Objects.requireNonNull(allocationMode, "allocationMode");
    this.allocatedAmountEnc = allocatedAmountEnc;
    this.allocationRatio = allocationRatio;
    this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    this.effectiveTo = null;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getGoalId() {
    return goalId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public AllocationSourceType getSourceType() {
    return sourceType;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public AllocationMode getAllocationMode() {
    return allocationMode;
  }

  public byte[] getAllocatedAmountEnc() {
    return allocatedAmountEnc;
  }

  public BigDecimal getAllocationRatio() {
    return allocationRatio;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }
}
