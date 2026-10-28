package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentPositionView;
import java.time.LocalDate;
import java.util.UUID;

/** Posição manual com valores decifrados e total derivado. */
public record InvestmentPositionResponse(
    UUID id,
    String broker,
    String assetName,
    String ticker,
    String quantity,
    String referencePrice,
    String total,
    LocalDate referenceDate,
    String notes,
    String origin,
    boolean excludeFromWealth) {

  public static InvestmentPositionResponse from(InvestmentPositionView view) {
    return new InvestmentPositionResponse(
        view.id(),
        view.broker(),
        view.assetName(),
        view.ticker(),
        view.quantity(),
        view.referencePrice(),
        view.total(),
        view.referenceDate(),
        view.notes(),
        view.origin(),
        view.excludeFromWealth());
  }
}
