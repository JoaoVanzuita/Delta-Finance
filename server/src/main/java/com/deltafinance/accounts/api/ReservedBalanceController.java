package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.ReservedBalanceDisplayNameService;
import com.deltafinance.accounts.application.ReservedBalanceQueryService;
import com.deltafinance.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Leitura e nome local dos saldos reservados de uma conta. */
@RestController
@RequestMapping("/api/v1")
public class ReservedBalanceController {

  private final ReservedBalanceQueryService queryService;
  private final ReservedBalanceDisplayNameService displayNameService;
  private final AuthenticatedUser authenticatedUser;

  public ReservedBalanceController(
      ReservedBalanceQueryService queryService,
      ReservedBalanceDisplayNameService displayNameService,
      AuthenticatedUser authenticatedUser) {
    this.queryService = queryService;
    this.displayNameService = displayNameService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/accounts/{accountId}/reserved-balances")
  public ReservedBalanceListResponse list(@PathVariable UUID accountId) {
    return new ReservedBalanceListResponse(
        queryService.list(authenticatedUser.requireId(), accountId).stream()
            .map(ReservedBalanceResponse::from)
            .toList());
  }

  @PutMapping("/reserved-balances/{id}/display-name")
  public ReservedBalanceResponse rename(
      @PathVariable UUID id, @Valid @RequestBody ReservedBalanceDisplayNameRequest request) {
    return ReservedBalanceResponse.from(
        displayNameService.rename(authenticatedUser.requireId(), id, request.displayName()));
  }

  @DeleteMapping("/reserved-balances/{id}/display-name")
  public ReservedBalanceResponse reset(@PathVariable UUID id) {
    return ReservedBalanceResponse.from(
        displayNameService.reset(authenticatedUser.requireId(), id));
  }
}
