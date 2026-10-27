package com.deltafinance.goals.api;

import com.deltafinance.goals.application.GoalAllocationView;
import java.util.UUID;

/** Alocação explícita com a contribuição efetiva. */
public record GoalAllocationResponse(
    UUID id,
    String sourceType,
    UUID sourceId,
    String sourceName,
    String allocationMode,
    String allocatedAmount,
    String percentage,
    String contributionAmount,
    boolean capacityExceeded) {

  public static GoalAllocationResponse from(GoalAllocationView view) {
    return new GoalAllocationResponse(
        view.id(),
        view.sourceType(),
        view.sourceId(),
        view.sourceName(),
        view.allocationMode(),
        view.allocatedAmount(),
        view.percentage(),
        view.contributionAmount(),
        view.capacityExceeded());
  }
}
