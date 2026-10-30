package com.deltafinance.connections.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nome de exibição local de um item/conexão do Pluggy. */
public record ConnectionDisplayNameRequest(
    @NotBlank(message = "Informe o nome de exibição.") @Size(max = 120, message = "O nome de exibição deve ter no máximo 120 caracteres.") String displayName) {}
