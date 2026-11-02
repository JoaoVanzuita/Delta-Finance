package com.deltafinance.wealth.api;

import com.deltafinance.wealth.application.ManualItemView;
import java.util.List;

/** Lista de ativos e passivos manuais. */
public record ManualItemListResponse(List<ManualItemView> items) {}
