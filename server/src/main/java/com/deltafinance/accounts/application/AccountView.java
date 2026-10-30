package com.deltafinance.accounts.application;

import java.time.Instant;
import java.util.UUID;

/** Visão pública de conta/cartão com valores decifrados. */
public record AccountView(
    UUID id,
    String type,
    String subtype,
    String origin,
    String institutionName,
    String alias,
    String providerName,
    boolean nameCustomized,
    String currency,
    String currentBalance,
    String referenceBalance,
    Instant balanceReferenceAt,
    String status,
    Instant lastSyncAt,
    String notes,
    CardProfileView cardProfile,
    String connectionStatus) {}
