package com.deltafinance.wealth.application;

/** Fatia de investimento por classe em um snapshot. */
public record InvestmentClassSliceView(
    String assetClass, String value, String percentage, int positionCount) {}
