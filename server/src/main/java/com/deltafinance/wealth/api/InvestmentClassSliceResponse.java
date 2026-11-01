package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.InvestmentClassSliceView;

public record InvestmentClassSliceResponse(
    String assetClass, String value, String percentage, int positionCount) {

  public static InvestmentClassSliceResponse from(InvestmentClassSliceView view) {
    return new InvestmentClassSliceResponse(
        view.assetClass(), view.value(), view.percentage(), view.positionCount());
  }
}
