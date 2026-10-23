package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountQueryService accountQueryService;
  private final AuthenticatedUser authenticatedUser;

  public AccountController(
      AccountQueryService accountQueryService, AuthenticatedUser authenticatedUser) {
    this.accountQueryService = accountQueryService;
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
}
