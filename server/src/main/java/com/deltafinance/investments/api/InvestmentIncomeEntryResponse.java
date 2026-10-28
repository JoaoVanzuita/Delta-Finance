package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentIncomeEntryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvestmentIncomeEntryResponse(
    UUID id,
    String targetType,
    UUID targetId,
    String targetName,
    String targetCode,
    String incomeType,
    LocalDate incomeDate,
    String amount,
    String description,
    String source,
    Instant createdAt) {

  public static InvestmentIncomeEntryResponse from(InvestmentIncomeEntryView view) {
    return new InvestmentIncomeEntryResponse(
        view.id(),
        view.targetType(),
        view.targetId(),
        view.targetName(),
        view.targetCode(),
        view.incomeType(),
        view.incomeDate(),
        view.amount(),
        view.description(),
        view.source(),
        view.createdAt());
  }
}
