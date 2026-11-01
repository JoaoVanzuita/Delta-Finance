package com.deltafinance.wealth.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ponto mensal da série de investimentos derivada dos snapshots. */
public record InvestmentHistoryPointView(
    UUID snapshotId,
    LocalDate referenceDate,
    String totalValue,
    String completeness,
    List<InvestmentClassSliceView> allocation) {}
