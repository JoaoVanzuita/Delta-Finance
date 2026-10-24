package com.deltafinance.goals.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Fotografia diária do progresso de uma meta. */
@Entity
@Table(name = "goal_progress_snapshot")
public class GoalProgressSnapshot extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "goal_id", nullable = false)
  private UUID goalId;

  @Column(name = "reference_date", nullable = false)
  private LocalDate referenceDate;

  @Column(name = "current_value_enc", nullable = false, columnDefinition = "bytea")
  private byte[] currentValueEnc;

  @Column(name = "percentage", nullable = false, precision = 9, scale = 4)
  private BigDecimal percentage;

  @Column(name = "remaining_enc", nullable = false, columnDefinition = "bytea")
  private byte[] remainingEnc;

  @Column(name = "rule_version", nullable = false, length = 16)
  private String ruleVersion;

  protected GoalProgressSnapshot() {
    // Hibernate
  }

  public GoalProgressSnapshot(UUID userId, UUID goalId, LocalDate referenceDate) {
    this(UUID.randomUUID(), userId, goalId, referenceDate);
  }

  public GoalProgressSnapshot(UUID id, UUID userId, UUID goalId, LocalDate referenceDate) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.goalId = Objects.requireNonNull(goalId, "goalId");
    this.referenceDate = Objects.requireNonNull(referenceDate, "referenceDate");
  }

  public void apply(
      byte[] currentValueEnc, BigDecimal percentage, byte[] remainingEnc, String ruleVersion) {
    this.currentValueEnc = Objects.requireNonNull(currentValueEnc, "currentValueEnc");
    this.percentage = Objects.requireNonNull(percentage, "percentage");
    this.remainingEnc = Objects.requireNonNull(remainingEnc, "remainingEnc");
    this.ruleVersion = Objects.requireNonNull(ruleVersion, "ruleVersion");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getGoalId() {
    return goalId;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public byte[] getCurrentValueEnc() {
    return currentValueEnc;
  }

  public BigDecimal getPercentage() {
    return percentage;
  }

  public byte[] getRemainingEnc() {
    return remainingEnc;
  }

  public String getRuleVersion() {
    return ruleVersion;
  }
}
