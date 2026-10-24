package com.deltafinance.goals.api;

import jakarta.validation.constraints.NotBlank;

/** Ação explícita de estado; progresso de PAUSED/ARCHIVED fica congelado. */
public record GoalStatusRequest(@NotBlank(message = "Informe o estado desejado.") String status) {}
