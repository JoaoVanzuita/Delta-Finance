package com.deltafinance.unit.money;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.shared.money.MoneyJsonModule;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MoneyJsonModuleTest {

  private final JsonMapper mapper = JsonMapper.builder().addModule(new MoneyJsonModule()).build();

  @Test
  void serializaBigDecimalComoStringDecimal() {
    assertThat(mapper.writeValueAsString(new BigDecimal("1234.50"))).isEqualTo("\"1234.50\"");
    assertThat(mapper.writeValueAsString(new BigDecimal("-0.01"))).isEqualTo("\"-0.01\"");
  }

  @Test
  void desserializaStringDecimalSemPerderPrecisao() {
    BigDecimal value = mapper.readValue("\"1234.50\"", BigDecimal.class);

    assertThat(value).isEqualByComparingTo("1234.50");
    assertThat(value.scale()).isEqualTo(2);
  }
}
