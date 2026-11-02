package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.ReservedBalanceHistoryPointView;
import java.time.LocalDate;
import java.util.List;

/** Evolução mensal de um saldo reservado. */
public record ReservedBalanceHistoryResponse(List<Point> items) {

  public record Point(LocalDate referenceDate, String value, String currency, boolean included) {}

  public static ReservedBalanceHistoryResponse from(List<ReservedBalanceHistoryPointView> points) {
    return new ReservedBalanceHistoryResponse(
        points.stream()
            .map(
                point ->
                    new Point(
                        point.referenceDate(), point.value(), point.currency(), point.included()))
            .toList());
  }
}
