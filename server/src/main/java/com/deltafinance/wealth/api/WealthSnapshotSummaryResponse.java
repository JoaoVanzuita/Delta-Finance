package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.WealthSnapshotSummaryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WealthSnapshotSummaryResponse(
    UUID id,
    LocalDate referenceDate,
    Instant capturedAt,
    String trigger,
    String completeness,
    String grossAssets,
    String liabilities,
    String netWorth,
    int itemCount,
    int includedItemCount,
    boolean hasPartialData) {

  public static WealthSnapshotSummaryResponse from(WealthSnapshotSummaryView view) {
    return new WealthSnapshotSummaryResponse(
        view.id(),
        view.referenceDate(),
        view.capturedAt(),
        view.trigger(),
        view.completeness(),
        view.grossAssets(),
        view.liabilities(),
        view.netWorth(),
        view.itemCount(),
        view.includedItemCount(),
        view.hasPartialData());
  }
}
