package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.InvoiceItemView;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceItemResponse(
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
    long correctionCount) {

  public static InvoiceItemResponse from(InvoiceItemView view) {
    return new InvoiceItemResponse(
        view.id(),
        view.invoiceId(),
        view.transactionId(),
        view.kind(),
        view.reportingEffect(),
        view.source(),
        view.description(),
        view.amount(),
        view.occurredOn(),
        view.installmentNumber(),
        view.totalInstallments(),
        view.correctionCount());
  }
}
