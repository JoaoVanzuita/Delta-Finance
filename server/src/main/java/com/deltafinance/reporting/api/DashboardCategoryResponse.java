package com.deltafinance.reporting.api;

import com.deltafinance.reporting.application.DashboardCategoryView;
import java.util.UUID;

public record DashboardCategoryResponse(
    UUID categoryId, String categoryName, String amount, int transactionCount) {

  public static DashboardCategoryResponse from(DashboardCategoryView view) {
    return new DashboardCategoryResponse(
        view.categoryId(), view.categoryName(), view.amount(), view.transactionCount());
  }
}
