package com.deltafinance.wealth.api;

import java.util.List;

/** Lista de ativos e passivos manuais. */
public record ManualItemListResponse(List<ManualItemResponse> items) {}
