package com.deltafinance.transactions.api;

import com.deltafinance.transactions.application.TransactionView;
import java.util.List;

public record TransactionPageResponse(
    List<TransactionView> items, String nextCursor, boolean hasMore) {}
