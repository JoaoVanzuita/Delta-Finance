package com.deltafinance.wealth.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Snapshot patrimonial detalhado, com a composição decifrada. */
public record WealthSnapshotView(
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
    List<WealthSnapshotItemView> items) {}
