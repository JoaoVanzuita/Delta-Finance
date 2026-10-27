package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.CardOverviewService;
import com.deltafinance.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Visão geral dos cartões de crédito, separada da lista de contas correntes. */
@RestController
@RequestMapping("/api/v1/cards")
public class CardOverviewController {

  private final CardOverviewService cardOverviewService;
  private final AuthenticatedUser authenticatedUser;

  public CardOverviewController(
      CardOverviewService cardOverviewService, AuthenticatedUser authenticatedUser) {
    this.cardOverviewService = cardOverviewService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/overview")
  public CardOverviewResponse overview() {
    return CardOverviewResponse.from(cardOverviewService.overview(authenticatedUser.requireId()));
  }
}
