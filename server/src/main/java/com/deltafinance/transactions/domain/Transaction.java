package com.deltafinance.transactions.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Movimento financeiro importado ou manual.
 *
 * <p>O valor efetivo é assinado: negativo para saída, positivo para entrada. Valores e descrições
 * são cifrados; {@code dedup_key} e {@code fingerprint} são HMAC.
 */
@Entity
@Table(name = "transaction")
public class Transaction extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "category_id")
  private UUID categoryId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 16)
  private TransactionSource source;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 16)
  private TransactionType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private TransactionStatus status;

  @Column(name = "posted_on", nullable = false)
  private LocalDate postedOn;

  @Column(name = "source_posted_on")
  private LocalDate sourcePostedOn;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "amount_source_enc", nullable = false, columnDefinition = "bytea")
  private byte[] amountSourceEnc;

  @Column(name = "amount_effective_enc", nullable = false, columnDefinition = "bytea")
  private byte[] amountEffectiveEnc;

  @Column(name = "description_source_enc", nullable = false, columnDefinition = "bytea")
  private byte[] descriptionSourceEnc;

  @Column(name = "description_effective_enc", nullable = false, columnDefinition = "bytea")
  private byte[] descriptionEffectiveEnc;

  @Column(name = "external_transaction_id_enc", columnDefinition = "bytea")
  private byte[] externalTransactionIdEnc;

  @Column(name = "external_transaction_id_hash", columnDefinition = "bytea")
  private byte[] externalTransactionIdHash;

  @Column(name = "dedup_key", columnDefinition = "bytea")
  private byte[] dedupKey;

  @Column(name = "fingerprint", columnDefinition = "bytea")
  private byte[] fingerprint;

  @Column(name = "related_transaction_id")
  private UUID relatedTransactionId;

  @Column(name = "adjustment_reason_enc", columnDefinition = "bytea")
  private byte[] adjustmentReasonEnc;

  @Enumerated(EnumType.STRING)
  @Column(name = "reconciliation_state", nullable = false, length = 24)
  private ReconciliationState reconciliationState;

  protected Transaction() {
    // Hibernate
  }

  public Transaction(
      UUID userId,
      UUID accountId,
      TransactionSource source,
      TransactionType type,
      TransactionStatus status,
      LocalDate postedOn,
      LocalDate sourcePostedOn,
      String currency,
      byte[] amountSourceEnc,
      byte[] amountEffectiveEnc,
      byte[] descriptionSourceEnc,
      byte[] descriptionEffectiveEnc) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.source = Objects.requireNonNull(source, "source");
    this.type = Objects.requireNonNull(type, "type");
    this.status = Objects.requireNonNull(status, "status");
    this.postedOn = Objects.requireNonNull(postedOn, "postedOn");
    this.sourcePostedOn = sourcePostedOn;
    this.currency = currency == null || currency.isBlank() ? "BRL" : currency;
    this.amountSourceEnc = Objects.requireNonNull(amountSourceEnc, "amountSourceEnc");
    this.amountEffectiveEnc = Objects.requireNonNull(amountEffectiveEnc, "amountEffectiveEnc");
    this.descriptionSourceEnc =
        Objects.requireNonNull(descriptionSourceEnc, "descriptionSourceEnc");
    this.descriptionEffectiveEnc =
        Objects.requireNonNull(descriptionEffectiveEnc, "descriptionEffectiveEnc");
    this.reconciliationState = ReconciliationState.NONE;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public TransactionSource getSource() {
    return source;
  }

  public TransactionType getType() {
    return type;
  }

  public TransactionStatus getStatus() {
    return status;
  }

  public LocalDate getPostedOn() {
    return postedOn;
  }

  public LocalDate getSourcePostedOn() {
    return sourcePostedOn;
  }

  public String getCurrency() {
    return currency;
  }

  public byte[] getAmountSourceEnc() {
    return amountSourceEnc;
  }

  public byte[] getAmountEffectiveEnc() {
    return amountEffectiveEnc;
  }

  public byte[] getDescriptionSourceEnc() {
    return descriptionSourceEnc;
  }

  public byte[] getDescriptionEffectiveEnc() {
    return descriptionEffectiveEnc;
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

  public byte[] getFingerprint() {
    return fingerprint;
  }

  public UUID getRelatedTransactionId() {
    return relatedTransactionId;
  }

  public byte[] getAdjustmentReasonEnc() {
    return adjustmentReasonEnc;
  }

  public ReconciliationState getReconciliationState() {
    return reconciliationState;
  }

  public void setCategoryId(UUID categoryId) {
    this.categoryId = categoryId;
  }

  public void linkRelatedTransaction(UUID relatedTransactionId) {
    this.relatedTransactionId = relatedTransactionId;
  }

  public void setReconciliationState(ReconciliationState reconciliationState) {
    this.reconciliationState = Objects.requireNonNull(reconciliationState, "reconciliationState");
  }

  /** Atualiza dados vindos da origem mantendo o mesmo registro local. */
  public void applyImported(
      TransactionType type,
      TransactionStatus status,
      LocalDate postedOn,
      LocalDate sourcePostedOn,
      byte[] amountSourceEnc,
      byte[] amountEffectiveEnc,
      byte[] descriptionSourceEnc,
      byte[] descriptionEffectiveEnc,
      byte[] externalTransactionIdEnc,
      byte[] externalTransactionIdHash,
      byte[] dedupKey,
      byte[] fingerprint) {
    this.type = Objects.requireNonNull(type, "type");
    this.status = Objects.requireNonNull(status, "status");
    this.postedOn = Objects.requireNonNull(postedOn, "postedOn");
    this.sourcePostedOn = sourcePostedOn;
    this.amountSourceEnc = Objects.requireNonNull(amountSourceEnc, "amountSourceEnc");
    this.amountEffectiveEnc = Objects.requireNonNull(amountEffectiveEnc, "amountEffectiveEnc");
    this.descriptionSourceEnc =
        Objects.requireNonNull(descriptionSourceEnc, "descriptionSourceEnc");
    this.descriptionEffectiveEnc =
        Objects.requireNonNull(descriptionEffectiveEnc, "descriptionEffectiveEnc");
    this.externalTransactionIdEnc = externalTransactionIdEnc;
    this.externalTransactionIdHash = externalTransactionIdHash;
    this.dedupKey = dedupKey;
    this.fingerprint = fingerprint;
  }

  public void updateIdentity(
      byte[] externalTransactionIdEnc, byte[] externalTransactionIdHash, byte[] dedupKey) {
    this.externalTransactionIdEnc = externalTransactionIdEnc;
    this.externalTransactionIdHash = externalTransactionIdHash;
    this.dedupKey = dedupKey;
  }

  public void markStatus(TransactionStatus status) {
    this.status = Objects.requireNonNull(status, "status");
  }

  /** Marca a transação como ausente na origem sem apagar a linha. */
  public void markSourceRemoved() {
    this.status = TransactionStatus.SOURCE_REMOVED;
    this.reconciliationState = ReconciliationState.SOURCE_REMOVED;
  }

  /** Restaura uma transação validada na origem, mantendo a mesma linha e o histórico. */
  public void restoreFromSource(TransactionStatus restoredStatus) {
    this.status = Objects.requireNonNull(restoredStatus, "restoredStatus");
    this.reconciliationState = ReconciliationState.NONE;
  }
}
