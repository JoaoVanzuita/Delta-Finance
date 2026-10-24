package com.deltafinance.unit.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Cobertura integral das operações monetárias críticas (T199). */
class MoneyTest {

  @Test
  void normalizaEscalaEMoedaComArredondamentoHalfEven() {
    assertThat(Money.of("10.005").toPlainString()).isEqualTo("10.00");
    assertThat(Money.of("10.015").toPlainString()).isEqualTo("10.02");
    assertThat(new Money(new BigDecimal("1"), "brl").currency()).isEqualTo("BRL");
    assertThat(Money.ZERO_BRL.isZero()).isTrue();
    assertThat(Money.zero()).isEqualTo(Money.of("0.00"));
  }

  @Test
  void rejeitaMoedaOuValorInvalido() {
    assertThatThrownBy(() -> new Money(null, "BRL")).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Money(BigDecimal.ONE, "BR"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void operaSomenteComAMesmaMoeda() {
    Money ten = Money.of("10.00");
    Money five = Money.of("5.00");
    assertThat(ten.plus(five).toPlainString()).isEqualTo("15.00");
    assertThat(ten.minus(five).toPlainString()).isEqualTo("5.00");
    assertThat(ten.negate().toPlainString()).isEqualTo("-10.00");
    assertThat(ten.negate().abs()).isEqualTo(ten);
    assertThat(ten.multipliedBy(new BigDecimal("2.5")).toPlainString()).isEqualTo("25.00");
    assertThat(ten.isSameCurrency(five)).isTrue();
    assertThat(ten.compareTo(five)).isPositive();
    assertThat(five.compareTo(ten)).isNegative();
    assertThat(ten.compareTo(Money.of("10.00"))).isZero();
    assertThat(ten.isPositive()).isTrue();
    assertThat(ten.negate().isNegative()).isTrue();
    assertThat(Money.of("0.00").isZero()).isTrue();
    assertThat(ten.toString()).isEqualTo("10.00 BRL");

    Money dollar = Money.of("10.00", "USD");
    assertThatThrownBy(() -> ten.plus(dollar)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ten.minus(dollar)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ten.compareTo(dollar)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ten.plus(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ten.multipliedBy(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void igualdadeIgnoraEscalaMasNaoMoeda() {
    assertThat(Money.of("10.0")).isEqualTo(Money.of("10.00"));
    assertThat(Money.of("10.00").hashCode()).isEqualTo(Money.of("10.000").hashCode());
    assertThat(Money.of("10.00")).isNotEqualTo(Money.of("10.00", "USD"));
    assertThat(Money.of("10.00")).isNotEqualTo("10.00");
    assertThat(Money.of(new BigDecimal("2.5"), "BRL")).isEqualTo(Money.of("2.50"));
  }
}
