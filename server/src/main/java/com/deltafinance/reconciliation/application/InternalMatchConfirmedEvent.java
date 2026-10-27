package com.deltafinance.reconciliation.application;

import java.util.UUID;

/**
 * Movimento interno confirmado explicitamente.
 *
 * <p>Publicado após a confirmação para que outros módulos ajustem vínculos derivados, como a conta
 * pagadora de uma fatura de cartão. Não contém valores nem descrições.
 */
public record InternalMatchConfirmedEvent(
    UUID userId, UUID debitTransactionId, UUID creditTransactionId) {}
