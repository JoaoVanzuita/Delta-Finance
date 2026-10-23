package com.deltafinance.transactions.api;

import com.deltafinance.transactions.application.TransactionView;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
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
    UUID relatedTransactionId) {

  public static TransactionResponse from(TransactionView view) {
    return new TransactionResponse(
        view.id(),
        view.accountId(),
        view.categoryId(),
        view.categoryName(),
        view.source(),
        view.type(),
        view.status(),
        view.postedOn(),
        view.amount(),
        view.currency(),
        view.description(),
        view.reconciliationState(),
        view.relatedTransactionId());
  }
}
