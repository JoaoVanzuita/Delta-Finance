package com.deltafinance.transactions.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Auditoria de uma correção de campo de transação pelo usuário. */
@Entity
@Table(name = "transaction_correction")
public class TransactionCorrection extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "field_name", nullable = false, length = 32)
  private String fieldName;

  @Column(name = "old_value_enc", columnDefinition = "bytea")
  private byte[] oldValueEnc;

  @Column(name = "new_value_enc", nullable = false, columnDefinition = "bytea")
  private byte[] newValueEnc;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected TransactionCorrection() {
    // Hibernate
  }

  public TransactionCorrection(
      UUID id,
      UUID userId,
      UUID transactionId,
      String fieldName,
      byte[] oldValueEnc,
      byte[] newValueEnc,
      Instant changedAt) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
    this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
    this.oldValueEnc = oldValueEnc;
    this.newValueEnc = Objects.requireNonNull(newValueEnc, "newValueEnc");
    this.changedAt = Objects.requireNonNull(changedAt, "changedAt");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public String getFieldName() {
    return fieldName;
  }

  public byte[] getOldValueEnc() {
    return oldValueEnc;
  }

  public byte[] getNewValueEnc() {
    return newValueEnc;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
