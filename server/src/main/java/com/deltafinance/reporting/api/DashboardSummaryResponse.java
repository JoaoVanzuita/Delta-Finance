package com.deltafinance.reporting.api;

import com.deltafinance.reporting.application.DashboardSummaryView;
import java.time.Instant;
import java.time.LocalDate;

public record DashboardSummaryResponse(
    LocalDate periodStart,
    LocalDate periodEnd,
    String periodResult,
    String currentBalance,
    Instant currentBalanceReferenceAt,
    String income,
    String expenses,
    int pendingCount,
    String pendingIncome,
    String pendingExpenses,
    long pendingInternalMatches,
    Instant lastSuccessfulSyncAt,
    String cardOutstandingBalance) {

  public static DashboardSummaryResponse from(DashboardSummaryView view) {
    return new DashboardSummaryResponse(
        view.periodStart(),
        view.periodEnd(),
        view.periodResult(),
        view.currentBalance(),
        view.currentBalanceReferenceAt(),
        view.income(),
        view.expenses(),
        view.pendingCount(),
        view.pendingIncome(),
        view.pendingExpenses(),
        view.pendingInternalMatches(),
        view.lastSuccessfulSyncAt(),
        view.cardOutstandingBalance());
  }
}
