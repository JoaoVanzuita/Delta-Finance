package com.deltafinance.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Relógio da aplicação.
 *
 * <p>Instantes são sempre {@link Instant} (UTC) e regras de calendário usam {@link #zone()}. O fuso
 * padrão do MVP é {@code America/Sao_Paulo}; testes podem injetar um relógio fixo para evitar
 * dependência de horário real.
 */
public interface Clock {

  Instant now();

  ZoneId zone();

  default LocalDate today() {
    return LocalDate.ofInstant(now(), zone());
  }

  static Clock fixed(Instant instant, ZoneId zone) {
    return new Clock() {
      @Override
      public Instant now() {
        return instant;
      }

      @Override
      public ZoneId zone() {
        return zone;
      }
    };
  }
}
