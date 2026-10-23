package com.deltafinance.integrations.domain;

/** Estados da integração pessoal (FR-035). */
public enum IntegrationStatus {
  INCOMPLETE,
  ACTIVE,
  SYNCING,
  SYNCED,
  CREDENTIAL_FAILURE,
  ACTION_REQUIRED,
  AUTHORIZATION_EXPIRED,
  ITEM_UNAVAILABLE
}
