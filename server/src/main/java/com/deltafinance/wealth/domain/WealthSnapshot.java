package com.deltafinance.wealth.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Snapshot mensal imutável da posição patrimonial.
 *
 * <p>Totais são cifrados e representam apenas a posição no momento da captura. O snapshot não é
 * cotação em tempo real, não calcula rentabilidade e não é reconstruído retroativamente.
 */
@Entity
@Table(name = "wealth_snapshot")
public class WealthSnapshot extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "reference_date", nullable = false)
  private LocalDate referenceDate;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "capture_trigger", nullable = false, length = 16)
  private SnapshotTrigger trigger;

  @Enumerated(EnumType.STRING)
  @Column(name = "completeness", nullable = false, length = 16)
  private WealthCompleteness completeness;

  @Column(name = "gross_assets_enc", nullable = false, columnDefinition = "bytea")
  private byte[] grossAssetsEnc;

  @Column(name = "liabilities_enc", nullable = false, columnDefinition = "bytea")
  private byte[] liabilitiesEnc;

  @Column(name = "net_worth_enc", nullable = false, columnDefinition = "bytea")
  private byte[] netWorthEnc;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "coverage", nullable = false, columnDefinition = "jsonb")
  private String coverage;

  protected WealthSnapshot() {
    // Hibernate
  }

  public WealthSnapshot(
      UUID userId,
      LocalDate referenceDate,
      Instant capturedAt,
      SnapshotTrigger trigger,
      WealthCompleteness completeness,
      byte[] grossAssetsEnc,
      byte[] liabilitiesEnc,
      byte[] netWorthEnc,
      String coverage) {
    this(
        UUID.randomUUID(),
        userId,
        referenceDate,
        capturedAt,
        trigger,
        completeness,
        grossAssetsEnc,
        liabilitiesEnc,
        netWorthEnc,
        coverage);
  }

  public WealthSnapshot(
      UUID id,
      UUID userId,
      LocalDate referenceDate,
      Instant capturedAt,
      SnapshotTrigger trigger,
      WealthCompleteness completeness,
      byte[] grossAssetsEnc,
      byte[] liabilitiesEnc,
      byte[] netWorthEnc,
      String coverage) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.referenceDate = Objects.requireNonNull(referenceDate, "referenceDate");
    this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
    this.trigger = Objects.requireNonNull(trigger, "trigger");
    this.completeness = Objects.requireNonNull(completeness, "completeness");
    this.grossAssetsEnc = Objects.requireNonNull(grossAssetsEnc, "grossAssetsEnc");
    this.liabilitiesEnc = Objects.requireNonNull(liabilitiesEnc, "liabilitiesEnc");
    this.netWorthEnc = Objects.requireNonNull(netWorthEnc, "netWorthEnc");
    this.coverage = coverage == null ? "{}" : coverage;
  }

  public UUID getUserId() {
    return userId;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public SnapshotTrigger getTrigger() {
    return trigger;
  }

  public WealthCompleteness getCompleteness() {
    return completeness;
  }

  public byte[] getGrossAssetsEnc() {
    return grossAssetsEnc;
  }

  public byte[] getLiabilitiesEnc() {
    return liabilitiesEnc;
  }

  public byte[] getNetWorthEnc() {
    return netWorthEnc;
  }

  public String getCoverage() {
    return coverage;
  }
}
