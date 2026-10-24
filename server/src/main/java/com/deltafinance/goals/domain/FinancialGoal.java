package com.deltafinance.goals.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Meta financeira.
 *
 * <p>Nome, descrição, valor-alvo e dívida inicial são cifrados. Tipo, estado, regra, prazo e {@code
 * changed_at} ficam em claro. A meta nunca movimenta dinheiro, cria ordens ou executa ação
 * bancária.
 */
@Entity
@Table(name = "financial_goal")
public class FinancialGoal extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "name_enc", nullable = false, columnDefinition = "bytea")
  private byte[] nameEnc;

  @Column(name = "description_enc", columnDefinition = "bytea")
  private byte[] descriptionEnc;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 32)
  private GoalType type;

  @Column(name = "target_amount_enc", nullable = false, columnDefinition = "bytea")
  private byte[] targetAmountEnc;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private GoalStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "progress_rule", nullable = false, length = 24)
  private GoalProgressRule progressRule;

  @Column(name = "initial_value_enc", columnDefinition = "bytea")
  private byte[] initialValueEnc;

  @Column(name = "changed_at")
  private Instant changedAt;

  protected FinancialGoal() {
    // Hibernate
  }

  public FinancialGoal(UUID userId) {
    this(UUID.randomUUID(), userId);
  }

  public FinancialGoal(UUID id, UUID userId) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.status = GoalStatus.ACTIVE;
  }

  public void apply(
      byte[] nameEnc,
      byte[] descriptionEnc,
      GoalType type,
      byte[] targetAmountEnc,
      LocalDate targetDate,
      GoalProgressRule progressRule,
      byte[] initialValueEnc) {
    this.nameEnc = Objects.requireNonNull(nameEnc, "nameEnc");
    this.descriptionEnc = descriptionEnc;
    this.type = Objects.requireNonNull(type, "type");
    this.targetAmountEnc = Objects.requireNonNull(targetAmountEnc, "targetAmountEnc");
    this.targetDate = targetDate;
    this.progressRule = Objects.requireNonNull(progressRule, "progressRule");
    this.initialValueEnc = initialValueEnc;
  }

  public void markChanged(Instant changedAt) {
    this.changedAt = Objects.requireNonNull(changedAt, "changedAt");
    if (this.status != GoalStatus.PAUSED && this.status != GoalStatus.ARCHIVED) {
      this.status = GoalStatus.CHANGED;
    }
  }

  public void acknowledgeChange() {
    this.changedAt = null;
    if (this.status == GoalStatus.CHANGED) {
      this.status = GoalStatus.ACTIVE;
    }
  }

  public void changeStatus(GoalStatus status) {
    this.status = Objects.requireNonNull(status, "status");
  }

  public UUID getUserId() {
    return userId;
  }

  public byte[] getNameEnc() {
    return nameEnc;
  }

  public byte[] getDescriptionEnc() {
    return descriptionEnc;
  }

  public GoalType getType() {
    return type;
  }

  public byte[] getTargetAmountEnc() {
    return targetAmountEnc;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public GoalStatus getStatus() {
    return status;
  }

  public GoalProgressRule getProgressRule() {
    return progressRule;
  }

  public byte[] getInitialValueEnc() {
    return initialValueEnc;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
