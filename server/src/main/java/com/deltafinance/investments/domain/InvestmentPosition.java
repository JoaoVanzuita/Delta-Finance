package com.deltafinance.investments.domain;

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
 * Posição manual de investimento.
 *
 * <p>Ativo, ticker, quantidade, preço e observações são cifrados. O total nunca é persistido: é
 * sempre {@code quantity × referencePrice}, evitando divergência entre campos. A origem é sempre
 * {@code MANUAL}; não existe importação automática validada.
 */
@Entity
@Table(name = "investment_position")
public class InvestmentPosition extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "broker", nullable = false, length = 16)
  private InvestmentBroker broker;

  @Column(name = "asset_name_enc", nullable = false, columnDefinition = "bytea")
  private byte[] assetNameEnc;

  @Column(name = "ticker_enc", columnDefinition = "bytea")
  private byte[] tickerEnc;

  @Column(name = "quantity_enc", nullable = false, columnDefinition = "bytea")
  private byte[] quantityEnc;

  @Column(name = "reference_price_enc", nullable = false, columnDefinition = "bytea")
  private byte[] referencePriceEnc;

  @Column(name = "reference_date", nullable = false)
  private LocalDate referenceDate;

  @Column(name = "notes_enc", columnDefinition = "bytea")
  private byte[] notesEnc;

  @Enumerated(EnumType.STRING)
  @Column(name = "origin", nullable = false, length = 16)
  private InvestmentOrigin origin;

  protected InvestmentPosition() {
    // Hibernate
  }

  public InvestmentPosition(UUID userId, InvestmentBroker broker) {
    this(UUID.randomUUID(), userId, broker);
  }

  public InvestmentPosition(UUID id, UUID userId, InvestmentBroker broker) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.broker = Objects.requireNonNull(broker, "broker");
    this.origin = InvestmentOrigin.MANUAL;
  }

  public UUID getUserId() {
    return userId;
  }

  public InvestmentBroker getBroker() {
    return broker;
  }

  public byte[] getAssetNameEnc() {
    return assetNameEnc;
  }

  public byte[] getTickerEnc() {
    return tickerEnc;
  }

  public byte[] getQuantityEnc() {
    return quantityEnc;
  }

  public byte[] getReferencePriceEnc() {
    return referencePriceEnc;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public byte[] getNotesEnc() {
    return notesEnc;
  }

  public InvestmentOrigin getOrigin() {
    return origin;
  }

  /** Atualiza todos os campos editáveis, mantendo a origem manual e o identificador. */
  public void apply(
      InvestmentBroker broker,
      byte[] assetNameEnc,
      byte[] tickerEnc,
      byte[] quantityEnc,
      byte[] referencePriceEnc,
      LocalDate referenceDate,
      byte[] notesEnc) {
    this.broker = Objects.requireNonNull(broker, "broker");
    this.assetNameEnc = Objects.requireNonNull(assetNameEnc, "assetNameEnc");
    this.tickerEnc = tickerEnc;
    this.quantityEnc = Objects.requireNonNull(quantityEnc, "quantityEnc");
    this.referencePriceEnc = Objects.requireNonNull(referencePriceEnc, "referencePriceEnc");
    this.referenceDate = Objects.requireNonNull(referenceDate, "referenceDate");
    this.notesEnc = notesEnc;
  }
}
