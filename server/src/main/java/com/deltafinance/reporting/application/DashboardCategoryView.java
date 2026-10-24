package com.deltafinance.reporting.application;

import java.util.UUID;

/**
 * Distribuição de despesas por categoria; detalhamento do total de despesas, não um total paralelo.
 */
public record DashboardCategoryView(
    UUID categoryId, String categoryName, String amount, int transactionCount) {}
