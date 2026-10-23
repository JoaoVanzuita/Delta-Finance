package com.deltafinance.transactions.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Revisão explícita de transação removida na origem. */
public record SourceRemovalReviewRequest(
    @NotBlank(message = "Informe a ação.") String action,
    @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres.") String reason) {}
