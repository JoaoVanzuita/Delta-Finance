package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.CardProfileService;
import com.deltafinance.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
public class CardProfileController {

  private final CardProfileService cardProfileService;
  private final AuthenticatedUser authenticatedUser;

  public CardProfileController(
      CardProfileService cardProfileService, AuthenticatedUser authenticatedUser) {
    this.cardProfileService = cardProfileService;
    this.authenticatedUser = authenticatedUser;
  }

  @PutMapping("/{accountId}/profile")
  public CardProfileResponse update(
      @PathVariable UUID accountId, @Valid @RequestBody CardProfileRequest request) {
    return CardProfileResponse.from(
        cardProfileService.update(authenticatedUser.requireId(), accountId, request));
  }
}
