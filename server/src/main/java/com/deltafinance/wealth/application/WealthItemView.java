package com.deltafinance.wealth.application;

import java.time.LocalDate;
import java.util.UUID;

/** Item da composição patrimonial, já decifrado. */
public record WealthItemView(
    String sourceType,
    UUID sourceId,
    String description,
    String value,
    boolean included,
    String exclusionReason,
    String origin,
    boolean duplicate,
    LocalDate referenceDate,
    boolean stale) {}
