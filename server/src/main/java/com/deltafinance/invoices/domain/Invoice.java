package com.deltafinance.invoices.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Fatura de cartão por ciclo, com totais cifrados. */
@Entity
@Table(name = "invoice")
public class Invoice extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "card_account_id", nullable = false)
  private UUID cardAccountId;

  @Column(name = "cycle_start", nullable = false)
  private LocalDate cycleStart;

  @Column(name = "cycle_end", nullable = false)
  private LocalDate cycleEnd;

  @Column(name = "closing_date", nullable = false)
  private LocalDate closingDate;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private InvoiceStatus status;

  @Column(name = "has_adjustments", nullable = false)
  private boolean hasAdjustments;

  @Column(name = "adjustment_count", nullable = false)
  private int adjustmentCount;

  @Column(name = "total_enc", nullable = false, columnDefinition = "bytea")
  private byte[] totalEnc;

  @Column(name = "payments_total_enc", nullable = false, columnDefinition = "bytea")
  private byte[] paymentsTotalEnc;

  @Column(name = "outstanding_enc", nullable = false, columnDefinition = "bytea")
  private byte[] outstandingEnc;

  @Column(name = "applied_credit_enc", nullable = false, columnDefinition = "bytea")
  private byte[] appliedCreditEnc;

  @Column(name = "minimum_payment_enc", columnDefinition = "bytea")
  private byte[] minimumPaymentEnc;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 16)
  private InvoiceSource source;

  protected Invoice() {
    // Hibernate
  }

  public Invoice(
      UUID userId,
      UUID cardAccountId,
      LocalDate cycleStart,
      LocalDate cycleEnd,
      LocalDate closingDate,
      LocalDate dueDate,
      InvoiceSource source,
      byte[] zeroEnc) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.cardAccountId = Objects.requireNonNull(cardAccountId, "cardAccountId");
    this.cycleStart = Objects.requireNonNull(cycleStart, "cycleStart");
    this.cycleEnd = Objects.requireNonNull(cycleEnd, "cycleEnd");
    this.closingDate = Objects.requireNonNull(closingDate, "closingDate");
    this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
    this.status = InvoiceStatus.OPEN;
    this.source = Objects.requireNonNull(source, "source");
    this.totalEnc = Objects.requireNonNull(zeroEnc, "zeroEnc");
    this.paymentsTotalEnc = zeroEnc;
    this.outstandingEnc = zeroEnc;
    this.appliedCreditEnc = zeroEnc;
  }

  public Invoice(
      UUID id,
      UUID userId,
      UUID cardAccountId,
      LocalDate cycleStart,
      LocalDate cycleEnd,
      LocalDate closingDate,
      LocalDate dueDate,
      InvoiceSource source,
      byte[] zeroEnc) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.cardAccountId = Objects.requireNonNull(cardAccountId, "cardAccountId");
    this.cycleStart = Objects.requireNonNull(cycleStart, "cycleStart");
    this.cycleEnd = Objects.requireNonNull(cycleEnd, "cycleEnd");
    this.closingDate = Objects.requireNonNull(closingDate, "closingDate");
    this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
    this.status = InvoiceStatus.OPEN;
    this.source = Objects.requireNonNull(source, "source");
    this.totalEnc = Objects.requireNonNull(zeroEnc, "zeroEnc");
    this.paymentsTotalEnc = zeroEnc;
    this.outstandingEnc = zeroEnc;
    this.appliedCreditEnc = zeroEnc;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getCardAccountId() {
    return cardAccountId;
  }

  public LocalDate getCycleStart() {
    return cycleStart;
  }

  public LocalDate getCycleEnd() {
    return cycleEnd;
  }

  public LocalDate getClosingDate() {
    return closingDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public boolean isHasAdjustments() {
    return hasAdjustments;
  }

  public int getAdjustmentCount() {
    return adjustmentCount;
  }

  public byte[] getTotalEnc() {
    return totalEnc;
  }

  public byte[] getPaymentsTotalEnc() {
    return paymentsTotalEnc;
  }

  public byte[] getOutstandingEnc() {
    return outstandingEnc;
  }

  public byte[] getAppliedCreditEnc() {
    return appliedCreditEnc;
  }

  public byte[] getMinimumPaymentEnc() {
    return minimumPaymentEnc;
  }

  public InvoiceSource getSource() {
    return source;
  }

  public void applyTotals(
      InvoiceStatus status,
      byte[] totalEnc,
      byte[] paymentsTotalEnc,
      byte[] outstandingEnc,
      byte[] appliedCreditEnc) {
    this.status = Objects.requireNonNull(status, "status");
    this.totalEnc = Objects.requireNonNull(totalEnc, "totalEnc");
    this.paymentsTotalEnc = Objects.requireNonNull(paymentsTotalEnc, "paymentsTotalEnc");
    this.outstandingEnc = Objects.requireNonNull(outstandingEnc, "outstandingEnc");
    this.appliedCreditEnc = Objects.requireNonNull(appliedCreditEnc, "appliedCreditEnc");
  }

  public void markAdjustment() {
    this.hasAdjustments = true;
    this.adjustmentCount++;
  }
}
