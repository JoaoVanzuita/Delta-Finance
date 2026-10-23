package com.deltafinance.invoices.application;

import java.time.LocalDate;
import java.util.UUID;

/** Visão pública de item de fatura. */
public record InvoiceItemView(
    UUID id,
    UUID invoiceId,
    UUID transactionId,
    String kind,
    String reportingEffect,
    String source,
    String description,
    String amount,
    LocalDate occurredOn,
    Integer installmentNumber,
    Integer totalInstallments,
    long correctionCount) {}
