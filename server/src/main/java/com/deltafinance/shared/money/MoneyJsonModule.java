package com.deltafinance.shared.money;

import java.math.BigDecimal;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Serializa todo {@link BigDecimal} como string decimal.
 *
 * <p>Motivo: o cliente JavaScript não deve receber ponto flutuante binário nem perder precisão.
 * Exemplo: {@code 1234.50} trafega como {@code "1234.50"}, nunca como {@code 1234.5} de um double.
 */
public final class MoneyJsonModule extends SimpleModule {

  public MoneyJsonModule() {
    super("delta-finance-money");
    addSerializer(BigDecimal.class, new BigDecimalAsStringSerializer());
    addDeserializer(BigDecimal.class, new BigDecimalFromStringDeserializer());
  }

  static final class BigDecimalAsStringSerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
      generator.writeString(value.toPlainString());
    }
  }

  static final class BigDecimalFromStringDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
      String value = parser.getString();
      if (value == null || value.isBlank()) {
        return null;
      }
      try {
        return new BigDecimal(value.trim());
      } catch (NumberFormatException exception) {
        return (BigDecimal)
            context.handleWeirdStringValue(BigDecimal.class, value, "valor decimal inválido");
      }
    }
  }
}
