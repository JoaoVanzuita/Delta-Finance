package com.deltafinance.transactions.application;

import java.util.UUID;

/**
 * Porta para registrar uma transferência manual como movimento interno confirmado.
 *
 * <p>Mantém a direção {@code reconciliation → transactions}; transações não dependem do módulo de
 * reconciliação.
 */
public interface InternalMatchPort {

  void confirmManualTransfer(UUID userId, UUID debitTransactionId, UUID creditTransactionId);
}
