package com.deltafinance.connections.api;

import com.deltafinance.connections.application.ConnectionView;
import java.time.Instant;
import java.util.UUID;

/** Conexão exposta à interface com itemId mascarado. */
public record ConnectionResponse(
    UUID id,
    String itemIdMasked,
    String institutionName,
    String displayName,
    boolean nameCustomized,
    String status,
    Instant consentExpiresAt,
    Instant lastSyncAt,
    String lastErrorCode,
    long accountCount) {

  public static ConnectionResponse from(ConnectionView view) {
    return new ConnectionResponse(
        view.id(),
        view.itemIdMasked(),
        view.institutionName(),
        view.displayName(),
        view.nameCustomized(),
        view.status(),
        view.consentExpiresAt(),
        view.lastSyncAt(),
        view.lastErrorCode(),
        view.accountCount());
  }
}
