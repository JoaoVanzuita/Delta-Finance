package com.deltafinance.reconciliation.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Correspondência entre débito e crédito do mesmo usuário.
 *
 * <p>Somente {@link InternalMatchStatus#CONFIRMED} exclui os dois lados de receitas/despesas.
 * Nenhuma sugestão é confirmada automaticamente.
 */
@Entity
@Table(name = "internal_match")
public class InternalMatch extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 16)
  private InternalMatchKind kind;

  @Column(name = "debit_transaction_id", nullable = false)
  private UUID debitTransactionId;

  @Column(name = "credit_transaction_id", nullable = false)
  private UUID creditTransactionId;

  @Column(name = "score", nullable = false, precision = 5, scale = 2)
  private BigDecimal score;

  @Column(name = "criteria_version", nullable = false, length = 32)
  private String criteriaVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private InternalMatchStatus status;

  @Column(name = "reason", length = 200)
  private String reason;

  @Column(name = "decided_at")
  private Instant decidedAt;

  protected InternalMatch() {
    // Hibernate
  }

  public InternalMatch(
      UUID userId,
      InternalMatchKind kind,
      UUID debitTransactionId,
      UUID creditTransactionId,
      BigDecimal score,
      String criteriaVersion,
      String reason) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.debitTransactionId = Objects.requireNonNull(debitTransactionId, "debitTransactionId");
    this.creditTransactionId = Objects.requireNonNull(creditTransactionId, "creditTransactionId");
    this.score = Objects.requireNonNull(score, "score");
    this.criteriaVersion = Objects.requireNonNull(criteriaVersion, "criteriaVersion");
    this.reason = reason;
    this.status = InternalMatchStatus.PENDING;
  }

  public UUID getUserId() {
    return userId;
  }

  public InternalMatchKind getKind() {
    return kind;
  }

  public UUID getDebitTransactionId() {
    return debitTransactionId;
  }

  public UUID getCreditTransactionId() {
    return creditTransactionId;
  }

  public BigDecimal getScore() {
    return score;
  }

  public String getCriteriaVersion() {
    return criteriaVersion;
  }

  public InternalMatchStatus getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public void confirm(Instant at) {
    this.status = InternalMatchStatus.CONFIRMED;
    this.decidedAt = Objects.requireNonNull(at, "at");
    this.reason = "Confirmado pelo usuário.";
  }

  public void reject(Instant at) {
    this.status = InternalMatchStatus.REJECTED;
    this.decidedAt = Objects.requireNonNull(at, "at");
    this.reason = "Rejeitado pelo usuário.";
  }

  public void markConfirmedForManualTransfer(Instant at) {
    this.status = InternalMatchStatus.CONFIRMED;
    this.decidedAt = at;
    this.reason = "Transferência manual confirmada na criação.";
  }
}
