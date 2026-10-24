package com.deltafinance.reporting.application;

import java.time.LocalDate;
import java.util.UUID;

/** Entrada do drill-down serializada na API, com valor decimal em string. */
public record ReportEntryView(
    UUID id,
    String source,
    UUID sourceId,
    String origin,
    LocalDate occurredOn,
    String amount,
    String description,
    UUID categoryId,
    UUID accountId,
    UUID invoiceId,
    UUID transactionId,
    String type) {

  public static ReportEntryView from(ReportEntry entry) {
    return new ReportEntryView(
        entry.id(),
        entry.source(),
        entry.sourceId(),
        entry.origin(),
        entry.occurredOn(),
        MoneyAggregator.format(entry.amount()),
        entry.description(),
        entry.categoryId(),
        entry.accountId(),
        entry.invoiceId(),
        entry.transactionId(),
        entry.type());
  }
}
