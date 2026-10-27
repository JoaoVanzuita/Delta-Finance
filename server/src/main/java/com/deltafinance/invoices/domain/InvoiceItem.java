package com.deltafinance.invoices.domain;

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

/** Item de fatura; valor sempre positivo, direção em {@code reporting_effect}. */
@Entity
@Table(name = "invoice_item")
public class InvoiceItem extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "transaction_id")
  private UUID transactionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 24)
  private InvoiceItemKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "reporting_effect", nullable = false, length = 24)
  private ReportingEffect reportingEffect;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 16)
  private InvoiceItemSource source;

  @Column(name = "description_enc", nullable = false, columnDefinition = "bytea")
  private byte[] descriptionEnc;

  @Column(name = "amount_enc", nullable = false, columnDefinition = "bytea")
  private byte[] amountEnc;

  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  @Column(name = "installment_number")
  private Integer installmentNumber;

  @Column(name = "total_installments")
  private Integer totalInstallments;

  @Column(name = "installment_plan_key", columnDefinition = "bytea")
  private byte[] installmentPlanKey;

  @Column(name = "removed_at")
  private Instant removedAt;

  @Column(name = "mirror_suspended", nullable = false)
  private boolean mirrorSuspended;

  protected InvoiceItem() {
    // Hibernate
  }

  public InvoiceItem(
      UUID userId,
      UUID invoiceId,
      UUID transactionId,
      InvoiceItemKind kind,
      ReportingEffect reportingEffect,
      InvoiceItemSource source,
      byte[] descriptionEnc,
      byte[] amountEnc,
      LocalDate occurredOn,
      Integer installmentNumber,
      Integer totalInstallments,
      byte[] installmentPlanKey) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
    this.transactionId = transactionId;
    this.kind = Objects.requireNonNull(kind, "kind");
    this.reportingEffect = Objects.requireNonNull(reportingEffect, "reportingEffect");
    this.source = Objects.requireNonNull(source, "source");
    this.descriptionEnc = Objects.requireNonNull(descriptionEnc, "descriptionEnc");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn");
    this.installmentNumber = installmentNumber;
    this.totalInstallments = totalInstallments;
    this.installmentPlanKey = installmentPlanKey;
  }

  public InvoiceItem(
      UUID id,
      UUID userId,
      UUID invoiceId,
      UUID transactionId,
      InvoiceItemKind kind,
      ReportingEffect reportingEffect,
      InvoiceItemSource source,
      byte[] descriptionEnc,
      byte[] amountEnc,
      LocalDate occurredOn,
      Integer installmentNumber,
      Integer totalInstallments,
      byte[] installmentPlanKey) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
    this.transactionId = transactionId;
    this.kind = Objects.requireNonNull(kind, "kind");
    this.reportingEffect = Objects.requireNonNull(reportingEffect, "reportingEffect");
    this.source = Objects.requireNonNull(source, "source");
    this.descriptionEnc = Objects.requireNonNull(descriptionEnc, "descriptionEnc");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn");
    this.installmentNumber = installmentNumber;
    this.totalInstallments = totalInstallments;
    this.installmentPlanKey = installmentPlanKey;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public InvoiceItemKind getKind() {
    return kind;
  }

  public ReportingEffect getReportingEffect() {
    return reportingEffect;
  }

  public InvoiceItemSource getSource() {
    return source;
  }

  public byte[] getDescriptionEnc() {
    return descriptionEnc;
  }

  public byte[] getAmountEnc() {
    return amountEnc;
  }

  public LocalDate getOccurredOn() {
    return occurredOn;
  }

  public Integer getInstallmentNumber() {
    return installmentNumber;
  }

  public Integer getTotalInstallments() {
    return totalInstallments;
  }

  public byte[] getInstallmentPlanKey() {
    return installmentPlanKey;
  }

  public Instant getRemovedAt() {
    return removedAt;
  }

  /** Item participa dos totais; suspensão automática também usa {@code removed_at}. */
  public boolean isActive() {
    return removedAt == null;
  }

  public boolean isMirrorSuspended() {
    return mirrorSuspended;
  }

  public void applyEdits(
      InvoiceItemKind kind,
      ReportingEffect reportingEffect,
      byte[] descriptionEnc,
      byte[] amountEnc,
      LocalDate occurredOn,
      Integer installmentNumber,
      Integer totalInstallments) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.reportingEffect = Objects.requireNonNull(reportingEffect, "reportingEffect");
    this.descriptionEnc = Objects.requireNonNull(descriptionEnc, "descriptionEnc");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn");
    this.installmentNumber = installmentNumber;
    this.totalInstallments = totalInstallments;
  }

  public void attachTransaction(UUID transactionId) {
    this.transactionId = transactionId;
  }

  /** Atualiza o espelho de uma transação sem alterar vínculo, histórico ou parcelamento. */
  public void applyMirror(
      InvoiceItemKind kind,
      ReportingEffect reportingEffect,
      byte[] descriptionEnc,
      byte[] amountEnc,
      LocalDate occurredOn) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.reportingEffect = Objects.requireNonNull(reportingEffect, "reportingEffect");
    this.descriptionEnc = Objects.requireNonNull(descriptionEnc, "descriptionEnc");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn");
  }

  public void moveTo(UUID invoiceId) {
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
  }

  public void remove(Instant at) {
    this.removedAt = Objects.requireNonNull(at, "at");
    this.mirrorSuspended = false;
  }

  /** Remove logicamente por perda de efetividade da âncora, preservando a marca de suspensão. */
  public void suspendByMirror(Instant at) {
    this.removedAt = Objects.requireNonNull(at, "at");
    this.mirrorSuspended = true;
  }

  /** Reativa um item que havia sido suspenso pelo espelho, mantendo seus dados originais. */
  public void restoreFromMirrorSuspension() {
    this.removedAt = null;
    this.mirrorSuspended = false;
  }
}
