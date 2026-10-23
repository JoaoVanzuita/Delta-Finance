package com.deltafinance.invoices.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Histórico auditável de correções de itens de fatura. */
@Entity
@Table(name = "invoice_item_correction")
public class InvoiceItemCorrection extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "invoice_item_id", nullable = false)
  private UUID invoiceItemId;

  @Column(name = "field_name", nullable = false, length = 32)
  private String fieldName;

  @Column(name = "old_value_enc", columnDefinition = "bytea")
  private byte[] oldValueEnc;

  @Column(name = "new_value_enc", nullable = false, columnDefinition = "bytea")
  private byte[] newValueEnc;

  @Column(name = "reason_enc", columnDefinition = "bytea")
  private byte[] reasonEnc;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected InvoiceItemCorrection() {
    // Hibernate
  }

  public InvoiceItemCorrection(
      UUID id,
      UUID userId,
      UUID invoiceItemId,
      String fieldName,
      byte[] oldValueEnc,
      byte[] newValueEnc,
      byte[] reasonEnc,
      Instant changedAt) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.invoiceItemId = Objects.requireNonNull(invoiceItemId, "invoiceItemId");
    this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
    this.oldValueEnc = oldValueEnc;
    this.newValueEnc = Objects.requireNonNull(newValueEnc, "newValueEnc");
    this.reasonEnc = reasonEnc;
    this.changedAt = Objects.requireNonNull(changedAt, "changedAt");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getInvoiceItemId() {
    return invoiceItemId;
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

  public byte[] getReasonEnc() {
    return reasonEnc;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
