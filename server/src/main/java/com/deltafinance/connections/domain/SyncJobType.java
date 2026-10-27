package com.deltafinance.connections.domain;

/** Tipo de trabalho durável da fila de sincronização. */
public enum SyncJobType {
  SYNC_INTEGRATION,
  SYNC_CONNECTION,
  RECALCULATE_INVOICE,
  RECONCILE
}
