package com.deltafinance.goals.api;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Alocação explícita; exatamente um entre valor fixo e percentual. */
public record GoalAllocationRequest(
    @NotBlank(message = "Informe a origem da alocação.") String sourceType,
    UUID sourceId,
    @NotBlank(message = "Informe o modo da alocação.") String allocationMode,
    String allocatedAmount,
    String percentage) {}
