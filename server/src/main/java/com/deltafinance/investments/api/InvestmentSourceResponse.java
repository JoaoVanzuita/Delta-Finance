package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentSourceView;
import java.time.Instant;
import java.util.UUID;

public record InvestmentSourceResponse(
    UUID connectionId,
    String scope,
    int resourceCount,
    int availableResourceCount,
    int pendingResourceCount,
    int unavailableResourceCount,
    Instant resourcesCollectedAt,
    Instant lastSyncedAt,
    String errorCode) {

  public static InvestmentSourceResponse from(InvestmentSourceView view) {
    return new InvestmentSourceResponse(
        view.connectionId(),
        view.scope(),
        view.resourceCount(),
        view.availableResourceCount(),
        view.pendingResourceCount(),
        view.unavailableResourceCount(),
        view.resourcesCollectedAt(),
        view.lastSyncedAt(),
        view.errorCode());
  }
}
