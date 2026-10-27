package com.deltafinance.invoices.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Bill oficial comparada com a fatura local.
 *
 * <p>Os campos {@code *Difference} são {@code local - provedor}. Uma fatura está conciliada quando
 * total e pagamentos oficiais coincidem com os locais. {@code ignoredPayments} conta pagamentos da
 * Bill descartados por estarem fora da janela do ciclo (dados antigos do conector).
 */
public record ProviderBillView(
    LocalDate closingDate,
    LocalDate dueDate,
    String total,
    String minimumPayment,
    String paymentsTotal,
    String totalDifference,
    String paymentsDifference,
    boolean reconciled,
    int ignoredPayments,
    LocalDate syncedOn,
    List<ProviderBillPaymentView> payments) {}
