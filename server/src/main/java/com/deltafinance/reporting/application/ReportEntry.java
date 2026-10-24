package com.deltafinance.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entrada interna do drill-down.
 *
 * <p>{@code amount} é assinado: receita positiva, despesa negativa e redução de despesa
 * (reembolso/chargeback/crédito de fatura) positiva. A soma das entradas equivale ao resultado do
 * período; para indicadores de despesa, a magnitude é obtida pela soma dos valores negativos menos
 * as reduções.
 */
public record ReportEntry(
    UUID id,
    String source,
    UUID sourceId,
    String origin,
    LocalDate occurredOn,
    BigDecimal amount,
    String description,
    UUID categoryId,
    UUID accountId,
    UUID invoiceId,
    UUID transactionId,
    String type,
    Direction direction) {

  /** Direção contábil da entrada, independente do sinal bruto da transação. */
  public enum Direction {
    INCOME,
    EXPENSE,
    EXPENSE_REDUCTION
  }
}
