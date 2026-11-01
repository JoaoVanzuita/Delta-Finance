package com.deltafinance.wealth.api;

import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.wealth.application.WealthSnapshotService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura do histórico de investimentos derivado dos snapshots patrimoniais.
 *
 * <p>Fica no módulo de patrimônio para não criar dependência de investments para wealth; o caminho
 * público continua sob {@code /api/v1/investments}.
 */
@RestController
@RequestMapping("/api/v1/investments")
public class InvestmentHistoryController {

  private final WealthSnapshotService snapshotService;
  private final AuthenticatedUser authenticatedUser;

  public InvestmentHistoryController(
      WealthSnapshotService snapshotService, AuthenticatedUser authenticatedUser) {
    this.snapshotService = snapshotService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/history")
  public List<InvestmentHistoryPointResponse> history(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return snapshotService.investmentHistory(authenticatedUser.requireId(), from, to).stream()
        .map(InvestmentHistoryPointResponse::from)
        .toList();
  }
}
