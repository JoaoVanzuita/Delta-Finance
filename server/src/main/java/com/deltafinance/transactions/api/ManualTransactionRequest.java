package com.deltafinance.transactions.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record ManualTransactionRequest(
    @NotNull(message = "Informe a conta.") UUID accountId,
    @NotBlank(message = "Informe o tipo do lançamento.") String type,
    @NotNull(message = "Informe a data.") LocalDate postedOn,
    @NotBlank(message = "Informe o valor.") String amount,
    @NotBlank(message = "Informe a descrição.") @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.") String description,
    UUID categoryId,
    UUID relatedTransactionId,
    @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres.") String notes,
    @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres.") String adjustmentReason,
    UUID transferAccountId) {}
