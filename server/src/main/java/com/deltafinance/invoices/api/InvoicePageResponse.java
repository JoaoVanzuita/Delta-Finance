package com.deltafinance.invoices.api;

import java.util.List;

public record InvoicePageResponse(
    List<InvoiceResponse> items, String nextCursor, boolean hasMore) {}
