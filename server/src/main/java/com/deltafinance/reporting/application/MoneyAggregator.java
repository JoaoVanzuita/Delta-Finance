package com.deltafinance.reporting.application;

import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Agregação monetária dos relatórios.
 *
 * <p>Valores trafegam como strings decimais entre módulos; esta classe converte para {@link
 * BigDecimal}, aplica a faixa de valor (por magnitude, para que o filtro "de R$ 50 a R$ 200" inclua
 * despesas e receitas) e devolve valores normalizados na escala de BRL.
 */
public final class MoneyAggregator {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Money.SCALE);

  private MoneyAggregator() {}

  public static BigDecimal parse(String value) {
    if (value == null || value.isBlank()) {
      return ZERO;
    }
    return new BigDecimal(value).setScale(Money.SCALE, RoundingMode.HALF_EVEN);
  }

  public static BigDecimal zero() {
    return ZERO;
  }

  public static BigDecimal sum(BigDecimal left, BigDecimal right) {
    return left.add(right).setScale(Money.SCALE, RoundingMode.HALF_EVEN);
  }

  public static BigDecimal abs(BigDecimal value) {
    return value.abs().setScale(Money.SCALE, RoundingMode.HALF_EVEN);
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
    return value.setScale(Money.SCALE, RoundingMode.HALF_EVEN).toPlainString();
  }
}
