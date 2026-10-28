package com.deltafinance.investments.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Posição importada pronta para leitura.
 *
 * <p>Valores ausentes permanecem nulos; o cliente deve exibir "não informado". {@code assetClass}
 * usa o provedor, uma heurística explícita por código ou uma classificação informada pelo usuário.
 */
public record InvestmentHoldingView(
    UUID id,
    UUID connectionId,
    String name,
    String code,
    String isin,
    String type,
    String subtype,
    String assetClass,
    String classificationSource,
    String status,
    String currency,
    String quantity,
    String unitValue,
    String amount,
    String balance,
    String amountOriginal,
    String amountProfit,
    String amountWithdrawal,
    String taxes,
    String taxes2,
    String rate,
    String rateType,
    String fixedAnnualRate,
    String lastMonthRate,
    String annualRate,
    String lastTwelveMonthsRate,
    LocalDate referenceDate,
    LocalDate purchaseDate,
    LocalDate dueDate,
    LocalDate gracePeriodDate,
    Instant providerUpdatedAt,
    Instant syncedAt,
    Instant unavailableAt,
    String manualCostBasis,
    String costBasis,
    String costBasisSource,
    String incomeTotal,
    String estimatedProfit,
    String estimatedReturnPercentage,
    String userAssetClass,
    String profitSource,
    boolean stale) {}
