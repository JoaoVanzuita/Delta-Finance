package com.deltafinance.integrations.application;

import java.time.Instant;
import java.util.UUID;

/** Visão pública da integração; nunca contém Client Secret ou API Key. */
public record IntegrationView(
    UUID id,
    String provider,
    String clientIdMasked,
    String status,
    Instant lastSyncAttemptAt,
    Instant lastSuccessfulSyncAt,
    String lastErrorCode,
    long connectionCount) {}
