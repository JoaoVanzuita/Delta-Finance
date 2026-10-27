package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentQueryService;
import com.deltafinance.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leitura de investimentos importados, cobertura e proventos do provedor. */
@RestController
@RequestMapping("/api/v1/investments")
public class InvestmentDashboardController {

  private final InvestmentQueryService queryService;
  private final AuthenticatedUser authenticatedUser;

  public InvestmentDashboardController(
      InvestmentQueryService queryService, AuthenticatedUser authenticatedUser) {
    this.queryService = queryService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/overview")
  public InvestmentOverviewResponse overview() {
    return InvestmentOverviewResponse.from(queryService.overview(authenticatedUser.requireId()));
  }

  @GetMapping("/holdings")
  public InvestmentHoldingListResponse holdings(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String assetClass) {
    return new InvestmentHoldingListResponse(
        queryService.holdings(authenticatedUser.requireId(), status, assetClass).stream()
            .map(InvestmentHoldingResponse::from)
            .toList());
  }

  @GetMapping("/holdings/{id}")
  public InvestmentHoldingResponse holding(@PathVariable UUID id) {
    return InvestmentHoldingResponse.from(queryService.holding(authenticatedUser.requireId(), id));
  }

  @GetMapping("/sources")
  public InvestmentSourceListResponse sources() {
    return new InvestmentSourceListResponse(
        queryService.sources(authenticatedUser.requireId()).stream()
            .map(InvestmentSourceResponse::from)
            .toList());
  }

  @GetMapping("/income")
  public InvestmentIncomeResponse income(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return InvestmentIncomeResponse.from(
        queryService.income(authenticatedUser.requireId(), from, to));
  }
}
