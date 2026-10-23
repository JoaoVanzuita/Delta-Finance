package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.ManualAccountService;
import com.deltafinance.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Criação/edição de contas manuais. */
@RestController
@RequestMapping("/api/v1/accounts")
public class ManualAccountController {

  private final ManualAccountService manualAccountService;
  private final AuthenticatedUser authenticatedUser;

  public ManualAccountController(
      ManualAccountService manualAccountService, AuthenticatedUser authenticatedUser) {
    this.manualAccountService = manualAccountService;
    this.authenticatedUser = authenticatedUser;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponse create(@Valid @RequestBody ManualAccountRequest request) {
    return AccountResponse.from(
        manualAccountService.create(authenticatedUser.requireId(), request));
  }

  @PatchMapping("/{id}")
  public AccountResponse update(
      @PathVariable UUID id, @Valid @RequestBody AccountUpdateRequest request) {
    return AccountResponse.from(
        manualAccountService.update(authenticatedUser.requireId(), id, request));
  }
}
