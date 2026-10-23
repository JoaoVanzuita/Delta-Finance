package com.deltafinance.accounts.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** Perfil de cartão: ciclo, limite e crédito acumulado. */
@Entity
@Table(name = "card_profile")
public class CardProfile extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "closing_day", nullable = false)
  private int closingDay;

  @Column(name = "due_day", nullable = false)
  private int dueDay;

  @Column(name = "credit_limit_enc", columnDefinition = "bytea")
  private byte[] creditLimitEnc;

  @Column(name = "credit_balance_enc", columnDefinition = "bytea")
  private byte[] creditBalanceEnc;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 16)
  private CardProfileSource source;

  protected CardProfile() {
    // Hibernate
  }

  public CardProfile(
      UUID userId, UUID accountId, int closingDay, int dueDay, CardProfileSource source) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.closingDay = validDay(closingDay);
    this.dueDay = validDay(dueDay);
    this.source = Objects.requireNonNull(source, "source");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public int getClosingDay() {
    return closingDay;
  }

  public int getDueDay() {
    return dueDay;
  }

  public byte[] getCreditLimitEnc() {
    return creditLimitEnc;
  }

  public byte[] getCreditBalanceEnc() {
    return creditBalanceEnc;
  }

  public CardProfileSource getSource() {
    return source;
  }

  /** Aplica o ciclo sugerido pelo provedor sem marcar configuração manual. */
  public void applyProviderCycle(int closingDay, int dueDay) {
    this.closingDay = validDay(closingDay);
    this.dueDay = validDay(dueDay);
    this.source = CardProfileSource.PROVIDER;
  }

  public void updateCycle(int closingDay, int dueDay) {
    this.closingDay = validDay(closingDay);
    this.dueDay = validDay(dueDay);
    this.source = CardProfileSource.USER;
  }

  public void updateCreditLimit(byte[] creditLimitEnc) {
    this.creditLimitEnc = creditLimitEnc;
  }

  public void updateCreditBalance(byte[] creditBalanceEnc) {
    this.creditBalanceEnc = creditBalanceEnc;
  }

  private static int validDay(int day) {
    if (day < 1 || day > 31) {
      throw new IllegalArgumentException("Dia do cartão deve estar entre 1 e 31");
    }
    return day;
  }
}
