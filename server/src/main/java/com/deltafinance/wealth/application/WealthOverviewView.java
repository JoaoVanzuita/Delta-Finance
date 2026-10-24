package com.deltafinance.wealth.application;

import java.time.LocalDate;
import java.util.List;

/** Visão consolidada de patrimônio com data de referência e composição. */
public record WealthOverviewView(
    LocalDate referenceDate,
    String grossAssets,
    String liabilities,
    String netWorth,
    String completeness,
    boolean hasData,
    List<WealthItemView> items) {}
