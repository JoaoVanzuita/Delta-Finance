package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.CardOverviewView;
import java.time.LocalDate;
import java.util.List;

public record CardOverviewResponse(
    String totalOutstanding,
    String totalLimit,
    String totalUsed,
    LocalDate nextDueDate,
    List<CardOverviewItemResponse> items) {

  public static CardOverviewResponse from(CardOverviewView view) {
    return new CardOverviewResponse(
        view.totalOutstanding(),
        view.totalLimit(),
        view.totalUsed(),
        view.nextDueDate(),
        view.items().stream().map(CardOverviewItemResponse::from).toList());
  }
}
