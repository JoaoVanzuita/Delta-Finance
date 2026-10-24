package com.deltafinance.reporting.api;

import com.deltafinance.reporting.application.DashboardBucketView;
import java.time.LocalDate;

public record DashboardBucketResponse(
    String label, LocalDate start, LocalDate end, String income, String expenses, String result) {

  public static DashboardBucketResponse from(DashboardBucketView view) {
    return new DashboardBucketResponse(
        view.label(), view.start(), view.end(), view.income(), view.expenses(), view.result());
  }
}
