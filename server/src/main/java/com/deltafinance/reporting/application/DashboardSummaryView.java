package com.deltafinance.reporting.application;

import java.time.Instant;
import java.time.LocalDate;

/** Resumo do dashboard, com resultado do período separado do saldo atual consolidado. */
public record DashboardSummaryView(
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
    String cardOutstandingBalance) {}
