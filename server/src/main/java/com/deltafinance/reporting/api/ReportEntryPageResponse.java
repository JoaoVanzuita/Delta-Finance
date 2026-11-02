package com.deltafinance.reporting.api;

import com.deltafinance.reporting.application.ReportEntryView;
import com.deltafinance.shared.pagination.CursorPage;
import java.util.List;

public record ReportEntryPageResponse(
    List<ReportEntryView> items, String nextCursor, boolean hasMore) {

  public static ReportEntryPageResponse from(CursorPage<ReportEntryView> page) {
    return new ReportEntryPageResponse(
        page.items().stream().toList(), page.nextCursor(), page.hasMore());
  }
}
