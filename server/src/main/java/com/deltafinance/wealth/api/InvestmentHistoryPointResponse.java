package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.InvestmentHistoryPointView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvestmentHistoryPointResponse(
    UUID snapshotId,
    LocalDate referenceDate,
    String totalValue,
    String completeness,
    List<InvestmentClassSliceResponse> allocation) {

  public static InvestmentHistoryPointResponse from(InvestmentHistoryPointView view) {
    return new InvestmentHistoryPointResponse(
        view.snapshotId(),
        view.referenceDate(),
        view.totalValue(),
        view.completeness(),
        view.allocation().stream().map(InvestmentClassSliceResponse::from).toList());
  }
}
