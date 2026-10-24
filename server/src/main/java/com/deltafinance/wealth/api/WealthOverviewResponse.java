package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.WealthOverviewView;
import java.time.LocalDate;
import java.util.List;

/** Visão consolidada de patrimônio. */
public record WealthOverviewResponse(
    LocalDate referenceDate,
    String grossAssets,
    String liabilities,
    String netWorth,
    String completeness,
    boolean hasData,
    List<WealthItemResponse> items) {

  public static WealthOverviewResponse from(WealthOverviewView view) {
    return new WealthOverviewResponse(
        view.referenceDate(),
        view.grossAssets(),
        view.liabilities(),
        view.netWorth(),
        view.completeness(),
        view.hasData(),
        view.items().stream().map(WealthItemResponse::from).toList());
  }
}
