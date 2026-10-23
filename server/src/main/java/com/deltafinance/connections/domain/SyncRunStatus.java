package com.deltafinance.connections.domain;

/** Resultado de uma tentativa de sincronização. */
public enum SyncRunStatus {
  SUCCESS,
  PARTIAL,
  EMPTY,
  AUTH_ERROR,
  RATE_LIMITED,
  TIMEOUT,
  FAILED
}
