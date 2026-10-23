package com.deltafinance.connections.domain;

/** Estados da conexão/item (espelha o estado local e o do provedor). */
public enum ConnectionStatus {
  INCOMPLETE,
  ACTIVE,
  SYNCING,
  SYNCED,
  CREDENTIAL_FAILURE,
  ACTION_REQUIRED,
  AUTHORIZATION_EXPIRED,
  ITEM_UNAVAILABLE
}
