package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.InvoiceView;
import java.util.List;

public record InvoicePageResponse(List<InvoiceView> items, String nextCursor, boolean hasMore) {}
