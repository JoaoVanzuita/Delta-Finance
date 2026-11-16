package com.deltafinance.transactions.application;

import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Efetivação de lançamentos pendentes.
 *
 * <p>Um lançamento {@code PENDING} não compõe saldo nem relatórios: é a previsão de um fato que
 * ainda não ocorreu (salário previsto, boleto a vencer). A efetivação move para {@code POSTED} uma
 * única vez e dispara os mesmos eventos de mudança financeira da importação — recálculo de cartão,
 * espelho de fatura e reavaliação de vínculos. É idempotente: efetivar um lançamento já efetivado
 * não altera nada.
 */
@Service
public class TransactionSettlementService {

  private final TransactionRepository transactionRepository;
  private final TransactionQueryService queryService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public TransactionSettlementService(
      TransactionRepository transactionRepository,
      TransactionQueryService queryService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.queryService = queryService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /**
   * Efetiva a transação pendente.
   *
   * @param postedOn data efetiva; ausente usa a data atual. Nunca aceita data futura: efetivar é
   *     afirmar que o fato ocorreu.
   */
  @Transactional
  public TransactionView settle(UUID userId, UUID transactionId, LocalDate postedOn) {
    Transaction transaction =
        transactionRepository
            .findByIdAndUserId(transactionId, userId)
            .orElseThrow(NotFoundException::new);
    if (transaction.getStatus() == TransactionStatus.POSTED
        || transaction.getStatus() == TransactionStatus.RECONCILED) {
      // Idempotente: a efetivação já ocorreu.
      return queryService.get(userId, transactionId);
    }
    if (transaction.getStatus() != TransactionStatus.PENDING
        || transaction.getReconciliationState() == ReconciliationState.SOURCE_REMOVED) {
      throw new ConflictException(
          "TRANSACTION_NOT_PENDING", "Somente lançamentos pendentes podem ser efetivados.");
    }
    LocalDate today = clock.today();
    // Sem data informada, a efetivação ocorre hoje: a previsão original pode ser futura e
    // efetivar é afirmar que o fato ocorreu agora.
    LocalDate effectiveDate = postedOn == null ? today : postedOn;
    if (effectiveDate.isAfter(today)) {
      throw new ValidationException("postedOn", "A efetivação não pode ter data futura.");
    }
    LocalDate previous = transaction.getPostedOn();
    if (!effectiveDate.equals(previous)) {
      transaction.setPostedOn(effectiveDate);
      if (transaction.getSource() == TransactionSource.IMPORTED) {
        // A data efetiva difere da previsão: a correção prevalece sobre sincronizações futuras.
        transaction.markEdited("postedOn");
      }
    }
    transaction.markStatus(TransactionStatus.POSTED);
    transactionRepository.save(transaction);

    // Transferência pendente: a perna correspondente é efetivada junto e o par passa a valer.
    if (transaction.getType() == TransactionType.TRANSFER
        && transaction.getRelatedTransactionId() != null) {
      settleCounterpart(userId, transaction.getRelatedTransactionId(), effectiveDate);
    }

    auditService.record(
        userId,
        AuditEventType.TRANSACTION_SETTLED,
        "transaction",
        transaction.getId(),
        Map.of("previousPostedOn", previous.toString(), "postedOn", effectiveDate.toString()));
    eventPublisher.publishEvent(new TransactionFinancialChangedEvent(userId, transaction.getId()));
    return queryService.get(userId, transactionId);
  }

  private void settleCounterpart(UUID userId, UUID counterpartId, LocalDate effectiveDate) {
    Transaction counterpart =
        transactionRepository.findByIdAndUserId(counterpartId, userId).orElse(null);
    if (counterpart == null || counterpart.getStatus() != TransactionStatus.PENDING) {
      return;
    }
    counterpart.setPostedOn(effectiveDate);
    counterpart.markStatus(TransactionStatus.POSTED);
    transactionRepository.save(counterpart);
    eventPublisher.publishEvent(new TransactionFinancialChangedEvent(userId, counterpart.getId()));
  }
}
