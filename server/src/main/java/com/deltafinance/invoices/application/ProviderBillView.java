package com.deltafinance.invoices.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Bill oficial comparada com a fatura local.
 *
 * <p>Os campos {@code *Difference} são {@code local - provedor}. {@code paymentsTotal} e {@code
 * paymentsDifference} usam os pagamentos da Bill seguinte aplicados à Bill anterior, não o total
 * bruto do ciclo seguinte. {@code financeCharges} e {@code settled} expõem a regra de quitação da
 * Pluggy. {@code ignoredPayments} conta pagamentos descartados por estarem fora da janela do ciclo
 * (dados antigos do conector).
 */
public record ProviderBillView(
    LocalDate closingDate,
    LocalDate dueDate,
    String total,
    String minimumPayment,
    String paymentsTotal,
    String financeCharges,
    boolean settled,
    String totalDifference,
    String paymentsDifference,
    boolean reconciled,
    int ignoredPayments,
    LocalDate syncedOn,
    List<ProviderBillPaymentView> payments) {}
