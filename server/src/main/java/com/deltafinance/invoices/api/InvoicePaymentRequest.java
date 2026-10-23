package com.deltafinance.invoices.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record InvoicePaymentRequest(
    @NotNull(message = "Informe a conta de pagamento.") UUID paymentAccountId,
    @NotBlank(message = "Informe o valor.") String amount,
    @NotNull(message = "Informe a data do pagamento.") LocalDate paidOn) {}
