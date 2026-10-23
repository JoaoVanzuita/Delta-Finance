package com.deltafinance.invoices.application;

import java.util.List;

/** Fatura com itens e pagamentos. */
public record InvoiceDetailView(
    InvoiceView invoice, List<InvoiceItemView> items, List<InvoicePaymentView> payments) {}
