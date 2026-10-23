package com.deltafinance.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário imutável com moeda ISO-4217.
 *
 * <p>Regras deliberadas:
 *
 * <ul>
 *   <li>{@link BigDecimal} é obrigatório; nunca {@code double}/{@code float};
 *   <li>a escala canônica de BRL é 2, com arredondamento {@link RoundingMode#HALF_EVEN};
 *   <li>operações entre moedas diferentes falham em vez de converter silenciosamente;
 *   <li>a serialização JSON sempre usa string decimal (ver {@link MoneyJsonModule}).
 * </ul>
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

  public static final String BRL = "BRL";
  public static final int SCALE = 2;
  public static final Money ZERO_BRL = new Money(BigDecimal.ZERO, BRL);

  public Money {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    if (currency.length() != 3) {
      throw new IllegalArgumentException("Moeda deve usar o código ISO de 3 letras");
    }
    currency = currency.toUpperCase(java.util.Locale.ROOT);
    amount = amount.setScale(SCALE, RoundingMode.HALF_EVEN);
  }

  public static Money of(String amount) {
    return new Money(new BigDecimal(amount), BRL);
  }

  public static Money of(BigDecimal amount) {
    return new Money(amount, BRL);
  }

  public static Money of(String amount, String currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public static Money of(BigDecimal amount, String currency) {
    return new Money(amount, currency);
  }

  public static Money zero() {
    return ZERO_BRL;
  }

  public Money plus(Money other) {
    return new Money(amount.add(requireSameCurrency(other).amount), currency);
  }

  public Money minus(Money other) {
    return new Money(amount.subtract(requireSameCurrency(other).amount), currency);
  }

  public Money negate() {
    return new Money(amount.negate(), currency);
  }

  public Money abs() {
    return new Money(amount.abs(), currency);
  }

  /**
   * Multiplica por um fator não monetário (quantidade, percentual), arredondando o resultado para a
   * escala monetária. O fator não é arredondado.
   */
  public Money multipliedBy(BigDecimal factor) {
    Objects.requireNonNull(factor, "factor");
    return new Money(amount.multiply(factor), currency);
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  public boolean isSameCurrency(Money other) {
    return currency.equals(other.currency);
  }

  public String toPlainString() {
    return amount.toPlainString();
  }

  private Money requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other");
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Moedas incompatíveis: " + currency + " e " + other.currency);
    }
    return other;
  }

  @Override
  public int compareTo(Money other) {
    return amount.compareTo(requireSameCurrency(other).amount);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Money money
        && amount.compareTo(money.amount) == 0
        && currency.equals(money.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount.stripTrailingZeros(), currency);
  }

  @Override
  public String toString() {
    return toPlainString() + " " + currency;
  }
}
