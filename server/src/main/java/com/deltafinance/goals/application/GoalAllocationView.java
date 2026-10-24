package com.deltafinance.goals.application;

import java.util.UUID;

/** Alocação explícita com a contribuição efetiva após resíduo. */
public record GoalAllocationView(
    UUID id,
    String sourceType,
    UUID sourceId,
    String allocationMode,
    String allocatedAmount,
    String percentage,
    String contributionAmount) {}
