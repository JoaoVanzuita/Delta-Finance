package com.deltafinance.accounts.api;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Atualização de conta manual; o tipo é imutável e não faz parte do contrato. */
public record AccountUpdateRequest(
    @Size(max = 120, message = "O apelido deve ter no máximo 120 caracteres.") String alias,
    String referenceBalance,
    Instant referenceDate,
    @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres.") String notes,
    String status) {}
