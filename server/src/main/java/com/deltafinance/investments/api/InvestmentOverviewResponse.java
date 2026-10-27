package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentOverviewView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Resumo de investimentos importados e manuais. */
public record InvestmentOverviewResponse(
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
    List<AllocationResponse> allocation,
    List<InvestmentSourceResponse> coverage,
    List<DuplicateResponse> duplicates) {

  public record AllocationResponse(String assetClass, String value, int count, String percentage) {}

  public record DuplicateResponse(UUID manualPositionId, String code, UUID providerInvestmentId) {}

  public static InvestmentOverviewResponse from(InvestmentOverviewView view) {
    return new InvestmentOverviewResponse(
        view.referenceDate(),
        view.providerValue(),
        view.manualValue(),
        view.totalValue(),
        view.activeCount(),
        view.closedCount(),
        view.unavailableCount(),
        view.manualCount(),
        view.incomeLast12Months(),
        view.incomeSource(),
        view.profitReported(),
        view.profitCoverageCount(),
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
        view.coverage().stream().map(InvestmentSourceResponse::from).toList(),
        view.duplicates().stream()
            .map(
                duplicate ->
                    new DuplicateResponse(
                        duplicate.manualPositionId(),
                        duplicate.code(),
                        duplicate.providerInvestmentId()))
            .toList());
  }
}
