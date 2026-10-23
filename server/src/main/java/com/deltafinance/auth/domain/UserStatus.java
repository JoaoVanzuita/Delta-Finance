package com.deltafinance.auth.domain;

/** Estado da conta. Apenas {@link #ACTIVE} pode autenticar. */
public enum UserStatus {
  ACTIVE,
  DELETION_PENDING,
  DELETED
}
