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
 * Ativo ou passivo informado manualmente.
 *
 * <p>Descrição, valor e observações são cifrados. O valor é sempre uma magnitude positiva; a
 * direção vem de {@link ManualItemKind}.
 */
@Entity
@Table(name = "manual_asset_liability")
public class ManualAssetLiability extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 16)
  private ManualItemKind kind;

  @Column(name = "type", nullable = false, length = 60)
  private String type;

  @Column(name = "description_enc", nullable = false, columnDefinition = "bytea")
  private byte[] descriptionEnc;

  @Column(name = "reference_value_enc", nullable = false, columnDefinition = "bytea")
  private byte[] referenceValueEnc;

  @Column(name = "reference_date", nullable = false)
  private LocalDate referenceDate;

  @Column(name = "notes_enc", columnDefinition = "bytea")
  private byte[] notesEnc;

  protected ManualAssetLiability() {
    // Hibernate
  }

  public ManualAssetLiability(UUID userId, ManualItemKind kind) {
    this(UUID.randomUUID(), userId, kind);
  }

  public ManualAssetLiability(UUID id, UUID userId, ManualItemKind kind) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  public void apply(
      ManualItemKind kind,
      String type,
      byte[] descriptionEnc,
      byte[] referenceValueEnc,
      LocalDate referenceDate,
      byte[] notesEnc) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.type = Objects.requireNonNull(type, "type");
    this.descriptionEnc = Objects.requireNonNull(descriptionEnc, "descriptionEnc");
    this.referenceValueEnc = Objects.requireNonNull(referenceValueEnc, "referenceValueEnc");
    this.referenceDate = Objects.requireNonNull(referenceDate, "referenceDate");
    this.notesEnc = notesEnc;
  }

  public UUID getUserId() {
    return userId;
  }

  public ManualItemKind getKind() {
    return kind;
  }

  public String getType() {
    return type;
  }

  public byte[] getDescriptionEnc() {
    return descriptionEnc;
  }

  public byte[] getReferenceValueEnc() {
    return referenceValueEnc;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public byte[] getNotesEnc() {
    return notesEnc;
  }
}
