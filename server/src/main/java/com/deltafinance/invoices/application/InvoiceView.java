package com.deltafinance.invoices.application;

import java.time.LocalDate;
import java.util.UUID;

/** Visão pública de fatura. */
public record InvoiceView(
    UUID id,
    UUID cardAccountId,
    LocalDate cycleStart,
    LocalDate cycleEnd,
    LocalDate closingDate,
    LocalDate dueDate,
    String status,
    String total,
    String paymentsTotal,
    String outstanding,
    String appliedCredit,
    String minimumPayment,
    String source,
    boolean hasAdjustments,
    int adjustmentCount) {}
