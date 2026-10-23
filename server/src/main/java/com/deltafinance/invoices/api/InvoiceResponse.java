package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.InvoiceView;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
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
    int adjustmentCount) {

  public static InvoiceResponse from(InvoiceView view) {
    return new InvoiceResponse(
        view.id(),
        view.cardAccountId(),
        view.cycleStart(),
        view.cycleEnd(),
        view.closingDate(),
        view.dueDate(),
        view.status(),
        view.total(),
        view.paymentsTotal(),
        view.outstanding(),
        view.appliedCredit(),
        view.minimumPayment(),
        view.source(),
        view.hasAdjustments(),
        view.adjustmentCount());
  }
}
