package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.AccountView;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String type,
    String subtype,
    String origin,
    String institutionName,
    String alias,
    String currency,
    String currentBalance,
    Instant balanceReferenceAt,
    String status,
    Instant lastSyncAt,
    CardProfileResponse cardProfile) {

  public static AccountResponse from(AccountView view) {
    return new AccountResponse(
        view.id(),
        view.type(),
        view.subtype(),
        view.origin(),
        view.institutionName(),
        view.alias(),
        view.currency(),
        view.currentBalance(),
        view.balanceReferenceAt(),
        view.status(),
        view.lastSyncAt(),
        view.cardProfile() == null ? null : CardProfileResponse.from(view.cardProfile()));
  }
}
