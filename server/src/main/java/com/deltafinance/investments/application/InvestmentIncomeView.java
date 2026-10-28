package com.deltafinance.investments.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Proventos de investimento, com origem explícita. */
public record InvestmentIncomeView(
    LocalDate from,
    LocalDate to,
    String total,
    String source,
    List<Month> byMonth,
    List<Item> items) {

  public record Month(String month, String amount, int count) {}

  public record Item(
      UUID id,
      String targetType,
      UUID investmentId,
      String investmentName,
      String code,
      String type,
      String incomeType,
      LocalDate date,
      String amount,
      String description,
      String source,
      boolean editable) {}
}
