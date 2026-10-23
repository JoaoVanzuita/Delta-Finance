package com.deltafinance.categories.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank(message = "Informe o nome da categoria.") @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres.") String name,
    @NotNull(message = "Informe o tipo da categoria.") String kind) {}
