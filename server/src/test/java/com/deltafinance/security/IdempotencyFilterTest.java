package com.deltafinance.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Classificação das operações que aceitam {@code Idempotency-Key}. */
class IdempotencyFilterTest {

  @Test
  void protegeEscritasDeTransacoesItensEPagamentos() {
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/transactions")).isTrue();
    assertThat(IdempotencyFilter.isProtected("PATCH", "/api/v1/transactions/abc")).isTrue();
    assertThat(IdempotencyFilter.isProtected("DELETE", "/api/v1/transactions/abc")).isTrue();
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/invoices/abc/items")).isTrue();
    assertThat(IdempotencyFilter.isProtected("PATCH", "/api/v1/invoice-items/abc")).isTrue();
    assertThat(IdempotencyFilter.isProtected("DELETE", "/api/v1/invoice-items/abc")).isTrue();
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/invoices/abc/payments")).isTrue();
  }

  @Test
  void ignoraDemaisMutacoesELeituras() {
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/accounts")).isFalse();
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/connections")).isFalse();
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/connections/abc/sync")).isFalse();
    assertThat(IdempotencyFilter.isProtected("PATCH", "/api/v1/goals/abc")).isFalse();
    assertThat(IdempotencyFilter.isProtected("DELETE", "/api/v1/investments/income/abc")).isFalse();
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/categories")).isFalse();
    assertThat(IdempotencyFilter.isProtected("GET", "/api/v1/transactions")).isFalse();
  }

  @Test
  void naoConfundePrefixoComCaminhoProtegido() {
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/transactions/abc")).isFalse();
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/transactions-extra")).isFalse();
    assertThat(IdempotencyFilter.isProtected("POST", "/api/v1/invoices/abc/items/extra")).isFalse();
    assertThat(IdempotencyFilter.isProtected("PATCH", "/api/v1/invoice-items")).isFalse();
    assertThat(IdempotencyFilter.isProtected("PUT", "/api/v1/transactions/abc")).isFalse();
  }
}
