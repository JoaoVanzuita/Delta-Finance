package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentIncomeView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvestmentIncomeResponse(
    LocalDate from,
    LocalDate to,
    String total,
    String source,
    List<MonthResponse> byMonth,
    List<ItemResponse> items) {

  public record MonthResponse(String month, String amount, int count) {}

  public record ItemResponse(
      LocalDate date,
      UUID investmentId,
      String investmentName,
      String code,
      String type,
      String amount,
      String description,
      String source) {}

  public static InvestmentIncomeResponse from(InvestmentIncomeView view) {
    return new InvestmentIncomeResponse(
        view.from(),
        view.to(),
        view.total(),
        view.source(),
        view.byMonth().stream()
            .map(month -> new MonthResponse(month.month(), month.amount(), month.count()))
            .toList(),
        view.items().stream()
            .map(
                item ->
                    new ItemResponse(
                        item.date(),
                        item.investmentId(),
                        item.investmentName(),
                        item.code(),
                        item.type(),
                        item.amount(),
                        item.description(),
                        item.source()))
            .toList());
  }
}
