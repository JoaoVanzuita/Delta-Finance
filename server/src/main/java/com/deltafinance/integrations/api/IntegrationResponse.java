package com.deltafinance.integrations.api;

import com.deltafinance.integrations.application.IntegrationView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Resposta da integração; Client Secret e API Key nunca aparecem. */
public record IntegrationResponse(
    UUID id,
    String provider,
    String clientIdMasked,
    String status,
    Instant lastSyncAttemptAt,
    Instant lastSuccessfulSyncAt,
    String lastErrorCode,
    long connectionCount,
    int connectionLimit,
    LocalDate connectionLimitCheckedAt) {

  public static IntegrationResponse from(IntegrationView view) {
    return new IntegrationResponse(
        view.id(),
        view.provider(),
        view.clientIdMasked(),
        view.status(),
        view.lastSyncAttemptAt(),
        view.lastSuccessfulSyncAt(),
        view.lastErrorCode(),
        view.connectionCount(),
        view.connectionLimit(),
        view.connectionLimitCheckedAt());
  }
}
