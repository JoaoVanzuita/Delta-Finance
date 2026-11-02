package com.deltafinance.wealth.application;

import java.time.LocalDate;

/** Ponto mensal do saldo reservado em um snapshot patrimonial. */
public record ReservedBalanceHistoryPointView(
    LocalDate referenceDate, String value, String currency, boolean included) {}
