package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentSourceView;
import java.util.List;

public record InvestmentSourceListResponse(List<InvestmentSourceView> items) {}
