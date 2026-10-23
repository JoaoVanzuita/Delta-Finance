package com.deltafinance.invoices.application;

import java.time.Instant;
import java.util.UUID;

/** Correção auditável de item de fatura com valores decifrados. */
public record InvoiceItemCorrectionView(
    UUID id,
    UUID invoiceItemId,
    String fieldName,
    String oldValue,
    String newValue,
    String reason,
    Instant changedAt) {}
