package com.deltafinance.accounts.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nome de exibição local de uma conta ou cartão importado. */
public record AccountDisplayNameRequest(
    @NotBlank(message = "Informe o nome de exibição.") @Size(max = 120, message = "O nome de exibição deve ter no máximo 120 caracteres.") String displayName) {}
