package com.deltafinance.transactions.application;

import com.deltafinance.transactions.domain.TransactionStatus;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta para validar na origem se uma transação removida voltou a existir.
 *
 * <p>Mantém a direção de dependência {@code connections → transactions}; transações não conhecem
 * itens, conexões nem clientes do provedor.
 */
public interface SourceValidationPort {

  /** Status atual na origem, ou vazio quando a transação não está mais disponível. */
  Optional<TransactionStatus> statusAtSource(
      UUID userId, UUID accountId, String externalTransactionId);
}
