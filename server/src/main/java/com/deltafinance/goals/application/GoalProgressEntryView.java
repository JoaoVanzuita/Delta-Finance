package com.deltafinance.goals.application;

import java.time.LocalDate;

/** Entrada do histórico diário de progresso. */
public record GoalProgressEntryView(
    LocalDate referenceDate,
    String currentValue,
    String percentage,
    String remainingAmount,
    String ruleVersion) {}
