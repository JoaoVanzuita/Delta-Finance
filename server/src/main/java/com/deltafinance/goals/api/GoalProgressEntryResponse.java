package com.deltafinance.goals.api;

import com.deltafinance.goals.application.GoalProgressEntryView;
import java.time.LocalDate;

/** Entrada do histórico de progresso. */
public record GoalProgressEntryResponse(
    LocalDate referenceDate,
    String currentValue,
    String percentage,
    String remainingAmount,
    String ruleVersion) {

  public static GoalProgressEntryResponse from(GoalProgressEntryView view) {
    return new GoalProgressEntryResponse(
        view.referenceDate(),
        view.currentValue(),
        view.percentage(),
        view.remainingAmount(),
        view.ruleVersion());
  }
}
