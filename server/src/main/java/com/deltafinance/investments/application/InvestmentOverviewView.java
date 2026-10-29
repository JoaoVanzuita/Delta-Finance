package com.deltafinance.investments.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Visão consolidada das posições importadas do provedor. */
public record InvestmentOverviewView(
    LocalDate referenceDate,
    String providerValue,
    String totalValue,
    int activeCount,
    int closedCount,
    int unavailableCount,
    String incomeLast12Months,
    String incomeSource,
    String profitReported,
    int profitCoverageCount,
    String estimatedProfit,
    int estimatedProfitCoverageCount,
    Instant lastSyncedAt,
    boolean stale,
    List<Allocation> allocation,
    List<InvestmentSourceView> coverage) {

  /** Distribuição das posições importadas ativas por classe. */
  public record Allocation(String assetClass, String value, int count, String percentage) {}
}
