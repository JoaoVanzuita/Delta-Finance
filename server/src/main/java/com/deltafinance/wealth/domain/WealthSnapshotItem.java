package com.deltafinance.wealth.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Item imutável da composição de um snapshot.
 *
 * <p>Guarda inclusive itens excluídos e o motivo, para que a fotografia explique lacunas sem
 * recalcular nada com dados futuros.
 */
@Entity
@Table(name = "wealth_snapshot_item")
public class WealthSnapshotItem extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "snapshot_id", nullable = false)
  private UUID snapshotId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 24)
  private WealthSourceType sourceType;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "description_enc", nullable = false, columnDefinition = "bytea")
  private byte[] descriptionEnc;

  @Column(name = "value_enc", nullable = false, columnDefinition = "bytea")
  private byte[] valueEnc;

  @Column(name = "included", nullable = false)
  private boolean included;

  @Column(name = "exclusion_reason", length = 200)
  private String exclusionReason;

  protected WealthSnapshotItem() {
    // Hibernate
  }

  public WealthSnapshotItem(
      UUID userId, UUID snapshotId, WealthSourceType sourceType, UUID sourceId) {
    this(UUID.randomUUID(), userId, snapshotId, sourceType, sourceId);
  }

  public WealthSnapshotItem(
      UUID id, UUID userId, UUID snapshotId, WealthSourceType sourceType, UUID sourceId) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
    this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
    this.sourceId = sourceId;
  }

  public void apply(
      byte[] descriptionEnc, byte[] valueEnc, boolean included, String exclusionReason) {
    this.descriptionEnc = Objects.requireNonNull(descriptionEnc, "descriptionEnc");
    this.valueEnc = Objects.requireNonNull(valueEnc, "valueEnc");
    this.included = included;
    this.exclusionReason = exclusionReason;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getSnapshotId() {
    return snapshotId;
  }

  public WealthSourceType getSourceType() {
    return sourceType;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public byte[] getDescriptionEnc() {
    return descriptionEnc;
  }

  public byte[] getValueEnc() {
    return valueEnc;
  }

  public boolean isIncluded() {
    return included;
  }

  public String getExclusionReason() {
    return exclusionReason;
  }
}
