package com.deltafinance.transactions.api;

import java.util.List;

public record TransactionPageResponse(
    List<TransactionResponse> items, String nextCursor, boolean hasMore) {}
