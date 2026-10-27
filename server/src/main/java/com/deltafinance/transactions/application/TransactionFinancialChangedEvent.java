package com.deltafinance.transactions.application;

import java.util.UUID;

/**
 * Sinaliza que os valores efetivos de uma transação mudaram.
 *
 * <p>Publicado após importação, criação manual, correção e revisão de remoção. Módulos dependentes
 * — como o espelho de fatura — sincronizam o estado a partir da transação persistida.
 */
public record TransactionFinancialChangedEvent(UUID userId, UUID transactionId) {}
