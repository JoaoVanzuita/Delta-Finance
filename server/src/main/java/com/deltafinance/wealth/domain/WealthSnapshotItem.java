package com.deltafinance.wealth.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Item imutável que compõe um snapshot patrimonial. */
@Entity
@Table(name = "wealth_snapshot_item")
public class WealthSnapshotItem extends BaseEntity {

  @Column(name = "snapshot_id", nullable = false)
  private UUID snapshotId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 24)
  private WealthSourceType sourceType;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "asset_class", length = 40)
  private String assetClass;

  @Column(name = "description_enc", nullable = false, columnDefinition = "bytea")
  private byte[] descriptionEnc;

  @Column(name = "value_enc", nullable = false, columnDefinition = "bytea")
  private byte[] valueEnc;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "included", nullable = false)
  private boolean included;

  @Column(name = "exclusion_reason", length = 240)
  private String exclusionReason;

  @Column(name = "source_reference_date")
  private LocalDate sourceReferenceDate;

  @Column(name = "stale", nullable = false)
  private boolean stale;

  protected WealthSnapshotItem() {
    // Hibernate
  }

  public WealthSnapshotItem(
      UUID snapshotId,
      UUID userId,
      WealthSourceType sourceType,
      UUID sourceId,
      String assetClass,
      byte[] descriptionEnc,
      byte[] valueEnc,
      String currency,
      boolean included,
      String exclusionReason,
      LocalDate sourceReferenceDate,
      boolean stale) {
    this(
        UUID.randomUUID(),
        snapshotId,
        userId,
        sourceType,
        sourceId,
        assetClass,
        descriptionEnc,
        valueEnc,
        currency,
        included,
        exclusionReason,
        sourceReferenceDate,
        stale);
  }

  public WealthSnapshotItem(
      UUID id,
      UUID snapshotId,
      UUID userId,
      WealthSourceType sourceType,
      UUID sourceId,
      String assetClass,
      byte[] descriptionEnc,
      byte[] valueEnc,
      String currency,
      boolean included,
      String exclusionReason,
      LocalDate sourceReferenceDate,
      boolean stale) {
    super(id);
    this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
    this.sourceId = sourceId;
    this.assetClass = assetClass;
    this.descriptionEnc = Objects.requireNonNull(descriptionEnc, "descriptionEnc");
    this.valueEnc = Objects.requireNonNull(valueEnc, "valueEnc");
    this.currency = Objects.requireNonNull(currency, "currency");
    this.included = included;
    this.exclusionReason = exclusionReason;
    this.sourceReferenceDate = sourceReferenceDate;
    this.stale = stale;
  }

  public UUID getSnapshotId() {
    return snapshotId;
  }

  public UUID getUserId() {
    return userId;
  }

  public WealthSourceType getSourceType() {
    return sourceType;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public String getAssetClass() {
    return assetClass;
  }

  public byte[] getDescriptionEnc() {
    return descriptionEnc;
  }

  public byte[] getValueEnc() {
    return valueEnc;
  }

  public String getCurrency() {
    return currency;
  }

  public boolean isIncluded() {
    return included;
  }

  public String getExclusionReason() {
    return exclusionReason;
  }

  public LocalDate getSourceReferenceDate() {
    return sourceReferenceDate;
  }

  public boolean isStale() {
    return stale;
  }
}
