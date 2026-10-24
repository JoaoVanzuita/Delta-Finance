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

/**
 * Fotografia datada do patrimônio.
 *
 * <p>Valores são cifrados. O snapshot é sempre gerado a partir dos dados atuais do dia; nenhum
 * saldo passado é inferido retroativamente. No mesmo dia, o snapshot é atualizado.
 */
@Entity
@Table(name = "wealth_snapshot")
public class WealthSnapshot extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "reference_date", nullable = false)
  private LocalDate referenceDate;

  @Column(name = "gross_assets_enc", nullable = false, columnDefinition = "bytea")
  private byte[] grossAssetsEnc;

  @Column(name = "liabilities_enc", nullable = false, columnDefinition = "bytea")
  private byte[] liabilitiesEnc;

  @Column(name = "net_worth_enc", nullable = false, columnDefinition = "bytea")
  private byte[] netWorthEnc;

  @Enumerated(EnumType.STRING)
  @Column(name = "completeness", nullable = false, length = 16)
  private WealthCompleteness completeness;

  @Column(name = "notes_enc", columnDefinition = "bytea")
  private byte[] notesEnc;

  protected WealthSnapshot() {
    // Hibernate
  }

  public WealthSnapshot(UUID userId, LocalDate referenceDate) {
    this(UUID.randomUUID(), userId, referenceDate);
  }

  public WealthSnapshot(UUID id, UUID userId, LocalDate referenceDate) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.referenceDate = Objects.requireNonNull(referenceDate, "referenceDate");
  }

  public void apply(
      byte[] grossAssetsEnc,
      byte[] liabilitiesEnc,
      byte[] netWorthEnc,
      WealthCompleteness completeness,
      byte[] notesEnc) {
    this.grossAssetsEnc = Objects.requireNonNull(grossAssetsEnc, "grossAssetsEnc");
    this.liabilitiesEnc = Objects.requireNonNull(liabilitiesEnc, "liabilitiesEnc");
    this.netWorthEnc = Objects.requireNonNull(netWorthEnc, "netWorthEnc");
    this.completeness = Objects.requireNonNull(completeness, "completeness");
    this.notesEnc = notesEnc;
  }

  public UUID getUserId() {
    return userId;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
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

  public WealthCompleteness getCompleteness() {
    return completeness;
  }

  public byte[] getNotesEnc() {
    return notesEnc;
  }
}
