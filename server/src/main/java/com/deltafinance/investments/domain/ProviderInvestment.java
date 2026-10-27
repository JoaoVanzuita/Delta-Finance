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
 * Posição de investimento importada de um item do provedor.
 *
 * <p>Investimento não é conta: o vínculo é com a conexão/item. O valor atual é {@code balance};
 * quantidade e preço unitário são mantidos para conferência. Posições ausentes na origem são
 * marcadas como indisponíveis, nunca apagadas. Campos financeiros e de identificação do ativo são
 * cifrados.
 */
@Entity
@Table(name = "provider_investment")
public class ProviderInvestment extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "connection_id", nullable = false)
  private UUID connectionId;

  @Column(name = "external_investment_id_enc", nullable = false, columnDefinition = "bytea")
  private byte[] externalInvestmentIdEnc;

  @Column(name = "external_investment_id_hash", nullable = false, columnDefinition = "bytea")
  private byte[] externalInvestmentIdHash;

  @Column(name = "name_enc", columnDefinition = "bytea")
  private byte[] nameEnc;

  @Column(name = "code_enc", columnDefinition = "bytea")
  private byte[] codeEnc;

  @Column(name = "isin_enc", columnDefinition = "bytea")
  private byte[] isinEnc;

  @Column(name = "owner_enc", columnDefinition = "bytea")
  private byte[] ownerEnc;

  @Column(name = "institution_enc", columnDefinition = "bytea")
  private byte[] institutionEnc;

  @Column(name = "issuer_enc", columnDefinition = "bytea")
  private byte[] issuerEnc;

  @Column(name = "type", nullable = false, length = 32)
  private String type;

  @Column(name = "subtype", length = 48)
  private String subtype;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "quantity_enc", columnDefinition = "bytea")
  private byte[] quantityEnc;

  @Column(name = "unit_value_enc", columnDefinition = "bytea")
  private byte[] unitValueEnc;

  @Column(name = "amount_enc", columnDefinition = "bytea")
  private byte[] amountEnc;

  @Column(name = "balance_enc", columnDefinition = "bytea")
  private byte[] balanceEnc;

  @Column(name = "amount_original_enc", columnDefinition = "bytea")
  private byte[] amountOriginalEnc;

  @Column(name = "amount_profit_enc", columnDefinition = "bytea")
  private byte[] amountProfitEnc;

  @Column(name = "amount_withdrawal_enc", columnDefinition = "bytea")
  private byte[] amountWithdrawalEnc;

  @Column(name = "taxes_enc", columnDefinition = "bytea")
  private byte[] taxesEnc;

  @Column(name = "taxes2_enc", columnDefinition = "bytea")
  private byte[] taxes2Enc;

  @Column(name = "rate_enc", columnDefinition = "bytea")
  private byte[] rateEnc;

  @Column(name = "rate_type", length = 32)
  private String rateType;

  @Column(name = "fixed_annual_rate_enc", columnDefinition = "bytea")
  private byte[] fixedAnnualRateEnc;

  @Column(name = "last_month_rate_enc", columnDefinition = "bytea")
  private byte[] lastMonthRateEnc;

  @Column(name = "annual_rate_enc", columnDefinition = "bytea")
  private byte[] annualRateEnc;

  @Column(name = "last_twelve_months_rate_enc", columnDefinition = "bytea")
  private byte[] lastTwelveMonthsRateEnc;

  @Column(name = "reference_date")
  private LocalDate referenceDate;

  @Column(name = "purchase_date")
  private LocalDate purchaseDate;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "grace_period_date")
  private LocalDate gracePeriodDate;

  @Column(name = "provider_updated_at")
  private Instant providerUpdatedAt;

  @Column(name = "synced_at", nullable = false)
  private Instant syncedAt;

  @Column(name = "unavailable_at")
  private Instant unavailableAt;

  @Column(name = "manual_cost_basis_enc", columnDefinition = "bytea")
  private byte[] manualCostBasisEnc;

  @Column(name = "manual_cost_basis_updated_at")
  private Instant manualCostBasisUpdatedAt;

  @Column(name = "user_asset_class", length = 32)
  private String userAssetClass;

  protected ProviderInvestment() {
    // Hibernate
  }

  public ProviderInvestment(UUID userId, UUID connectionId, byte[] externalInvestmentIdHash) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
    this.externalInvestmentIdHash =
        Objects.requireNonNull(externalInvestmentIdHash, "externalInvestmentIdHash");
  }

  /** Atualiza os dados do provedor; não altera custo/classificação informados pelo usuário. */
  public void apply(ProviderInvestmentData data, Instant syncedAt) {
    this.externalInvestmentIdEnc =
        Objects.requireNonNull(data.externalInvestmentIdEnc(), "externalInvestmentIdEnc");
    this.nameEnc = data.nameEnc();
    this.codeEnc = data.codeEnc();
    this.isinEnc = data.isinEnc();
    this.ownerEnc = data.ownerEnc();
    this.institutionEnc = data.institutionEnc();
    this.issuerEnc = data.issuerEnc();
    this.type = Objects.requireNonNull(data.type(), "type");
    this.subtype = data.subtype();
    this.status = Objects.requireNonNull(data.status(), "status");
    this.currency = Objects.requireNonNull(data.currency(), "currency");
    this.quantityEnc = data.quantityEnc();
    this.unitValueEnc = data.unitValueEnc();
    this.amountEnc = data.amountEnc();
    this.balanceEnc = data.balanceEnc();
    this.amountOriginalEnc = data.amountOriginalEnc();
    this.amountProfitEnc = data.amountProfitEnc();
    this.amountWithdrawalEnc = data.amountWithdrawalEnc();
    this.taxesEnc = data.taxesEnc();
    this.taxes2Enc = data.taxes2Enc();
    this.rateEnc = data.rateEnc();
    this.rateType = data.rateType();
    this.fixedAnnualRateEnc = data.fixedAnnualRateEnc();
    this.lastMonthRateEnc = data.lastMonthRateEnc();
    this.annualRateEnc = data.annualRateEnc();
    this.lastTwelveMonthsRateEnc = data.lastTwelveMonthsRateEnc();
    this.referenceDate = data.referenceDate();
    this.purchaseDate = data.purchaseDate();
    this.dueDate = data.dueDate();
    this.gracePeriodDate = data.gracePeriodDate();
    this.providerUpdatedAt = data.providerUpdatedAt();
    this.syncedAt = Objects.requireNonNull(syncedAt, "syncedAt");
    this.unavailableAt = null;
  }

  /** Marca ausência na origem sem apagar a posição ou seu histórico. */
  public void markUnavailable(Instant at) {
    this.unavailableAt = Objects.requireNonNull(at, "at");
  }

  public void updateManualCostBasis(byte[] manualCostBasisEnc, Instant at) {
    this.manualCostBasisEnc = manualCostBasisEnc;
    this.manualCostBasisUpdatedAt = manualCostBasisEnc == null ? null : Objects.requireNonNull(at);
  }

  public void updateUserAssetClass(String userAssetClass) {
    this.userAssetClass = userAssetClass;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getConnectionId() {
    return connectionId;
  }

  public byte[] getExternalInvestmentIdEnc() {
    return externalInvestmentIdEnc;
  }

  public byte[] getExternalInvestmentIdHash() {
    return externalInvestmentIdHash;
  }

  public byte[] getNameEnc() {
    return nameEnc;
  }

  public byte[] getCodeEnc() {
    return codeEnc;
  }

  public byte[] getIsinEnc() {
    return isinEnc;
  }

  public byte[] getOwnerEnc() {
    return ownerEnc;
  }

  public byte[] getInstitutionEnc() {
    return institutionEnc;
  }

  public byte[] getIssuerEnc() {
    return issuerEnc;
  }

  public String getType() {
    return type;
  }

  public String getSubtype() {
    return subtype;
  }

  public String getStatus() {
    return status;
  }

  public String getCurrency() {
    return currency;
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

  public byte[] getBalanceEnc() {
    return balanceEnc;
  }

  public byte[] getAmountOriginalEnc() {
    return amountOriginalEnc;
  }

  public byte[] getAmountProfitEnc() {
    return amountProfitEnc;
  }

  public byte[] getAmountWithdrawalEnc() {
    return amountWithdrawalEnc;
  }

  public byte[] getTaxesEnc() {
    return taxesEnc;
  }

  public byte[] getTaxes2Enc() {
    return taxes2Enc;
  }

  public byte[] getRateEnc() {
    return rateEnc;
  }

  public String getRateType() {
    return rateType;
  }

  public byte[] getFixedAnnualRateEnc() {
    return fixedAnnualRateEnc;
  }

  public byte[] getLastMonthRateEnc() {
    return lastMonthRateEnc;
  }

  public byte[] getAnnualRateEnc() {
    return annualRateEnc;
  }

  public byte[] getLastTwelveMonthsRateEnc() {
    return lastTwelveMonthsRateEnc;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public LocalDate getPurchaseDate() {
    return purchaseDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public LocalDate getGracePeriodDate() {
    return gracePeriodDate;
  }

  public Instant getProviderUpdatedAt() {
    return providerUpdatedAt;
  }

  public Instant getSyncedAt() {
    return syncedAt;
  }

  public Instant getUnavailableAt() {
    return unavailableAt;
  }

  public byte[] getManualCostBasisEnc() {
    return manualCostBasisEnc;
  }

  public Instant getManualCostBasisUpdatedAt() {
    return manualCostBasisUpdatedAt;
  }

  public String getUserAssetClass() {
    return userAssetClass;
  }

  public boolean isAvailable() {
    return unavailableAt == null;
  }
}
