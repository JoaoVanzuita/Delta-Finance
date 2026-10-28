package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentCommandService;
import com.deltafinance.investments.application.InvestmentIncomeService;
import com.deltafinance.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Escritas do módulo de investimentos: proventos manuais, custo e classificação. */
@RestController
@RequestMapping("/api/v1/investments")
public class InvestmentCommandController {

  private final InvestmentIncomeService incomeService;
  private final InvestmentCommandService commandService;
  private final AuthenticatedUser authenticatedUser;

  public InvestmentCommandController(
      InvestmentIncomeService incomeService,
      InvestmentCommandService commandService,
      AuthenticatedUser authenticatedUser) {
    this.incomeService = incomeService;
    this.commandService = commandService;
    this.authenticatedUser = authenticatedUser;
  }

  @PostMapping("/income")
  @ResponseStatus(HttpStatus.CREATED)
  public InvestmentIncomeEntryResponse createIncome(
      @Valid @RequestBody InvestmentIncomeRequest request) {
    return InvestmentIncomeEntryResponse.from(
        incomeService.create(authenticatedUser.requireId(), request));
  }

  @PatchMapping("/income/{id}")
  public InvestmentIncomeEntryResponse updateIncome(
      @PathVariable UUID id, @Valid @RequestBody InvestmentIncomeRequest request) {
    return InvestmentIncomeEntryResponse.from(
        incomeService.update(authenticatedUser.requireId(), id, request));
  }

  @DeleteMapping("/income/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteIncome(@PathVariable UUID id) {
    incomeService.delete(authenticatedUser.requireId(), id);
  }

  @PatchMapping("/holdings/{id}/cost-basis")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateCostBasis(
      @PathVariable UUID id, @RequestBody InvestmentCostBasisRequest request) {
    commandService.updateCostBasis(authenticatedUser.requireId(), id, request.costBasis());
  }

  @PatchMapping("/holdings/{id}/asset-class")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateAssetClass(
      @PathVariable UUID id, @RequestBody InvestmentAssetClassRequest request) {
    commandService.updateAssetClass(authenticatedUser.requireId(), id, request.assetClass());
  }
}
