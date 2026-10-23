package com.deltafinance.integrations.infrastructure;

/** Credenciais recusadas pelo provedor (401/403 no POST /auth). */
public class PluggyCredentialException extends PluggyException {

  public PluggyCredentialException() {
    super("Credenciais recusadas pelo provedor.");
  }
}
