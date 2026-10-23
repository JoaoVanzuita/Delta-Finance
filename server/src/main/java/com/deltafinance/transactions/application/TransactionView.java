package com.deltafinance.transactions.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Visão pública de transação com valores decifrados. */
public record TransactionView(
    UUID id,
    UUID accountId,
    UUID categoryId,
    String categoryName,
    String source,
    String type,
    String status,
    LocalDate postedOn,
    String amount,
    String currency,
    String description,
    List<String> editedFields,
    String reconciliationState,
    String notes,
    String adjustmentReason,
    UUID relatedTransactionId,
    UUID counterpartTransactionId,
    UUID invoiceId) {}
