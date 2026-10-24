package com.deltafinance.investments.api;

import java.util.List;

/** Página por cursor de posições manuais. */
public record InvestmentPositionPageResponse(
    List<InvestmentPositionResponse> items, String nextCursor, boolean hasMore) {}
