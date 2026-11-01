package com.deltafinance.accounts.api;

import jakarta.validation.constraints.NotNull;

/** Marca ou desmarca uma conta como fronteira de caixa do perímetro de investimento. */
public record AccountInvestmentRequest(
    @NotNull(message = "Informe se a conta é de investimento.") Boolean investmentAccount) {}
