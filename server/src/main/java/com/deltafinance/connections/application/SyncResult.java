package com.deltafinance.connections.application;

import com.deltafinance.connections.domain.SyncRunStatus;
import com.deltafinance.transactions.application.ImportStats;

/** Resultado de uma tentativa de sincronização, sem dados financeiros. */
public record SyncResult(
    SyncRunStatus status,
    ImportStats stats,
    SyncStateService.SyncFailure failure,
    java.util.UUID runId,
    String correlationId) {

  public boolean succeeded() {
    return status == SyncRunStatus.SUCCESS || status == SyncRunStatus.EMPTY;
  }

  public boolean retryable() {
    return failure != null && failure.retryable();
  }
}
