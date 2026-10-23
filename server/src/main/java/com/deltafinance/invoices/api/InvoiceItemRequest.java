package com.deltafinance.invoices.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceItemRequest(
    @NotBlank(message = "Informe o tipo do item.") String kind,
    @NotBlank(message = "Informe o efeito do item.") String reportingEffect,
    @NotBlank(message = "Informe a descrição.") @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.") String description,
    @NotBlank(message = "Informe o valor.") String amount,
    @NotNull(message = "Informe a data.") LocalDate occurredOn,
    UUID transactionId,
    Integer installmentNumber,
    Integer totalInstallments) {}
