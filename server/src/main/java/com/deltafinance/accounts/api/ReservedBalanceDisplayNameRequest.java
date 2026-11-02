package com.deltafinance.accounts.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nome local de um saldo reservado; não altera o nome do provedor. */
public record ReservedBalanceDisplayNameRequest(
    @NotBlank(message = "Informe o nome de exibição.") @Size(max = 120, message = "O nome de exibição deve ter no máximo 120 caracteres.") String displayName) {}
