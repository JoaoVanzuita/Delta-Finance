package com.deltafinance.accounts.api;

import jakarta.validation.constraints.NotBlank;

/** Tratamento do saldo reservado no patrimônio consolidado. */
public record ReservedBalanceTreatmentRequest(
    @NotBlank(message = "Informe o tratamento do saldo reservado.") String treatment) {}
