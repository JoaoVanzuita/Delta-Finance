package com.deltafinance.transactions.application;

import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
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
  private final Clock clock;

  public TransactionCorrectionService(
      TransactionRepository transactionRepository,
      TransactionCorrectionRepository correctionRepository,
      CategoryService categoryService,
      TransactionQueryService queryService,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.correctionRepository = correctionRepository;
    this.categoryService = categoryService;
    this.queryService = queryService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional
  public TransactionView correct(
      UUID userId, UUID transactionId, TransactionCorrectionRequest request) {
    Transaction transaction =
        transactionRepository
            .findByIdAndUserId(transactionId, userId)
            .orElseThrow(NotFoundException::new);
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
    return queryService.get(userId, transactionId);
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
