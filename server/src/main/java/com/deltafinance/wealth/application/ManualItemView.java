package com.deltafinance.wealth.application;

import java.time.LocalDate;
import java.util.UUID;

/** Ativo ou passivo manual com valores decifrados. */
public record ManualItemView(
    UUID id,
    String kind,
    String type,
    String description,
    String referenceValue,
    LocalDate referenceDate,
    String notes) {}
