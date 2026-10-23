package com.deltafinance.integrations.infrastructure;

/** Falha sanitizada do provedor; nunca inclui corpo bruto ou credenciais. */
public class PluggyException extends RuntimeException {

  public PluggyException(String message) {
    super(message);
  }

  public PluggyException(String message, Throwable cause) {
    super(message, cause);
  }
}
