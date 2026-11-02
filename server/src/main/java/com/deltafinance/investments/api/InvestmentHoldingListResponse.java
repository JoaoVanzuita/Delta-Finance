package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentHoldingView;
import java.util.List;

public record InvestmentHoldingListResponse(List<InvestmentHoldingView> items) {}
