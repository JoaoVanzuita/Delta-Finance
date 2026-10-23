package com.deltafinance.shared.audit;

/** Tipos de evento auditáveis. Nunca inclua valores financeiros ou segredos em metadata. */
public enum AuditEventType {
  USER_REGISTERED,
  LOGIN_SUCCESS,
  LOGIN_FAILURE,
  LOGIN_BLOCKED,
  LOGOUT,
  PASSWORD_CHANGED,
  INTEGRATION_UPDATED,
  INTEGRATION_VALIDATION_FAILED,
  SYNC_FAILED,
  EXPORT_REQUESTED,
  DISCONNECTED,
  ACCOUNT_DELETION_REQUESTED,
  ACCOUNT_DELETED
}
