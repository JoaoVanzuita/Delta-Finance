package com.deltafinance.invoices.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Visão consolidada dos cartões de crédito do usuário. */
public record CardOverviewView(
    String totalOutstanding,
    String totalLimit,
    String totalUsed,
    LocalDate nextDueDate,
    List<CardItem> items) {

  /** Cartão com ciclo, fatura atual, próximo vencimento e pendências de alocação. */
  public record CardItem(
      UUID accountId,
      String alias,
      String institutionName,
      String status,
      String currency,
      boolean hasCycle,
      Integer closingDay,
      Integer dueDay,
      String creditLimit,
      String creditBalance,
      String availableCreditLimit,
      String brand,
      String level,
      String last4,
      String usedAmount,
      String outstanding,
      String utilization,
      LocalDate nextDueDate,
      InvoiceView currentInvoice,
      InvoiceView nextDueInvoice,
      int unallocatedCount) {}
}
