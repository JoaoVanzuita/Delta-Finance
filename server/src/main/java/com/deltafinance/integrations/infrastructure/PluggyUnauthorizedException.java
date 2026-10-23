package com.deltafinance.integrations.infrastructure;

/** API Key recusada/expirada em uma chamada de dados; o cache deve ser invalidado. */
public class PluggyUnauthorizedException extends PluggyException {

  public PluggyUnauthorizedException() {
    super("Credencial temporária recusada pelo provedor.");
  }
}
