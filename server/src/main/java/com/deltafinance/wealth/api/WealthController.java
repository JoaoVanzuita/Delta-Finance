package com.deltafinance.wealth.api;

import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.wealth.application.WealthService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Patrimônio consolidado e ativos/passivos manuais. */
@RestController
@RequestMapping("/api/v1/wealth")
public class WealthController {

  private final WealthService wealthService;
  private final AuthenticatedUser authenticatedUser;

  public WealthController(WealthService wealthService, AuthenticatedUser authenticatedUser) {
    this.wealthService = wealthService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/overview")
  public WealthOverviewResponse overview() {
    return WealthOverviewResponse.from(wealthService.overview(authenticatedUser.requireId()));
  }

  @GetMapping("/manual-items")
  public ManualItemListResponse listManualItems() {
    return new ManualItemListResponse(
        wealthService.listManual(authenticatedUser.requireId()).stream()
            .map(ManualItemResponse::from)
            .toList());
  }

  @GetMapping("/manual-items/{id}")
  public ManualItemResponse getManualItem(@PathVariable UUID id) {
    return ManualItemResponse.from(wealthService.getManual(authenticatedUser.requireId(), id));
  }

  @PostMapping("/manual-items")
  @ResponseStatus(HttpStatus.CREATED)
  public ManualItemResponse createManualItem(@Valid @RequestBody ManualItemRequest request) {
    return ManualItemResponse.from(
        wealthService.createManual(authenticatedUser.requireId(), request));
  }

  @PatchMapping("/manual-items/{id}")
  public ManualItemResponse updateManualItem(
      @PathVariable UUID id, @Valid @RequestBody ManualItemRequest request) {
    return ManualItemResponse.from(
        wealthService.updateManual(authenticatedUser.requireId(), id, request));
  }

  @DeleteMapping("/manual-items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteManualItem(@PathVariable UUID id) {
    wealthService.deleteManual(authenticatedUser.requireId(), id);
  }
}
