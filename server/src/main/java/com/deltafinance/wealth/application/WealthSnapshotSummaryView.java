package com.deltafinance.wealth.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Resumo de um snapshot patrimonial para a série histórica. */
public record WealthSnapshotSummaryView(
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
    boolean hasPartialData) {}
