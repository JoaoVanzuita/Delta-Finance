package com.deltafinance.transactions.application;

import com.deltafinance.accounts.domain.AccountStatus;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.api.ManualTransactionRequest;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lançamentos manuais: receitas, despesas, transferências em duas pernas, estornos,
 * reembolsos/chargebacks vinculados e ajustes.
 *
 * <p>O sinal efetivo é normalizado pelo tipo. Transferências internas criam duas pernas ligadas e
 * um {@code internal_match} já confirmado, sem impacto em receitas/despesas. A exclusão de um
 * lançamento manual nunca afeta transações importadas.
 */
@Service
public class ManualTransactionService {

  private static final String TABLE = "transaction";

  private final TransactionRepository transactionRepository;
  private final FinancialAccountRepository accountRepository;
  private final CategoryService categoryService;
  private final InternalMatchPort internalMatchPort;
  private final TransactionQueryService queryService;
  private final RefundMatcher refundMatcher;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public ManualTransactionService(
      TransactionRepository transactionRepository,
      FinancialAccountRepository accountRepository,
      CategoryService categoryService,
      InternalMatchPort internalMatchPort,
      TransactionQueryService queryService,
      RefundMatcher refundMatcher,
      CryptoService cryptoService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.categoryService = categoryService;
    this.internalMatchPort = internalMatchPort;
    this.queryService = queryService;
    this.refundMatcher = refundMatcher;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Transactional
  public TransactionView create(UUID userId, ManualTransactionRequest request) {
    TransactionType type = parseType(request.type());
    FinancialAccount account = requireActiveAccount(userId, request.accountId());
    String description = requireDescription(request.description());
    BigDecimal amount = parseAmount(request.amount());
    LocalDate postedOn = request.postedOn() == null ? clock.today() : request.postedOn();
    UUID categoryId = validateCategory(userId, request.categoryId());

    if (type == TransactionType.TRANSFER) {
      return createTransfer(userId, request, account, description, amount, postedOn, categoryId);
    }

    UUID relatedTransactionId = null;
    if (type == TransactionType.REFUND || type == TransactionType.CHARGEBACK) {
      Transaction original =
          resolveOriginal(
              userId, account, type, amount, postedOn, description, request.relatedTransactionId());
      relatedTransactionId = original.getId();
      if (categoryId == null) {
        categoryId = original.getCategoryId();
      }
      amount = amount.abs();
    } else if (type == TransactionType.ADJUSTMENT) {
      if (request.adjustmentReason() == null || request.adjustmentReason().isBlank()) {
        throw new ValidationException("adjustmentReason", "Informe o motivo do ajuste manual.");
      }
      if (amount.signum() == 0) {
        throw new ValidationException("amount", "O ajuste deve ter valor diferente de zero.");
      }
    } else {
      amount = type == TransactionType.DEBIT ? amount.abs().negate() : amount.abs();
    }

    String adjustmentReason =
        type == TransactionType.ADJUSTMENT ? request.adjustmentReason().strip() : null;

    Transaction transaction =
        createLeg(
            userId,
            account,
            type,
            amount,
            postedOn,
            description,
            categoryId,
            request.notes(),
            adjustmentReason);
    if (relatedTransactionId != null) {
      transaction.linkRelatedTransaction(relatedTransactionId);
      transactionRepository.save(transaction);
    }
    eventPublisher.publishEvent(new TransactionFinancialChangedEvent(userId, transaction.getId()));
    auditService.record(
        userId,
        AuditEventType.TRANSACTION_CREATED,
        "transaction",
        transaction.getId(),
        Map.of("type", type.name(), "source", TransactionSource.MANUAL.name()));
    return queryService.get(userId, transaction.getId());
  }

  @Transactional
  public void delete(UUID userId, UUID transactionId) {
    Transaction transaction =
        transactionRepository
            .findByIdAndUserId(transactionId, userId)
            .orElseThrow(NotFoundException::new);
    if (transaction.getSource() != TransactionSource.MANUAL) {
      throw new ConflictException(
          "TRANSACTION_NOT_MANUAL",
          "Transações importadas não são apagadas; elas saem dos totais quando deixam de"
              + " aparecer na origem.");
    }
    if (transaction.getType() != TransactionType.TRANSFER
        && transactionRepository.existsByUserIdAndRelatedTransactionId(userId, transactionId)) {
      throw new ConflictException(
          "TRANSACTION_HAS_RELATED_ENTRIES",
          "Exclua primeiro os reembolsos/chargebacks vinculados a esta transação.");
    }

    if (transaction.getType() == TransactionType.TRANSFER
        && transaction.getRelatedTransactionId() != null) {
      UUID counterpartId = transaction.getRelatedTransactionId();
      Transaction counterpart =
          transactionRepository.findByIdAndUserId(counterpartId, userId).orElse(null);
      eventPublisher.publishEvent(
          new TransactionDeletedEvent(userId, transaction.getId(), transaction.getAccountId()));
      if (counterpart != null) {
        eventPublisher.publishEvent(
            new TransactionDeletedEvent(userId, counterpart.getId(), counterpart.getAccountId()));
      }
      transaction.linkRelatedTransaction(null);
      Transaction first = transactionRepository.saveAndFlush(transaction);
      if (counterpart != null) {
        counterpart.linkRelatedTransaction(null);
        Transaction second = transactionRepository.saveAndFlush(counterpart);
        transactionRepository.deleteAll(java.util.List.of(first, second));
      } else {
        transactionRepository.delete(first);
      }
    } else {
      eventPublisher.publishEvent(
          new TransactionDeletedEvent(userId, transaction.getId(), transaction.getAccountId()));
      transactionRepository.delete(transaction);
    }
    auditService.record(
        userId,
        AuditEventType.TRANSACTION_DELETED,
        "transaction",
        transactionId,
        Map.of("type", transaction.getType().name()));
  }

  private TransactionView createTransfer(
      UUID userId,
      ManualTransactionRequest request,
      FinancialAccount origin,
      String description,
      BigDecimal amount,
      LocalDate postedOn,
      UUID categoryId) {
    if (request.transferAccountId() == null) {
      throw new ValidationException("transferAccountId", "Informe a conta de destino.");
    }
    if (request.transferAccountId().equals(origin.getId())) {
      throw new ValidationException(
          "transferAccountId", "A conta de destino deve ser diferente da origem.");
    }
    FinancialAccount destination = requireActiveAccount(userId, request.transferAccountId());
    if (!origin.getCurrency().equals(destination.getCurrency())) {
      throw new ValidationException(
          "transferAccountId", "Transferências entre moedas diferentes não são suportadas.");
    }
    BigDecimal magnitude = amount.abs();
    if (magnitude.signum() == 0) {
      throw new ValidationException("amount", "Informe um valor maior que zero.");
    }

    Transaction debit =
        createLeg(
            userId,
            origin,
            TransactionType.TRANSFER,
            magnitude.negate(),
            postedOn,
            description,
            categoryId,
            request.notes(),
            null);
    Transaction credit =
        createLeg(
            userId,
            destination,
            TransactionType.TRANSFER,
            magnitude,
            postedOn,
            description,
            categoryId,
            request.notes(),
            null);
    debit.linkRelatedTransaction(credit.getId());
    credit.linkRelatedTransaction(debit.getId());
    transactionRepository.save(debit);
    transactionRepository.save(credit);

    internalMatchPort.confirmManualTransfer(userId, debit.getId(), credit.getId());
    eventPublisher.publishEvent(new TransactionFinancialChangedEvent(userId, debit.getId()));
    eventPublisher.publishEvent(new TransactionFinancialChangedEvent(userId, credit.getId()));

    auditService.record(
        userId,
        AuditEventType.TRANSACTION_CREATED,
        "transaction",
        debit.getId(),
        Map.of("type", TransactionType.TRANSFER.name(), "legs", 2));
    return queryService.get(userId, debit.getId());
  }

  private Transaction createLeg(
      UUID userId,
      FinancialAccount account,
      TransactionType type,
      BigDecimal amount,
      LocalDate postedOn,
      String description,
      UUID categoryId,
      String notes,
      String adjustmentReason) {
    UUID id = UUID.randomUUID();
    BigDecimal scaled = amount.setScale(Money.SCALE, RoundingMode.HALF_EVEN);
    byte[] amountEnc =
        cryptoService.encrypt(
            scaled.toPlainString(), new CryptoContext(userId, TABLE, "amount_effective", id));
    byte[] amountSourceEnc =
        cryptoService.encrypt(
            scaled.toPlainString(), new CryptoContext(userId, TABLE, "amount_source", id));
    byte[] descriptionEnc =
        cryptoService.encrypt(
            description, new CryptoContext(userId, TABLE, "description_effective", id));
    byte[] descriptionSourceEnc =
        cryptoService.encrypt(
            description, new CryptoContext(userId, TABLE, "description_source", id));

    Transaction transaction =
        new Transaction(
            id,
            userId,
            account.getId(),
            TransactionSource.MANUAL,
            type,
            TransactionStatus.POSTED,
            postedOn,
            postedOn,
            account.getCurrency(),
            amountSourceEnc,
            amountEnc,
            descriptionSourceEnc,
            descriptionEnc);
    transaction.setCategoryId(categoryId);
    if (notes != null && !notes.isBlank()) {
      transaction.setNotesEnc(
          cryptoService.encrypt(notes.strip(), new CryptoContext(userId, TABLE, "notes", id)));
    }
    if (adjustmentReason != null) {
      transaction.setAdjustmentReasonEnc(
          cryptoService.encrypt(
              adjustmentReason, new CryptoContext(userId, TABLE, "adjustment_reason", id)));
    }
    return transactionRepository.save(transaction);
  }

  private Transaction resolveOriginal(
      UUID userId,
      FinancialAccount account,
      TransactionType type,
      BigDecimal amount,
      LocalDate postedOn,
      String description,
      UUID relatedTransactionId) {
    if (relatedTransactionId != null) {
      Transaction original =
          transactionRepository
              .findByIdAndUserId(relatedTransactionId, userId)
              .orElseThrow(NotFoundException::new);
      if (!original.getAccountId().equals(account.getId())
          || original.getType() != TransactionType.DEBIT) {
        throw new ValidationException(
            "relatedTransactionId", "A transação original deve ser uma despesa da mesma conta.");
      }
      return original;
    }
    return refundMatcher
        .findOriginal(userId, account.getId(), type, amount, postedOn, description)
        .orElseThrow(
            () ->
                new ValidationException(
                    "relatedTransactionId",
                    "Selecione a transação original para "
                        + type.name().toLowerCase(Locale.ROOT)
                        + "; nenhuma despesa compatível foi encontrada."));
  }

  private FinancialAccount requireActiveAccount(UUID userId, UUID accountId) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new ConflictException(
          "ACCOUNT_INACTIVE", "A conta está inativa e não recebe novos lançamentos.");
    }
    return account;
  }

  private UUID validateCategory(UUID userId, UUID categoryId) {
    if (categoryId == null) {
      return null;
    }
    return categoryService.requireActiveCategory(userId, categoryId).getId();
  }

  private static TransactionType parseType(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("type", "Informe o tipo do lançamento.");
    }
    try {
      return TransactionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("type", "Tipo de lançamento inválido.");
    }
  }

  private static String requireDescription(String description) {
    if (description == null || description.isBlank()) {
      throw new ValidationException("description", "Informe a descrição.");
    }
    String trimmed = description.strip();
    if (trimmed.length() > 500) {
      throw new ValidationException(
          "description", "A descrição deve ter no máximo 500 caracteres.");
    }
    return trimmed;
  }

  private static BigDecimal parseAmount(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("amount", "Informe o valor.");
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException exception) {
      throw new ValidationException("amount", "Valor inválido.");
    }
  }
}
