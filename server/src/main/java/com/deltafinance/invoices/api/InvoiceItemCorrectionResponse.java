package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.InvoiceItemCorrectionView;
import java.time.Instant;
import java.util.UUID;

public record InvoiceItemCorrectionResponse(
    UUID id,
    UUID invoiceItemId,
    String fieldName,
    String oldValue,
    String newValue,
    String reason,
    Instant changedAt) {

  public static InvoiceItemCorrectionResponse from(InvoiceItemCorrectionView view) {
    return new InvoiceItemCorrectionResponse(
        view.id(),
        view.invoiceItemId(),
        view.fieldName(),
        view.oldValue(),
        view.newValue(),
        view.reason(),
        view.changedAt());
  }
}
