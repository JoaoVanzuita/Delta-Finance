package com.deltafinance.reporting.application;

import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Agregação monetária dos relatórios.
 *
 * <p>Valores trafegam como strings decimais entre módulos; esta classe converte para {@link
 * BigDecimal}, aplica a faixa de valor (por magnitude, para que o filtro "de R$ 50 a R$ 200" inclua
 * despesas e receitas) e devolve valores normalizados na escala de BRL.
 */
public final class MoneyAggregator {

  private static final BigDecimal ZERO = Money.scaled(BigDecimal.ZERO);

  private MoneyAggregator() {}

  public static BigDecimal parse(String value) {
    if (value == null || value.isBlank()) {
      return ZERO;
    }
    return Money.scaled(new BigDecimal(value));
  }

  public static BigDecimal zero() {
    return ZERO;
  }

  public static BigDecimal sum(BigDecimal left, BigDecimal right) {
    return Money.scaled(left.add(right));
  }

  public static BigDecimal abs(BigDecimal value) {
    return Money.scaled(value.abs());
  }

  /** Faixa inclusiva aplicada à magnitude do valor. Limites negativos são normalizados. */
  public static boolean withinRange(BigDecimal value, BigDecimal min, BigDecimal max) {
    BigDecimal magnitude = value.abs();
    if (min != null && magnitude.compareTo(min.abs()) < 0) {
      return false;
    }
    if (max != null && magnitude.compareTo(max.abs()) > 0) {
      return false;
    }
    return true;
  }

  public static String format(BigDecimal value) {
    Objects.requireNonNull(value, "value");
    return Money.plain(value);
  }
}
