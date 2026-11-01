package com.deltafinance.wealth.application;

import java.time.LocalDate;
import java.util.UUID;

/** Item decifrado de um snapshot patrimonial. */
public record WealthSnapshotItemView(
    String sourceType,
    UUID sourceId,
    String description,
    String value,
    String currency,
    String assetClass,
    boolean included,
    String exclusionReason,
    LocalDate referenceDate,
    boolean stale) {}
