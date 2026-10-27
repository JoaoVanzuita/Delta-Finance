package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.InvoicePayment;
import com.deltafinance.invoices.domain.InvoicePaymentRepository;
import com.deltafinance.reconciliation.application.InternalMatchConfirmedEvent;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usa o movimento interno confirmado para registrar a conta que efetivamente pagou a fatura.
 *
 * <p>O pagamento importado nasce ligado à conta do cartão. Quando o usuário confirma a
 * correspondência com a perna bancária, a conta pagadora passa a ser a outra ponta do par.
 */
@Component
public class InvoicePaymentMatchListener {

  private final InvoicePaymentRepository paymentRepository;
  private final TransactionRepository transactionRepository;

  public InvoicePaymentMatchListener(
      InvoicePaymentRepository paymentRepository, TransactionRepository transactionRepository) {
    this.paymentRepository = paymentRepository;
    this.transactionRepository = transactionRepository;
  }

  @EventListener
  @Transactional
  public void onConfirmed(InternalMatchConfirmedEvent event) {
    apply(event.userId(), event.debitTransactionId(), event.creditTransactionId());
    apply(event.userId(), event.creditTransactionId(), event.debitTransactionId());
  }

  private void apply(UUID userId, UUID paymentTransactionId, UUID payingTransactionId) {
    InvoicePayment payment =
        paymentRepository
            .findByUserIdAndSourceTransactionId(userId, paymentTransactionId)
            .orElse(null);
    if (payment == null) {
      return;
    }
    Transaction paying =
        transactionRepository.findByIdAndUserId(payingTransactionId, userId).orElse(null);
    if (paying == null || paying.getAccountId() == null) {
      return;
    }
    if (paying.getAccountId().equals(payment.getPaymentAccountId())) {
      return;
    }
    payment.updatePaymentAccountId(paying.getAccountId());
    paymentRepository.save(payment);
  }
}
