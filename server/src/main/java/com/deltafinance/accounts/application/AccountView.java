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
    String currency,
    String currentBalance,
    Instant balanceReferenceAt,
    String status,
    Instant lastSyncAt,
    String notes,
    CardProfileView cardProfile) {}
