package com.deltafinance.accounts.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ManualAccountRequest(
    @NotBlank(message = "Informe o tipo da conta.") String type,
    @NotBlank(message = "Informe o apelido da conta.") @Size(max = 120, message = "O apelido deve ter no máximo 120 caracteres.") String alias,
    @NotBlank(message = "Informe o saldo de referência.") String referenceBalance,
    @NotNull(message = "Informe a data de referência.") Instant referenceDate,
    String currency,
    @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres.") String notes) {}
