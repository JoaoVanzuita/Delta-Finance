package com.deltafinance.reporting.api;

import com.deltafinance.reporting.application.ReportEntryView;
import com.deltafinance.shared.pagination.CursorPage;
import java.util.List;

public record ReportEntryPageResponse(
    List<ReportEntryResponse> items, String nextCursor, boolean hasMore) {

  public static ReportEntryPageResponse from(CursorPage<ReportEntryView> page) {
    return new ReportEntryPageResponse(
        page.items().stream().map(ReportEntryResponse::from).toList(),
        page.nextCursor(),
        page.hasMore());
  }
}
