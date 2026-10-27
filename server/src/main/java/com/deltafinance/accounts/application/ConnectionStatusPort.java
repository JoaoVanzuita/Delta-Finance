package com.deltafinance.accounts.application;

import java.util.UUID;

/**
 * Porta para expor o estado da conexão de origem sem criar dependência do módulo de contas para
 * connections.
 */
public interface ConnectionStatusPort {

  /** Estado da conexão ou {@code null} quando ela não existe. */
  String statusOf(UUID userId, UUID connectionId);
}
