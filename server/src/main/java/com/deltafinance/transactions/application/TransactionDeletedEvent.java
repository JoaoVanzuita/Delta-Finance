package com.deltafinance.transactions.application;

import java.util.UUID;

/**
 * Publicado antes de uma transação manual ser apagada.
 *
 * <p>Permite que módulos dependentes removam vínculos que impediriam a exclusão, como o item
 * espelho da fatura. A transação ainda existe quando o evento é tratado.
 */
public record TransactionDeletedEvent(UUID userId, UUID transactionId, UUID accountId) {}
