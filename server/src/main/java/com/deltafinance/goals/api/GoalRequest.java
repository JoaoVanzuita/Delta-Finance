package com.deltafinance.goals.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Criação/edição de meta com alocações explícitas. */
public record GoalRequest(
    @NotBlank(message = "Informe o nome da meta.") @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.") String name,
    @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.") String description,
    @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres.") String notes,
    @NotBlank(message = "Informe o tipo da meta.") String type,
    @NotBlank(message = "Informe o valor-alvo.") String targetAmount,
    LocalDate targetDate,
    @NotBlank(message = "Informe a regra de progresso.") String progressRule,
    List<@Valid GoalAllocationRequest> allocations) {}
