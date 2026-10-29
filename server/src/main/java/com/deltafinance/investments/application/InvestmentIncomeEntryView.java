package com.deltafinance.investments.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Provento manual com o destino resolvido para exibição. */
public record InvestmentIncomeEntryView(
    UUID id,
    UUID targetId,
    String targetName,
    String targetCode,
    String incomeType,
    LocalDate incomeDate,
    String amount,
    String description,
    String source,
    Instant createdAt) {}
