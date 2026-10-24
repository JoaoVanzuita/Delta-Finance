package com.deltafinance.investments.application;

import java.time.LocalDate;
import java.util.UUID;

/** Visão pública de posição manual com valores decifrados e total derivado. */
public record InvestmentPositionView(
    UUID id,
    String broker,
    String assetName,
    String ticker,
    String quantity,
    String referencePrice,
    String total,
    LocalDate referenceDate,
    String notes,
    String origin) {}
