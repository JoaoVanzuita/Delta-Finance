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

/** Pagamento de fatura; reduz saldo devedor e nunca é receita/despesa. */
@Entity
@Table(name = "invoice_payment")
public class InvoicePayment extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "payment_account_id", nullable = false)
  private UUID paymentAccountId;

  @Column(name = "amount_enc", nullable = false, columnDefinition = "bytea")
  private byte[] amountEnc;

  @Column(name = "paid_on", nullable = false)
  private LocalDate paidOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private InvoicePaymentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 16)
  private InvoicePaymentSource source;

  @Column(name = "source_transaction_id")
  private UUID sourceTransactionId;

  @Column(name = "dedup_key", columnDefinition = "bytea")
  private byte[] dedupKey;

  protected InvoicePayment() {
    // Hibernate
  }

  public InvoicePayment(
      UUID userId,
      UUID invoiceId,
      UUID paymentAccountId,
      byte[] amountEnc,
      LocalDate paidOn,
      InvoicePaymentStatus status,
      InvoicePaymentSource source,
      UUID sourceTransactionId) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
    this.paymentAccountId = Objects.requireNonNull(paymentAccountId, "paymentAccountId");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.paidOn = Objects.requireNonNull(paidOn, "paidOn");
    this.status = Objects.requireNonNull(status, "status");
    this.source = Objects.requireNonNull(source, "source");
    this.sourceTransactionId = sourceTransactionId;
  }

  public InvoicePayment(
      UUID id,
      UUID userId,
      UUID invoiceId,
      UUID paymentAccountId,
      byte[] amountEnc,
      LocalDate paidOn,
      InvoicePaymentStatus status,
      InvoicePaymentSource source,
      UUID sourceTransactionId) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
    this.paymentAccountId = Objects.requireNonNull(paymentAccountId, "paymentAccountId");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.paidOn = Objects.requireNonNull(paidOn, "paidOn");
    this.status = Objects.requireNonNull(status, "status");
    this.source = Objects.requireNonNull(source, "source");
    this.sourceTransactionId = sourceTransactionId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public UUID getPaymentAccountId() {
    return paymentAccountId;
  }

  public byte[] getAmountEnc() {
    return amountEnc;
  }

  public LocalDate getPaidOn() {
    return paidOn;
  }

  public InvoicePaymentStatus getStatus() {
    return status;
  }

  public InvoicePaymentSource getSource() {
    return source;
  }

  public UUID getSourceTransactionId() {
    return sourceTransactionId;
  }

  public byte[] getDedupKey() {
    return dedupKey;
  }

  /** Registra a chave de deduplicação de pagamentos importados; manual permanece nula. */
  public void applyDedupKey(byte[] dedupKey) {
    this.dedupKey = dedupKey;
  }

  /** Move o vínculo para outra transação equivalente (ex.: duplicata PENDING/POSTED). */
  public void repointSource(UUID transactionId) {
    this.sourceTransactionId = Objects.requireNonNull(transactionId, "transactionId");
  }

  /** Atualiza valor/data/chave quando a transação de origem é corrigida. */
  public void applySourceData(byte[] amountEnc, LocalDate paidOn, byte[] dedupKey) {
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.paidOn = Objects.requireNonNull(paidOn, "paidOn");
    if (dedupKey != null) {
      this.dedupKey = dedupKey;
    }
  }

  /** Move o pagamento para outra fatura do mesmo cartão. */
  public void reassignInvoice(UUID invoiceId) {
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
  }

  /** Conta pagadora identificada por movimento interno confirmado. */
  public void updatePaymentAccountId(UUID paymentAccountId) {
    this.paymentAccountId = Objects.requireNonNull(paymentAccountId, "paymentAccountId");
  }

  public void confirm() {
    this.status = InvoicePaymentStatus.CONFIRMED;
  }

  public void reverse() {
    this.status = InvoicePaymentStatus.REVERSED;
  }
}
