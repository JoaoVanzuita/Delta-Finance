package com.deltafinance.investments.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Movimentação de uma posição importada.
 *
 * <p>{@code INTEREST} representa dividendos, aluguel de FII, JCP e cupons. Movimentações de
 * investimento não viram transações bancárias e não alteram o fluxo de caixa. Ausência na origem
 * marca {@code removedAt} e exclui dos agregados, sem apagar o registro.
 */
@Entity
@Table(name = "provider_investment_transaction")
public class ProviderInvestmentTransaction extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "provider_investment_id", nullable = false)
  private UUID providerInvestmentId;

  @Column(name = "external_transaction_id_enc", columnDefinition = "bytea")
  private byte[] externalTransactionIdEnc;

  @Column(name = "external_transaction_id_hash", columnDefinition = "bytea")
  private byte[] externalTransactionIdHash;

  @Column(name = "dedup_key", nullable = false, columnDefinition = "bytea")
  private byte[] dedupKey;

  @Column(name = "type", nullable = false, length = 32)
  private String type;

  @Column(name = "movement_type", length = 16)
  private String movementType;

  @Column(name = "trade_date")
  private LocalDate tradeDate;

  @Column(name = "transaction_date")
  private LocalDate transactionDate;

  @Column(name = "quantity_enc", columnDefinition = "bytea")
  private byte[] quantityEnc;

  @Column(name = "unit_value_enc", columnDefinition = "bytea")
  private byte[] unitValueEnc;

  @Column(name = "amount_enc", columnDefinition = "bytea")
  private byte[] amountEnc;

  @Column(name = "net_amount_enc", columnDefinition = "bytea")
  private byte[] netAmountEnc;

  @Column(name = "description_enc", columnDefinition = "bytea")
  private byte[] descriptionEnc;

  @Column(name = "brokerage_number_enc", columnDefinition = "bytea")
  private byte[] brokerageNumberEnc;

  @Column(name = "expenses_enc", columnDefinition = "bytea")
  private byte[] expensesEnc;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "removed_at")
  private Instant removedAt;

  protected ProviderInvestmentTransaction() {
    // Hibernate
  }

  public ProviderInvestmentTransaction(UUID userId, UUID providerInvestmentId, byte[] dedupKey) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.providerInvestmentId =
        Objects.requireNonNull(providerInvestmentId, "providerInvestmentId");
    this.dedupKey = Objects.requireNonNull(dedupKey, "dedupKey");
  }

  public void apply(ProviderInvestmentTransactionData data, Instant lastSeenAt) {
    this.externalTransactionIdEnc = data.externalTransactionIdEnc();
    this.externalTransactionIdHash = data.externalTransactionIdHash();
    this.type = Objects.requireNonNull(data.type(), "type");
    this.movementType = data.movementType();
    this.tradeDate = data.tradeDate();
    this.transactionDate = data.transactionDate();
    this.quantityEnc = data.quantityEnc();
    this.unitValueEnc = data.unitValueEnc();
    this.amountEnc = data.amountEnc();
    this.netAmountEnc = data.netAmountEnc();
    this.descriptionEnc = data.descriptionEnc();
    this.brokerageNumberEnc = data.brokerageNumberEnc();
    this.expensesEnc = data.expensesEnc();
    this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
    this.removedAt = null;
  }

  public void markRemoved(Instant at) {
    this.removedAt = Objects.requireNonNull(at, "at");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getProviderInvestmentId() {
    return providerInvestmentId;
  }

  public byte[] getExternalTransactionIdEnc() {
    return externalTransactionIdEnc;
  }

  public byte[] getExternalTransactionIdHash() {
    return externalTransactionIdHash;
  }

  public byte[] getDedupKey() {
    return dedupKey;
  }

  public String getType() {
    return type;
  }

  public String getMovementType() {
    return movementType;
  }

  public LocalDate getTradeDate() {
    return tradeDate;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public byte[] getQuantityEnc() {
    return quantityEnc;
  }

  public byte[] getUnitValueEnc() {
    return unitValueEnc;
  }

  public byte[] getAmountEnc() {
    return amountEnc;
  }

  public byte[] getNetAmountEnc() {
    return netAmountEnc;
  }

  public byte[] getDescriptionEnc() {
    return descriptionEnc;
  }

  public byte[] getBrokerageNumberEnc() {
    return brokerageNumberEnc;
  }

  public byte[] getExpensesEnc() {
    return expensesEnc;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public Instant getRemovedAt() {
    return removedAt;
  }

  public boolean isRemoved() {
    return removedAt != null;
  }
}
