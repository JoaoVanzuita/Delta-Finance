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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
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
      LocalDate to,
      BigDecimal minAmount,
      BigDecimal maxAmount) {
    validateAmountRange(minAmount, maxAmount);
    Cursor cursor = decode(pageRequest.cursor());
    TransactionStatus parsedStatus = parse(TransactionStatus.class, status, "status");
    TransactionType parsedType = parse(TransactionType.class, type, "type");
    TransactionSource parsedSource = parse(TransactionSource.class, source, "origin");

    if (minAmount == null && maxAmount == null) {
      List<Transaction> rows =
          cursor == null
              ? transactionRepository.findPage(
                  userId,
                  accountId,
                  categoryId,
                  parsedStatus,
                  parsedType,
                  parsedSource,
                  from,
                  to,
                  org.springframework.data.domain.PageRequest.of(0, pageRequest.limit() + 1))
              : transactionRepository.findPageAfter(
                  userId,
                  accountId,
                  categoryId,
                  parsedStatus,
                  parsedType,
                  parsedSource,
                  from,
                  to,
                  cursor.postedOn(),
                  cursor.id(),
                  org.springframework.data.domain.PageRequest.of(0, pageRequest.limit() + 1));
      return toPage(rows, pageRequest.limit());
    }

    // Valores são cifrados: a faixa é aplicada após a decifragem. Varra páginas em blocos até
    // reunir limit + 1 correspondências, para que hasMore/nextCursor continuem corretos.
    int limit = pageRequest.limit();
    int fetchSize = PageRequest.MAX_LIMIT;
    List<Transaction> matches = new ArrayList<>(limit + 1);
    Cursor scan = cursor;
    while (matches.size() <= limit) {
      List<Transaction> rows =
          scan == null
              ? transactionRepository.findPage(
                  userId,
                  accountId,
                  categoryId,
                  parsedStatus,
                  parsedType,
                  parsedSource,
                  from,
                  to,
                  org.springframework.data.domain.PageRequest.of(0, fetchSize))
              : transactionRepository.findPageAfter(
                  userId,
                  accountId,
                  categoryId,
                  parsedStatus,
                  parsedType,
                  parsedSource,
                  from,
                  to,
                  scan.postedOn(),
                  scan.id(),
                  org.springframework.data.domain.PageRequest.of(0, fetchSize));
      if (rows.isEmpty()) {
        break;
      }
      for (Transaction row : rows) {
        if (withinRange(effectiveAmount(row), minAmount, maxAmount)) {
          matches.add(row);
          if (matches.size() > limit) {
            break;
          }
        }
      }
      if (matches.size() > limit || rows.size() < fetchSize) {
        break;
      }
      Transaction last = rows.getLast();
      scan = new Cursor(last.getPostedOn(), last.getId());
    }

    boolean hasMore = matches.size() > limit;
    List<Transaction> page = hasMore ? matches.subList(0, limit) : matches;
    List<TransactionView> views = page.stream().map(this::toView).toList();
    String nextCursor =
        hasMore && !page.isEmpty()
            ? encode(page.getLast().getPostedOn(), page.getLast().getId())
            : null;
    return hasMore ? CursorPage.of(views, nextCursor) : CursorPage.last(views);
  }

  private CursorPage<TransactionView> toPage(List<Transaction> rows, int limit) {
    boolean hasMore = rows.size() > limit;
    List<Transaction> page = hasMore ? rows.subList(0, limit) : rows;
    List<TransactionView> views = page.stream().map(this::toView).toList();
    String nextCursor =
        hasMore && !page.isEmpty()
            ? encode(page.getLast().getPostedOn(), page.getLast().getId())
            : null;
    return hasMore ? CursorPage.of(views, nextCursor) : CursorPage.last(views);
  }

  private BigDecimal effectiveAmount(Transaction transaction) {
    return new BigDecimal(
        cryptoService.decryptString(
            transaction.getAmountEffectiveEnc(),
            new CryptoContext(
                transaction.getUserId(), "transaction", "amount_effective", transaction.getId())));
  }

  private static void validateAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
    if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
      throw new ValidationException(
          "minAmount", "O valor mínimo não pode ser maior que o valor máximo.");
    }
  }

  private static boolean withinRange(BigDecimal value, BigDecimal min, BigDecimal max) {
    BigDecimal magnitude = value.abs();
    if (min != null && magnitude.compareTo(min.abs()) < 0) {
      return false;
    }
    return max == null || magnitude.compareTo(max.abs()) <= 0;
  }

  /** Retorna uma transação do usuário ou lança 404. */
  public TransactionView get(UUID userId, UUID transactionId) {
    return transactionRepository
        .findByIdAndUserId(transactionId, userId)
        .map(this::toView)
        .orElseThrow(com.deltafinance.shared.errors.NotFoundException::new);
  }

  /**
   * Candidatos de relatório no intervalo {@code [from, toExclusive)}, já decifrados.
   *
   * <p>A API pública existe para que o módulo {@code reporting} não dependa do repositório de
   * transações. A faixa de valor é aplicada pelo consumidor após a decifragem.
   */
  public List<TransactionView> listForReporting(
      UUID userId,
      LocalDate from,
      LocalDate toExclusive,
      UUID accountId,
      UUID categoryId,
      TransactionType type,
      TransactionSource source,
      Collection<TransactionStatus> statuses) {
    if (statuses == null || statuses.isEmpty()) {
      return List.of();
    }
    return transactionRepository
        .findForReporting(userId, from, toExclusive, accountId, categoryId, type, source, statuses)
        .stream()
        .map(this::toView)
        .toList();
  }

  /** Estornos/chargebacks vinculados anteriores a {@code toExclusive}, já decifrados. */
  public List<TransactionView> listLinkedReductionsBefore(
      UUID userId,
      UUID relatedTransactionId,
      LocalDate toExclusive,
      Collection<TransactionStatus> statuses) {
    if (statuses == null || statuses.isEmpty()) {
      return List.of();
    }
    return transactionRepository
        .findLinkedReductionsBefore(userId, relatedTransactionId, toExclusive, statuses)
        .stream()
        .map(this::toView)
        .toList();
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
        transaction.getEditedFields(),
        transaction.getReconciliationState().name(),
        decrypt(
            transaction.getUserId(),
            "transaction",
            "notes",
            transaction.getId(),
            transaction.getNotesEnc()),
        decrypt(
            transaction.getUserId(),
            "transaction",
            "adjustment_reason",
            transaction.getId(),
            transaction.getAdjustmentReasonEnc()),
        transaction.getType() == TransactionType.TRANSFER
            ? null
            : transaction.getRelatedTransactionId(),
        transaction.getType() == TransactionType.TRANSFER
            ? transaction.getRelatedTransactionId()
            : null,
        transaction.getInvoiceId());
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
