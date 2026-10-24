package com.deltafinance.reporting.api;

import com.deltafinance.reporting.application.ReportEntryView;
import java.time.LocalDate;
import java.util.UUID;

public record ReportEntryResponse(
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

  public static ReportEntryResponse from(ReportEntryView view) {
    return new ReportEntryResponse(
        view.id(),
        view.source(),
        view.sourceId(),
        view.origin(),
        view.occurredOn(),
        view.amount(),
        view.description(),
        view.categoryId(),
        view.accountId(),
        view.invoiceId(),
        view.transactionId(),
        view.type());
  }
}
