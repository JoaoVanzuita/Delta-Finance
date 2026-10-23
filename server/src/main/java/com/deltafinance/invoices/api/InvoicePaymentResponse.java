package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.InvoicePaymentView;
import java.time.LocalDate;
import java.util.UUID;

public record InvoicePaymentResponse(
    UUID id,
    UUID invoiceId,
    UUID paymentAccountId,
    String amount,
    LocalDate paidOn,
    String status,
    String source) {

  public static InvoicePaymentResponse from(InvoicePaymentView view) {
    return new InvoicePaymentResponse(
        view.id(),
        view.invoiceId(),
        view.paymentAccountId(),
        view.amount(),
        view.paidOn(),
        view.status(),
        view.source());
  }
}
