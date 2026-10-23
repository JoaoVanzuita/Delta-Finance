package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.CardProfileView;
import java.util.UUID;

public record CardProfileResponse(
    UUID accountId, int closingDay, int dueDay, String creditLimit, String creditBalance) {

  public static CardProfileResponse from(CardProfileView view) {
    return new CardProfileResponse(
        view.accountId(),
        view.closingDay(),
        view.dueDay(),
        view.creditLimit(),
        view.creditBalance());
  }
}
