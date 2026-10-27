package com.deltafinance.investments.application;

import java.time.Instant;
import java.util.UUID;

/** Cobertura de investimentos de uma conexão. */
public record InvestmentSourceView(
    UUID connectionId,
    String scope,
    int resourceCount,
    int availableResourceCount,
    int pendingResourceCount,
    int unavailableResourceCount,
    Instant resourcesCollectedAt,
    Instant lastSyncedAt,
    String errorCode) {}
