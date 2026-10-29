package com.deltafinance.investments.api;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Provento manual vinculado a uma posição importada. */
public record InvestmentIncomeRequest(
    UUID providerInvestmentId,
    String incomeType,
    LocalDate incomeDate,
    String amount,
    @Size(max = 500) String description) {}
