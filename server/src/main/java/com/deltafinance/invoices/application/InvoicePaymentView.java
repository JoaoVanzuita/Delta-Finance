package com.deltafinance.invoices.application;

import java.time.LocalDate;
import java.util.UUID;

/** Visão pública de pagamento de fatura. */
public record InvoicePaymentView(
    UUID id,
    UUID invoiceId,
    UUID paymentAccountId,
    String amount,
    LocalDate paidOn,
    String status,
    String source) {}
