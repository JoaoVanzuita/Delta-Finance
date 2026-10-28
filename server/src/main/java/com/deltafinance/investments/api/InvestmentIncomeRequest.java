package com.deltafinance.investments.api;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Provento manual vinculado a uma posição importada ou manual. */
public record InvestmentIncomeRequest(
    UUID providerInvestmentId,
    UUID investmentPositionId,
    String incomeType,
    LocalDate incomeDate,
    String amount,
    @Size(max = 500) String description) {}
