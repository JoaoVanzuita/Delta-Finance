package com.deltafinance.invoices.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirmação explícita para alinhar a fatura local ao total da Bill oficial. */
public record InvoiceReconciliationRequest(
    @NotBlank(message = "Informe o motivo do ajuste.") @Size(max = 300, message = "O motivo deve ter no máximo 300 caracteres.") String reason) {}
