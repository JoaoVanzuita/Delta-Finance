package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.InvoiceDetailView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceDetailResponse(
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
    int adjustmentCount,
    List<InvoiceItemResponse> items,
    List<InvoicePaymentResponse> payments) {

  public static InvoiceDetailResponse from(InvoiceDetailView view) {
    var invoice = view.invoice();
    return new InvoiceDetailResponse(
        invoice.id(),
        invoice.cardAccountId(),
        invoice.cycleStart(),
        invoice.cycleEnd(),
        invoice.closingDate(),
        invoice.dueDate(),
        invoice.status(),
        invoice.total(),
        invoice.paymentsTotal(),
        invoice.outstanding(),
        invoice.appliedCredit(),
        invoice.minimumPayment(),
        invoice.source(),
        invoice.hasAdjustments(),
        invoice.adjustmentCount(),
        view.items().stream().map(InvoiceItemResponse::from).toList(),
        view.payments().stream().map(InvoicePaymentResponse::from).toList());
  }
}
