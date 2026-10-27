package com.deltafinance.investments.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Visão consolidada dos investimentos importados e manuais. */
public record InvestmentOverviewView(
    LocalDate referenceDate,
    String providerValue,
    String manualValue,
    String totalValue,
    int activeCount,
    int closedCount,
    int unavailableCount,
    int manualCount,
    String incomeLast12Months,
    String incomeSource,
    String profitReported,
    int profitCoverageCount,
    Instant lastSyncedAt,
    boolean stale,
    List<Allocation> allocation,
    List<InvestmentSourceView> coverage,
    List<Duplicate> duplicates) {

  /** Distribuição das posições importadas ativas por classe. */
  public record Allocation(String assetClass, String value, int count, String percentage) {}

  /** Possível duplicidade entre posição manual e importada. */
  public record Duplicate(UUID manualPositionId, String code, UUID providerInvestmentId) {}
}
