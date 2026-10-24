package com.deltafinance.wealth.api;

import jakarta.validation.constraints.Size;

/** Observações opcionais do snapshot; a data é sempre hoje em America/Sao_Paulo. */
public record WealthSnapshotRequest(
    @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres.") String notes) {}
