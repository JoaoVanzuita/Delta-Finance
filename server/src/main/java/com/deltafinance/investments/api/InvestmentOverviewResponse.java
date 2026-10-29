package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentOverviewView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Resumo de investimentos importados. */
public record InvestmentOverviewResponse(
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
    List<AllocationResponse> allocation,
    List<InvestmentSourceResponse> coverage) {

  public record AllocationResponse(String assetClass, String value, int count, String percentage) {}

  public static InvestmentOverviewResponse from(InvestmentOverviewView view) {
    return new InvestmentOverviewResponse(
        view.referenceDate(),
        view.providerValue(),
        view.totalValue(),
        view.activeCount(),
        view.closedCount(),
        view.unavailableCount(),
        view.incomeLast12Months(),
        view.incomeSource(),
        view.profitReported(),
        view.profitCoverageCount(),
        view.estimatedProfit(),
        view.estimatedProfitCoverageCount(),
        view.lastSyncedAt(),
        view.stale(),
        view.allocation().stream()
            .map(
                allocation ->
                    new AllocationResponse(
                        allocation.assetClass(),
                        allocation.value(),
                        allocation.count(),
                        allocation.percentage()))
            .toList(),
        view.coverage().stream().map(InvestmentSourceResponse::from).toList());
  }
}
