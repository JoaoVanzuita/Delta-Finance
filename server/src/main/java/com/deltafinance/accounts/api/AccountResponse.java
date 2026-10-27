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
    String referenceBalance,
    Instant balanceReferenceAt,
    String status,
    Instant lastSyncAt,
    String notes,
    CardProfileResponse cardProfile,
    String connectionStatus) {

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
        view.referenceBalance(),
        view.balanceReferenceAt(),
        view.status(),
        view.lastSyncAt(),
        view.notes(),
        view.cardProfile() == null ? null : CardProfileResponse.from(view.cardProfile()),
        view.connectionStatus());
  }
}
