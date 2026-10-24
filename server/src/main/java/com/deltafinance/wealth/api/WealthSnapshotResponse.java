package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.WealthSnapshotView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Snapshot patrimonial com composição congelada. */
public record WealthSnapshotResponse(
    UUID id,
    LocalDate referenceDate,
    String grossAssets,
    String liabilities,
    String netWorth,
    String completeness,
    String notes,
    Instant createdAt,
    List<WealthItemResponse> items) {

  public static WealthSnapshotResponse from(WealthSnapshotView view) {
    return new WealthSnapshotResponse(
        view.id(),
        view.referenceDate(),
        view.grossAssets(),
        view.liabilities(),
        view.netWorth(),
        view.completeness(),
        view.notes(),
        view.createdAt(),
        view.items().stream().map(WealthItemResponse::from).toList());
  }
}
