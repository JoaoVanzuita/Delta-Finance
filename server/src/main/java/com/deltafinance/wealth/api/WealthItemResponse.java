package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.WealthItemView;
import java.time.LocalDate;
import java.util.UUID;

/** Item da composição patrimonial. */
public record WealthItemResponse(
    String sourceType,
    UUID sourceId,
    String description,
    String value,
    boolean included,
    String exclusionReason,
    String origin,
    boolean duplicate,
    LocalDate referenceDate,
    boolean stale) {

  public static WealthItemResponse from(WealthItemView view) {
    return new WealthItemResponse(
        view.sourceType(),
        view.sourceId(),
        view.description(),
        view.value(),
        view.included(),
        view.exclusionReason(),
        view.origin(),
        view.duplicate(),
        view.referenceDate(),
        view.stale());
  }
}
