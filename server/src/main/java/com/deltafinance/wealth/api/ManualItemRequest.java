package com.deltafinance.wealth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Ativo ou passivo manual (valor sempre positivo; a direção vem de {@code kind}). */
public record ManualItemRequest(
    @NotBlank(message = "Informe se é ativo ou passivo.") String kind,
    @NotBlank(message = "Informe o tipo do item.") @Size(max = 60, message = "O tipo deve ter no máximo 60 caracteres.") String type,
    @NotBlank(message = "Informe a descrição.") @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres.") String description,
    @NotBlank(message = "Informe o valor de referência.") String referenceValue,
    @NotNull(message = "Informe a data de referência.") LocalDate referenceDate,
    @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres.") String notes) {}
