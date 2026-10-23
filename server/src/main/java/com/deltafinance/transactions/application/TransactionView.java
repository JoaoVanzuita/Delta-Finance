package com.deltafinance.transactions.application;

import java.time.LocalDate;
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
    String reconciliationState,
    UUID relatedTransactionId) {}
