package com.deltafinance.transactions.application;

import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.pagination.CursorPage;
import com.deltafinance.shared.pagination.PageRequest;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Consulta paginada de transações, sempre escopada pelo usuário da sessão. */
@Service
public class TransactionQueryService {

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final CryptoService cryptoService;

  public TransactionQueryService(
      TransactionRepository transactionRepository,
      CategoryRepository categoryRepository,
      CryptoService cryptoService) {
    this.transactionRepository = transactionRepository;
    this.categoryRepository = categoryRepository;
    this.cryptoService = cryptoService;
  }

  public CursorPage<TransactionView> list(
      UUID userId,
      PageRequest pageRequest,
      UUID accountId,
      UUID categoryId,
      String status,
      String type,
      String source,
      LocalDate from,
      LocalDate to) {
    Cursor cursor = decode(pageRequest.cursor());
    Pageable pageable = org.springframework.data.domain.PageRequest.of(0, pageRequest.limit() + 1);
    List<Transaction> rows =
        transactionRepository.findPage(
            userId,
            accountId,
            categoryId,
            parse(TransactionStatus.class, status, "status"),
            parse(TransactionType.class, type, "type"),
            parse(TransactionSource.class, source, "origin"),
            from,
            to,
            cursor == null ? null : cursor.postedOn(),
            cursor == null ? null : cursor.id(),
            pageable);

    boolean hasMore = rows.size() > pageRequest.limit();
    List<Transaction> page = hasMore ? rows.subList(0, pageRequest.limit()) : rows;
    List<TransactionView> views = page.stream().map(this::toView).toList();
    String nextCursor =
        hasMore && !page.isEmpty()
            ? encode(page.getLast().getPostedOn(), page.getLast().getId())
            : null;
    return hasMore ? CursorPage.of(views, nextCursor) : CursorPage.last(views);
  }

  /** Retorna uma transação do usuário ou lança 404. */
  public TransactionView get(UUID userId, UUID transactionId) {
    return transactionRepository
        .findByIdAndUserId(transactionId, userId)
        .map(this::toView)
        .orElseThrow(com.deltafinance.shared.errors.NotFoundException::new);
  }

  private TransactionView toView(Transaction transaction) {
    String categoryName = null;
    if (transaction.getCategoryId() != null) {
      categoryName =
          categoryRepository
              .findByIdAndUserId(transaction.getCategoryId(), transaction.getUserId())
              .map(
                  category ->
                      decrypt(
                          transaction.getUserId(),
                          "category",
                          "name",
                          category.getId(),
                          category.getNameEnc()))
              .orElse(null);
    }
    return new TransactionView(
        transaction.getId(),
        transaction.getAccountId(),
        transaction.getCategoryId(),
        categoryName,
        transaction.getSource().name(),
        transaction.getType().name(),
        transaction.getStatus().name(),
        transaction.getPostedOn(),
        decrypt(
            transaction.getUserId(),
            "transaction",
            "amount_effective",
            transaction.getId(),
            transaction.getAmountEffectiveEnc()),
        transaction.getCurrency(),
        decrypt(
            transaction.getUserId(),
            "transaction",
            "description_effective",
            transaction.getId(),
            transaction.getDescriptionEffectiveEnc()),
        transaction.getReconciliationState().name(),
        transaction.getRelatedTransactionId());
  }

  private String decrypt(UUID userId, String table, String column, UUID recordId, byte[] value) {
    if (value == null) {
      return null;
    }
    try {
      return cryptoService.decryptString(value, new CryptoContext(userId, table, column, recordId));
    } catch (CryptoException exception) {
      return null;
    }
  }

  private static String encode(LocalDate postedOn, UUID id) {
    String raw = postedOn + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf('|');
      if (separator <= 0) {
        throw new IllegalArgumentException("cursor sem separador");
      }
      return new Cursor(
          LocalDate.parse(raw.substring(0, separator)),
          UUID.fromString(raw.substring(separator + 1)));
    } catch (RuntimeException exception) {
      throw new ValidationException("cursor", "Cursor inválido.");
    }
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(field, "Valor inválido para " + field + ".");
    }
  }

  private record Cursor(LocalDate postedOn, UUID id) {}
}
