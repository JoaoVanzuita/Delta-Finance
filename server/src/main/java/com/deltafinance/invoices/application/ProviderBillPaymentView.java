package com.deltafinance.invoices.application;

import java.time.LocalDate;

/** Pagamento oficial de uma Bill, exibido apenas na conciliação. */
public record ProviderBillPaymentView(
    LocalDate paidOn, String amount, String paymentMode, String valueType) {}
