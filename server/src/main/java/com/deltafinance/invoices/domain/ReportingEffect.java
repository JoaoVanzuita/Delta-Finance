package com.deltafinance.invoices.domain;

/** Direção do efeito de um item de fatura; o valor é sempre magnitude positiva. */
public enum ReportingEffect {
  EXPENSE_INCREASE,
  EXPENSE_DECREASE,
  NONE
}
