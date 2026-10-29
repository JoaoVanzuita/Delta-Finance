package com.deltafinance.investments.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Provento manual de investimento.
 *
 * <p>Aponta para uma posição importada. Proventos do provedor são lidos de {@code
 * provider_investment_transaction} com tipo {@code INTEREST}; esta entidade guarda apenas o
 * complemento informado pelo usuário.
 */
@Entity
@Table(name = "investment_income")
public class InvestmentIncome extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "provider_investment_id", nullable = false)
  private UUID providerInvestmentId;

  @Column(name = "income_type", nullable = false, length = 16)
  private String incomeType;

  @Column(name = "income_date", nullable = false)
  private LocalDate incomeDate;

  @Column(name = "amount_enc", nullable = false, columnDefinition = "bytea")
  private byte[] amountEnc;

  @Column(name = "description_enc", columnDefinition = "bytea")
  private byte[] descriptionEnc;

  @Column(name = "source", nullable = false, length = 16)
  private String source;

  protected InvestmentIncome() {
    // Hibernate
  }

  public InvestmentIncome(UUID userId) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.source = "MANUAL";
  }

  public void apply(
      UUID providerInvestmentId,
      String incomeType,
      LocalDate incomeDate,
      byte[] amountEnc,
      byte[] descriptionEnc) {
    this.providerInvestmentId =
        Objects.requireNonNull(providerInvestmentId, "providerInvestmentId");
    this.incomeType = Objects.requireNonNull(incomeType, "incomeType");
    this.incomeDate = Objects.requireNonNull(incomeDate, "incomeDate");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.descriptionEnc = descriptionEnc;
    this.source = "MANUAL";
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getProviderInvestmentId() {
    return providerInvestmentId;
  }

  public String getIncomeType() {
    return incomeType;
  }

  public LocalDate getIncomeDate() {
    return incomeDate;
  }

  public byte[] getAmountEnc() {
    return amountEnc;
  }

  public byte[] getDescriptionEnc() {
    return descriptionEnc;
  }

  public String getSource() {
    return source;
  }
}
