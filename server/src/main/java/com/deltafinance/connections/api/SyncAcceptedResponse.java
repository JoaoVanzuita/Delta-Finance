package com.deltafinance.connections.api;

import java.util.UUID;

/** Resposta de enfileiramento de sincronização (202). */
public record SyncAcceptedResponse(UUID jobId, String status) {

  public static SyncAcceptedResponse queued(UUID jobId) {
    return new SyncAcceptedResponse(jobId, "QUEUED");
  }
}
