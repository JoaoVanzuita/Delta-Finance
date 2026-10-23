package com.deltafinance.transactions.application;

import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionStatus;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Revisão explícita de transações {@code SOURCE_REMOVED}.
 *
 * <p>{@code RESTORE} valida que a transação voltou a existir na origem antes de retornar aos
 * totais; {@code CONFIRM_REMOVAL} mantém a linha e o histórico fora dos totais correntes. Nenhuma
 * ação apaga registros.
 */
@Service
public class SourceRemovalReviewService {

  private static final String TRANSACTION_TABLE = "transaction";

  private final TransactionRepository transactionRepository;
  private final TransactionQueryService queryService;
  private final SourceValidationPort sourceValidationPort;
  private final CryptoService cryptoService;
  private final AuditService auditService;

  public SourceRemovalReviewService(
      TransactionRepository transactionRepository,
      TransactionQueryService queryService,
      SourceValidationPort sourceValidationPort,
      CryptoService cryptoService,
      AuditService auditService) {
    this.transactionRepository = transactionRepository;
    this.queryService = queryService;
    this.sourceValidationPort = sourceValidationPort;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
  }

  public TransactionView review(UUID userId, UUID transactionId, String actionValue) {
    SourceRemovalAction action = parseAction(actionValue);
    Transaction transaction =
        transactionRepository
            .findByIdAndUserId(transactionId, userId)
            .orElseThrow(NotFoundException::new);
    if (transaction.getStatus() != TransactionStatus.SOURCE_REMOVED
        && transaction.getReconciliationState() != ReconciliationState.SOURCE_REMOVED) {
      throw new ConflictException(
          ErrorCode.SOURCE_REMOVAL_CONFLICT,
          "A transação não está marcada como removida na origem.");
    }

    if (action == SourceRemovalAction.CONFIRM_REMOVAL) {
      transaction.markSourceRemoved();
      transactionRepository.save(transaction);
    } else {
      TransactionStatus restored = validatePresenceAtSource(userId, transaction);
      transaction.restoreFromSource(restored);
      transactionRepository.save(transaction);
    }

    auditService.record(
        userId,
        AuditEventType.SOURCE_REMOVED_REVIEWED,
        "transaction",
        transaction.getId(),
        Map.of("action", action.name(), "status", transaction.getStatus().name()));
    return queryService.get(userId, transactionId);
  }

  private TransactionStatus validatePresenceAtSource(UUID userId, Transaction transaction) {
    if (transaction.getExternalTransactionIdEnc() == null) {
      throw new ConflictException(
          ErrorCode.SOURCE_REMOVAL_CONFLICT, "Não é possível validar a origem desta transação.");
    }
    String externalTransactionId =
        cryptoService.decryptString(
            transaction.getExternalTransactionIdEnc(),
            new CryptoContext(
                userId, TRANSACTION_TABLE, "external_transaction_id", transaction.getId()));
    return sourceValidationPort
        .statusAtSource(userId, transaction.getAccountId(), externalTransactionId)
        .orElseThrow(
            () ->
                new ConflictException(
                    ErrorCode.SOURCE_REMOVAL_CONFLICT,
                    "A transação não voltou a aparecer na origem. Confirme a remoção ou tente mais tarde."));
  }

  private static SourceRemovalAction parseAction(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("action", "Informe a ação.");
    }
    try {
      return SourceRemovalAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("action", "Ação inválida.");
    }
  }
}
