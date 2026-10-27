package com.deltafinance.goals.api;

import com.deltafinance.goals.application.GoalView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Meta financeira com progresso, alocações e histórico. */
public record GoalResponse(
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
    List<GoalAllocationResponse> allocations,
    List<GoalProgressEntryResponse> progressHistory) {

  public static GoalResponse from(GoalView view) {
    return new GoalResponse(
        view.id(),
        view.name(),
        view.description(),
        view.notes(),
        view.type(),
        view.targetAmount(),
        view.targetDate(),
        view.status(),
        view.progressRule(),
        view.changedAt(),
        view.createdAt(),
        view.currentValue(),
        view.percentage(),
        view.remainingAmount(),
        view.unallocatedAmount(),
        view.excessAmount(),
        view.allocations().stream().map(GoalAllocationResponse::from).toList(),
        view.progressHistory().stream().map(GoalProgressEntryResponse::from).toList());
  }
}
