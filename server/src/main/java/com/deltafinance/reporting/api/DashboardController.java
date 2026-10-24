package com.deltafinance.reporting.api;

import com.deltafinance.reporting.application.DashboardBucketView;
import com.deltafinance.reporting.application.DashboardCategoryView;
import com.deltafinance.reporting.application.DrillDownService;
import com.deltafinance.reporting.application.ReportEntryView;
import com.deltafinance.reporting.application.ReportQuery;
import com.deltafinance.reporting.application.ReportingService;
import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.shared.pagination.CursorPage;
import com.deltafinance.shared.pagination.PageRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard, distribuição por categoria, série temporal e drill-down. */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

  private final ReportingService reportingService;
  private final DrillDownService drillDownService;
  private final AuthenticatedUser authenticatedUser;

  public DashboardController(
      ReportingService reportingService,
      DrillDownService drillDownService,
      AuthenticatedUser authenticatedUser) {
    this.reportingService = reportingService;
    this.drillDownService = drillDownService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/dashboard/summary")
  public DashboardSummaryResponse summary(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String origin,
      @RequestParam(required = false) String minAmount,
      @RequestParam(required = false) String maxAmount) {
    return DashboardSummaryResponse.from(
        reportingService.summary(
            authenticatedUser.requireId(),
            query(period, from, to, accountId, categoryId, type, origin, minAmount, maxAmount)));
  }

  @GetMapping("/dashboard/categories")
  public List<DashboardCategoryResponse> categories(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String origin,
      @RequestParam(required = false) String minAmount,
      @RequestParam(required = false) String maxAmount) {
    List<DashboardCategoryView> views =
        reportingService.categories(
            authenticatedUser.requireId(),
            query(period, from, to, accountId, categoryId, type, origin, minAmount, maxAmount));
    return views.stream().map(DashboardCategoryResponse::from).toList();
  }

  @GetMapping("/dashboard/timeseries")
  public List<DashboardBucketResponse> timeseries(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String origin,
      @RequestParam(required = false) String minAmount,
      @RequestParam(required = false) String maxAmount) {
    List<DashboardBucketView> views =
        reportingService.timeseries(
            authenticatedUser.requireId(),
            query(period, from, to, accountId, categoryId, type, origin, minAmount, maxAmount));
    return views.stream().map(DashboardBucketResponse::from).toList();
  }

  @GetMapping("/reports/entries")
  public ReportEntryPageResponse entries(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String origin,
      @RequestParam(required = false) String minAmount,
      @RequestParam(required = false) String maxAmount,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int limit) {
    CursorPage<ReportEntryView> page =
        drillDownService.entries(
            authenticatedUser.requireId(),
            query(period, from, to, accountId, categoryId, type, origin, minAmount, maxAmount),
            PageRequest.of(limit, cursor));
    return ReportEntryPageResponse.from(page);
  }

  private static ReportQuery query(
      String period,
      LocalDate from,
      LocalDate to,
      UUID accountId,
      UUID categoryId,
      String type,
      String origin,
      String minAmount,
      String maxAmount) {
    return new ReportQuery(
        from, to, period, accountId, categoryId, type, origin, minAmount, maxAmount);
  }
}
