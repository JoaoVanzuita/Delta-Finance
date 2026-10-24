package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.ManualItemView;
import java.time.LocalDate;
import java.util.UUID;

/** Ativo ou passivo manual. */
public record ManualItemResponse(
    UUID id,
    String kind,
    String type,
    String description,
    String referenceValue,
    LocalDate referenceDate,
    String notes) {

  public static ManualItemResponse from(ManualItemView view) {
    return new ManualItemResponse(
        view.id(),
        view.kind(),
        view.type(),
        view.description(),
        view.referenceValue(),
        view.referenceDate(),
        view.notes());
  }
}
