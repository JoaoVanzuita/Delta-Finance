package com.deltafinance.accounts.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CardProfileRequest(
    @NotNull(message = "Informe o dia de fechamento.") @Min(value = 1, message = "Dia inválido.") @Max(value = 31, message = "Dia inválido.") Integer closingDay,
    @NotNull(message = "Informe o dia de vencimento.") @Min(value = 1, message = "Dia inválido.") @Max(value = 31, message = "Dia inválido.") Integer dueDay,
    String creditLimit) {}
