package com.deltafinance.invoices.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Bill oficial do provedor, guardada como referência de conciliação.
 *
 * <p>A fatura local continua sendo a fonte de verdade: esta entidade nunca altera totais nem saldo
 * devedor. Ela permite comparar total, pagamento mínimo, vencimento e pagamentos oficiais.
 */
@Entity
@Table(name = "provider_bill")
public class ProviderBill extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "card_account_id", nullable = false)
  private UUID cardAccountId;

  @Column(name = "external_bill_id_hash", nullable = false, columnDefinition = "bytea")
  private byte[] externalBillIdHash;

  @Column(name = "closing_date")
  private LocalDate closingDate;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "total_enc", nullable = false, columnDefinition = "bytea")
  private byte[] totalEnc;

  @Column(name = "minimum_payment_enc", columnDefinition = "bytea")
  private byte[] minimumPaymentEnc;

  @Column(name = "payments_total_enc", nullable = false, columnDefinition = "bytea")
  private byte[] paymentsTotalEnc;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "synced_at", nullable = false)
  private Instant syncedAt;

  protected ProviderBill() {
    // Hibernate
  }

  public ProviderBill(
      UUID id,
      UUID userId,
      UUID cardAccountId,
      byte[] externalBillIdHash,
      LocalDate closingDate,
      LocalDate dueDate,
      byte[] totalEnc,
      byte[] minimumPaymentEnc,
      byte[] paymentsTotalEnc,
      String currency,
      Instant syncedAt) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.cardAccountId = Objects.requireNonNull(cardAccountId, "cardAccountId");
    this.externalBillIdHash = Objects.requireNonNull(externalBillIdHash, "externalBillIdHash");
    this.closingDate = closingDate;
    this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
    this.totalEnc = Objects.requireNonNull(totalEnc, "totalEnc");
    this.minimumPaymentEnc = minimumPaymentEnc;
    this.paymentsTotalEnc = Objects.requireNonNull(paymentsTotalEnc, "paymentsTotalEnc");
    this.currency = Objects.requireNonNull(currency, "currency");
    this.syncedAt = Objects.requireNonNull(syncedAt, "syncedAt");
  }

  public void applyProviderData(
      LocalDate closingDate,
      LocalDate dueDate,
      byte[] totalEnc,
      byte[] minimumPaymentEnc,
      byte[] paymentsTotalEnc,
      String currency,
      Instant syncedAt) {
    this.closingDate = closingDate;
    this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
    this.totalEnc = Objects.requireNonNull(totalEnc, "totalEnc");
    this.minimumPaymentEnc = minimumPaymentEnc;
    this.paymentsTotalEnc = Objects.requireNonNull(paymentsTotalEnc, "paymentsTotalEnc");
    this.currency = Objects.requireNonNull(currency, "currency");
    this.syncedAt = Objects.requireNonNull(syncedAt, "syncedAt");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getCardAccountId() {
    return cardAccountId;
  }

  public byte[] getExternalBillIdHash() {
    return externalBillIdHash;
  }

  public LocalDate getClosingDate() {
    return closingDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public byte[] getTotalEnc() {
    return totalEnc;
  }

  public byte[] getMinimumPaymentEnc() {
    return minimumPaymentEnc;
  }

  public byte[] getPaymentsTotalEnc() {
    return paymentsTotalEnc;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getSyncedAt() {
    return syncedAt;
  }
}
