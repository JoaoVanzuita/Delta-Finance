package com.deltafinance.wealth.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Snapshot patrimonial datado com a composição congelada. */
public record WealthSnapshotView(
    UUID id,
    LocalDate referenceDate,
    String grossAssets,
    String liabilities,
    String netWorth,
    String completeness,
    String notes,
    Instant createdAt,
    List<WealthItemView> items) {}
