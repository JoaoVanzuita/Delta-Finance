package com.deltafinance.transactions.api;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Correção de campos de uma transação; campos nulos permanecem inalterados. */
public record TransactionCorrectionRequest(
    @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.") String description,
    LocalDate postedOn,
    String amount,
    UUID categoryId,
    @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres.") String notes) {}
