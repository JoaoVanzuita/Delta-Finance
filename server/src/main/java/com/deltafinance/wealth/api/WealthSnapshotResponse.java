package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.WealthSnapshotView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WealthSnapshotResponse(
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
    boolean hasPartialData,
    List<WealthSnapshotItemResponse> items) {

  public static WealthSnapshotResponse from(WealthSnapshotView view) {
    return new WealthSnapshotResponse(
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
        view.hasPartialData(),
        view.items().stream().map(WealthSnapshotItemResponse::from).toList());
  }
}
