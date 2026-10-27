package com.deltafinance.invoices.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Pagamento oficial informado em uma Bill; não é movimentação local. */
@Entity
@Table(name = "provider_bill_payment")
public class ProviderBillPayment extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "provider_bill_id", nullable = false)
  private UUID providerBillId;

  @Column(name = "external_payment_id_hash", nullable = false, columnDefinition = "bytea")
  private byte[] externalPaymentIdHash;

  @Column(name = "payment_date", nullable = false)
  private LocalDate paymentDate;

  @Column(name = "amount_enc", nullable = false, columnDefinition = "bytea")
  private byte[] amountEnc;

  @Column(name = "payment_mode", length = 32)
  private String paymentMode;

  @Column(name = "value_type", length = 32)
  private String valueType;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  protected ProviderBillPayment() {
    // Hibernate
  }

  public ProviderBillPayment(
      UUID id,
      UUID userId,
      UUID providerBillId,
      byte[] externalPaymentIdHash,
      LocalDate paymentDate,
      byte[] amountEnc,
      String paymentMode,
      String valueType,
      String currency) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.providerBillId = Objects.requireNonNull(providerBillId, "providerBillId");
    this.externalPaymentIdHash =
        Objects.requireNonNull(externalPaymentIdHash, "externalPaymentIdHash");
    this.paymentDate = Objects.requireNonNull(paymentDate, "paymentDate");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.paymentMode = paymentMode;
    this.valueType = valueType;
    this.currency = Objects.requireNonNull(currency, "currency");
  }

  public void applyProviderData(
      LocalDate paymentDate,
      byte[] amountEnc,
      String paymentMode,
      String valueType,
      String currency) {
    this.paymentDate = Objects.requireNonNull(paymentDate, "paymentDate");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.paymentMode = paymentMode;
    this.valueType = valueType;
    this.currency = Objects.requireNonNull(currency, "currency");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getProviderBillId() {
    return providerBillId;
  }

  public byte[] getExternalPaymentIdHash() {
    return externalPaymentIdHash;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public byte[] getAmountEnc() {
    return amountEnc;
  }

  public String getPaymentMode() {
    return paymentMode;
  }

  public String getValueType() {
    return valueType;
  }

  public String getCurrency() {
    return currency;
  }
}
