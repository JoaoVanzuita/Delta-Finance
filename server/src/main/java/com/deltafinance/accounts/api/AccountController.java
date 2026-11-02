package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.AccountDisplayNameService;
import com.deltafinance.accounts.application.AccountInvestmentService;
import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.ReservedBalanceTreatmentService;
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

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountQueryService accountQueryService;
  private final AccountDisplayNameService accountDisplayNameService;
  private final AccountInvestmentService accountInvestmentService;
  private final ReservedBalanceTreatmentService reservedBalanceTreatmentService;
  private final AuthenticatedUser authenticatedUser;

  public AccountController(
      AccountQueryService accountQueryService,
      AccountDisplayNameService accountDisplayNameService,
      AccountInvestmentService accountInvestmentService,
      ReservedBalanceTreatmentService reservedBalanceTreatmentService,
      AuthenticatedUser authenticatedUser) {
    this.accountQueryService = accountQueryService;
    this.accountDisplayNameService = accountDisplayNameService;
    this.accountInvestmentService = accountInvestmentService;
    this.reservedBalanceTreatmentService = reservedBalanceTreatmentService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  public AccountListResponse list() {
    return new AccountListResponse(
        accountQueryService.list(authenticatedUser.requireId()).stream()
            .map(AccountResponse::from)
            .toList());
  }

  @GetMapping("/{id}")
  public AccountResponse get(@PathVariable UUID id) {
    return AccountResponse.from(accountQueryService.get(authenticatedUser.requireId(), id));
  }

  @PutMapping("/{id}/display-name")
  public AccountResponse rename(
      @PathVariable UUID id, @Valid @RequestBody AccountDisplayNameRequest request) {
    return AccountResponse.from(
        accountDisplayNameService.rename(authenticatedUser.requireId(), id, request.displayName()));
  }

  @DeleteMapping("/{id}/display-name")
  public AccountResponse resetName(@PathVariable UUID id) {
    return AccountResponse.from(accountDisplayNameService.reset(authenticatedUser.requireId(), id));
  }

  @PutMapping("/{id}/investment")
  public AccountResponse updateInvestment(
      @PathVariable UUID id, @Valid @RequestBody AccountInvestmentRequest request) {
    return AccountResponse.from(
        accountInvestmentService.update(
            authenticatedUser.requireId(), id, request.investmentAccount()));
  }

  @PutMapping("/{id}/reserved-balance-treatment")
  public AccountResponse updateReservedBalanceTreatment(
      @PathVariable UUID id, @Valid @RequestBody ReservedBalanceTreatmentRequest request) {
    return AccountResponse.from(
        reservedBalanceTreatmentService.update(
            authenticatedUser.requireId(), id, request.treatment()));
  }
}
