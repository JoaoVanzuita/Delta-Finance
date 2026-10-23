package com.deltafinance.connections.domain;

/** Estado de um job da fila. */
public enum SyncJobStatus {
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED,
  RETRY
}
