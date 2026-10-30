package com.deltafinance.connections.application;

import java.time.Instant;
import java.util.UUID;

/** Visão pública de conexão; itemId sempre mascarado. */
public record ConnectionView(
    UUID id,
    String itemIdMasked,
    String institutionName,
    String displayName,
    boolean nameCustomized,
    String status,
    Instant consentExpiresAt,
    Instant lastSyncAt,
    String lastErrorCode,
    long accountCount) {}
