package com.deltafinance.transactions.application;

import java.util.UUID;

/** Porta para impedir correções financeiras que reescreveriam uma fatura já paga. */
public interface PaidInvoiceGuard {

  /** {@code true} quando a transação possui item espelho ativo em fatura paga. */
  boolean isInPaidInvoice(UUID userId, UUID transactionId);
}
