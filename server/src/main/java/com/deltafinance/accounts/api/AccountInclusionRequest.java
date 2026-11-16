package com.deltafinance.accounts.api;

import jakarta.validation.constraints.NotNull;

public record AccountInclusionRequest(
    @NotNull(message = "Informe se a conta soma no saldo consolidado.") Boolean includeInTotal) {}
