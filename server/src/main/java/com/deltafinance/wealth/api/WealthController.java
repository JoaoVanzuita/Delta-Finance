package com.deltafinance.wealth.api;

import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.wealth.application.ManualItemView;
import com.deltafinance.wealth.application.WealthOverviewView;
import com.deltafinance.wealth.application.WealthService;
import com.deltafinance.wealth.application.WealthSnapshotService;
import com.deltafinance.wealth.application.WealthSnapshotSummaryView;
import com.deltafinance.wealth.application.WealthSnapshotView;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Patrimônio consolidado e ativos/passivos manuais. */
@RestController
@RequestMapping("/api/v1/wealth")
public class WealthController {

  private final WealthService wealthService;
  private final WealthSnapshotService snapshotService;
  private final AuthenticatedUser authenticatedUser;

  public WealthController(
      WealthService wealthService,
      WealthSnapshotService snapshotService,
      AuthenticatedUser authenticatedUser) {
    this.wealthService = wealthService;
    this.snapshotService = snapshotService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/overview")
  public WealthOverviewView overview() {
    return wealthService.overview(authenticatedUser.requireId());
  }

  @GetMapping("/history")
  public List<WealthSnapshotSummaryView> history(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return snapshotService.history(authenticatedUser.requireId(), from, to).stream().toList();
  }

  @GetMapping("/history/{id}")
  public WealthSnapshotView historyDetail(@PathVariable UUID id) {
    return snapshotService.detail(authenticatedUser.requireId(), id);
  }

  @GetMapping("/reserved-balances/{id}/history")
  public ReservedBalanceHistoryResponse reservedBalanceHistory(@PathVariable UUID id) {
    return ReservedBalanceHistoryResponse.from(
        snapshotService.reservedBalanceHistory(authenticatedUser.requireId(), id));
  }

  @PostMapping("/history")
  @ResponseStatus(HttpStatus.CREATED)
  public WealthSnapshotView captureHistory(
      @RequestBody(required = false) WealthSnapshotCaptureRequest request) {
    return snapshotService.captureManual(
        authenticatedUser.requireId(), request == null ? null : request.referenceDate());
  }

  @GetMapping("/manual-items")
  public ManualItemListResponse listManualItems() {
    return new ManualItemListResponse(
        wealthService.listManual(authenticatedUser.requireId()).stream().toList());
  }

  @GetMapping("/manual-items/{id}")
  public ManualItemView getManualItem(@PathVariable UUID id) {
    return wealthService.getManual(authenticatedUser.requireId(), id);
  }

  @PostMapping("/manual-items")
  @ResponseStatus(HttpStatus.CREATED)
  public ManualItemView createManualItem(@Valid @RequestBody ManualItemRequest request) {
    return wealthService.createManual(authenticatedUser.requireId(), request);
  }

  @PatchMapping("/manual-items/{id}")
  public ManualItemView updateManualItem(
      @PathVariable UUID id, @Valid @RequestBody ManualItemRequest request) {
    return wealthService.updateManual(authenticatedUser.requireId(), id, request);
  }

  @DeleteMapping("/manual-items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteManualItem(@PathVariable UUID id) {
    wealthService.deleteManual(authenticatedUser.requireId(), id);
  }
}
