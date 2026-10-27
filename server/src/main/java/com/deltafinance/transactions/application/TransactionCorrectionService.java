package com.deltafinance.transactions.application;

import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.api.TransactionCorrectionRequest;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionCorrection;
import com.deltafinance.transactions.domain.TransactionCorrectionRepository;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Correções do usuário em transações.
 *
 * <p>Cada campo alterado gera um registro em {@code transaction_correction} com valores cifrados.
 * Campos corrigidos passam a prevalecer sobre sincronizações futuras (FR-059/FR-060); os valores
 * originais da origem continuam disponíveis para auditoria.
 */
@Service
public class TransactionCorrectionService {

  private static final String TABLE = "transaction";
  private static final String CORRECTION_TABLE = "transaction_correction";

  private final TransactionRepository transactionRepository;
  private final TransactionCorrectionRepository correctionRepository;
  private final CategoryService categoryService;
  private final TransactionQueryService queryService;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final PaidInvoiceGuard paidInvoiceGuard;
  private final Clock clock;

  public TransactionCorrectionService(
      TransactionRepository transactionRepository,
      TransactionCorrectionRepository correctionRepository,
      CategoryService categoryService,
      TransactionQueryService queryService,
      CryptoService cryptoService,
      AuditService auditService,
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher eventPublisher,
      PaidInvoiceGuard paidInvoiceGuard,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.correctionRepository = correctionRepository;
    this.categoryService = categoryService;
    this.queryService = queryService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.jdbcTemplate = jdbcTemplate;
    this.eventPublisher = eventPublisher;
    this.paidInvoiceGuard = paidInvoiceGuard;
    this.clock = clock;
  }

  @Transactional
  public TransactionView correct(
      UUID userId, UUID transactionId, TransactionCorrectionRequest request) {
    Transaction transaction =
        transactionRepository
            .findByIdAndUserId(transactionId, userId)
            .orElseThrow(NotFoundException::new);
    boolean changesInvoiceMirror =
        request.description() != null || request.postedOn() != null || request.amount() != null;
    if (changesInvoiceMirror && paidInvoiceGuard.isInPaidInvoice(userId, transactionId)) {
      throw new ConflictException(
          "TRANSACTION_PAID_INVOICE",
          "A compra está em uma fatura paga; registre um ajuste explícito na fatura antes de "
              + "alterar valor, data ou descrição.");
    }
    boolean lockedTransferLeg = isLockedTransferLeg(userId, transaction);
    List<CorrectionDraft> drafts = new ArrayList<>();

    if (request.description() != null) {
      String next = request.description().strip();
      if (next.isBlank() || next.length() > 500) {
        throw new ValidationException(
            "description", "A descrição deve ter entre 1 e 500 caracteres.");
      }
      String previous =
          decrypt(
              userId,
              transactionId,
              "description_effective",
              transaction.getDescriptionEffectiveEnc());
      if (!next.equals(previous)) {
        transaction.setDescriptionEffectiveEnc(
            encrypt(userId, transactionId, "description_effective", next));
        transaction.markEdited("description");
        drafts.add(new CorrectionDraft("description", previous, next));
      }
    }

    if (request.postedOn() != null && !request.postedOn().equals(transaction.getPostedOn())) {
      if (lockedTransferLeg) {
        throw transferLegLocked();
      }
      String previous = transaction.getPostedOn().toString();
      transaction.setPostedOn(request.postedOn());
      transaction.markEdited("postedOn");
      drafts.add(new CorrectionDraft("postedOn", previous, request.postedOn().toString()));
    }

    if (request.amount() != null) {
      BigDecimal previousAmount =
          new BigDecimal(
              decrypt(
                  userId, transactionId, "amount_effective", transaction.getAmountEffectiveEnc()));
      BigDecimal next = parseAmount(request.amount());
      BigDecimal signed = applySign(transaction.getType(), previousAmount, next);
      if (signed.compareTo(previousAmount) != 0) {
        if (lockedTransferLeg) {
          throw transferLegLocked();
        }
        transaction.setAmountEffectiveEnc(
            encrypt(userId, transactionId, "amount_effective", signed.toPlainString()));
        transaction.markEdited("amount");
        drafts.add(
            new CorrectionDraft("amount", previousAmount.toPlainString(), signed.toPlainString()));
      }
    }

    if (request.categoryId() != null && !request.categoryId().equals(transaction.getCategoryId())) {
      categoryService.requireActiveCategory(userId, request.categoryId());
      String previous =
          transaction.getCategoryId() == null ? null : transaction.getCategoryId().toString();
      transaction.setCategoryId(request.categoryId());
      transaction.markEdited("category");
      drafts.add(new CorrectionDraft("category", previous, request.categoryId().toString()));
    }

    if (request.relatedTransactionId() != null) {
      if (transaction.getType() != TransactionType.REFUND
          && transaction.getType() != TransactionType.CHARGEBACK) {
        throw new ValidationException(
            "relatedTransactionId", "Somente reembolsos e chargebacks podem ser vinculados.");
      }
      Transaction original =
          transactionRepository
              .findByIdAndUserId(request.relatedTransactionId(), userId)
              .orElseThrow(NotFoundException::new);
      if (original.getId().equals(transaction.getId())
          || !original.getAccountId().equals(transaction.getAccountId())
          || original.getType() != TransactionType.DEBIT) {
        throw new ValidationException(
            "relatedTransactionId", "A original deve ser uma despesa da mesma conta.");
      }
      if (!original.getId().equals(transaction.getRelatedTransactionId())) {
        String previous =
            transaction.getRelatedTransactionId() == null
                ? null
                : transaction.getRelatedTransactionId().toString();
        transaction.linkRelatedTransaction(original.getId());
        drafts.add(
            new CorrectionDraft("relatedTransactionId", previous, original.getId().toString()));
      }
    }

    if (request.notes() != null) {
      String next = request.notes().strip();
      if (next.length() > 2000) {
        throw new ValidationException(
            "notes", "As observações devem ter no máximo 2000 caracteres.");
      }
      String previous =
          transaction.getNotesEnc() == null
              ? null
              : decrypt(userId, transactionId, "notes", transaction.getNotesEnc());
      if (!Objects.equals(next, previous)) {
        transaction.setNotesEnc(
            next.isEmpty() ? null : encrypt(userId, transactionId, "notes", next));
        transaction.markEdited("notes");
        drafts.add(new CorrectionDraft("notes", previous, next));
      }
    }

    if (drafts.isEmpty()) {
      throw new ValidationException("fields", "Nenhum campo foi alterado.");
    }

    transactionRepository.save(transaction);
    for (CorrectionDraft draft : drafts) {
      UUID correctionId = UUID.randomUUID();
      correctionRepository.save(
          new TransactionCorrection(
              correctionId,
              userId,
              transactionId,
              draft.field(),
              draft.previous() == null
                  ? null
                  : cryptoService.encrypt(
                      draft.previous(),
                      new CryptoContext(userId, CORRECTION_TABLE, "old_value", correctionId)),
              cryptoService.encrypt(
                  draft.next(),
                  new CryptoContext(userId, CORRECTION_TABLE, "new_value", correctionId)),
              clock.now()));
    }
    auditService.record(
        userId,
        AuditEventType.TRANSACTION_CORRECTED,
        "transaction",
        transactionId,
        Map.of("fields", String.join(",", drafts.stream().map(CorrectionDraft::field).toList())));
    eventPublisher.publishEvent(new TransactionFinancialChangedEvent(userId, transactionId));
    return queryService.get(userId, transactionId);
  }

  private boolean isLockedTransferLeg(UUID userId, Transaction transaction) {
    if (transaction.getType() == TransactionType.TRANSFER
        && transaction.getRelatedTransactionId() != null) {
      return true;
    }
    Long confirmed =
        jdbcTemplate.queryForObject(
            "select count(*) from internal_match where user_id = ? and status = 'CONFIRMED'"
                + " and (debit_transaction_id = ? or credit_transaction_id = ?)",
            Long.class,
            userId,
            transaction.getId(),
            transaction.getId());
    return confirmed != null && confirmed > 0;
  }

  private static ConflictException transferLegLocked() {
    return new ConflictException(
        ErrorCode.CONFLICT,
        "Valor e data de uma perna de transferência não podem ser alterados. "
            + "Exclua e recrie o par se precisar mudar o valor ou a data.");
  }

  private static BigDecimal applySign(
      TransactionType type, BigDecimal previous, BigDecimal requested) {
    BigDecimal magnitude = requested.abs();
    if (type == TransactionType.ADJUSTMENT) {
      return requested.setScale(2, RoundingMode.HALF_EVEN);
    }
    if (previous.signum() < 0 || (previous.signum() == 0 && type == TransactionType.DEBIT)) {
      return magnitude.negate().setScale(2, RoundingMode.HALF_EVEN);
    }
    return magnitude.setScale(2, RoundingMode.HALF_EVEN);
  }

  private static BigDecimal parseAmount(String value) {
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException exception) {
      throw new ValidationException("amount", "Valor inválido.");
    }
  }

  private String decrypt(UUID userId, UUID transactionId, String column, byte[] value) {
    return cryptoService.decryptString(
        value, new CryptoContext(userId, TABLE, column, transactionId));
  }

  private byte[] encrypt(UUID userId, UUID transactionId, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, TABLE, column, transactionId));
  }

  private record CorrectionDraft(String field, String previous, String next) {}
}
