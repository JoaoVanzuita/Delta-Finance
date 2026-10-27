package com.deltafinance.goals.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Visão completa de uma meta, já calculada e decifrada. */
public record GoalView(
    UUID id,
    String name,
    String description,
    String notes,
    String type,
    String targetAmount,
    LocalDate targetDate,
    String status,
    String progressRule,
    Instant changedAt,
    Instant createdAt,
    String currentValue,
    String percentage,
    String remainingAmount,
    String unallocatedAmount,
    String excessAmount,
    List<GoalAllocationView> allocations,
    List<GoalProgressEntryView> progressHistory) {}
