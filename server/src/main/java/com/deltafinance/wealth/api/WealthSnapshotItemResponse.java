package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.WealthSnapshotItemView;
import java.time.LocalDate;
import java.util.UUID;

public record WealthSnapshotItemResponse(
    String sourceType,
    UUID sourceId,
    String description,
    String value,
    String currency,
    String assetClass,
    boolean included,
    String exclusionReason,
    LocalDate referenceDate,
    boolean stale) {

  public static WealthSnapshotItemResponse from(WealthSnapshotItemView view) {
    return new WealthSnapshotItemResponse(
        view.sourceType(),
        view.sourceId(),
        view.description(),
        view.value(),
        view.currency(),
        view.assetClass(),
        view.included(),
        view.exclusionReason(),
        view.referenceDate(),
        view.stale());
  }
}
