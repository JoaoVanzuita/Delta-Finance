package com.deltafinance.reconciliation.application;

import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.categories.domain.Category;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.reconciliation.domain.InternalMatch;
import com.deltafinance.reconciliation.domain.InternalMatchKind;
import com.deltafinance.reconciliation.domain.InternalMatchRepository;
import com.deltafinance.reconciliation.domain.InternalMatchStatus;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.pagination.CursorPage;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sugestões determinísticas de movimentos internos.
 *
 * <p>Critérios (FR-055): contas diferentes, sinais opostos, tolerância {@code max(R$ 0,01, 0,5% do
 * maior valor)}, janela de ±3 dias para transferência e ±7 dias para pagamento de cartão, score
 * 50/30/20 e limiar 80. Empates usam menor diferença de data, depois de valor e, por fim, o
 * identificador local. Nenhuma sugestão é confirmada automaticamente.
 */
@Service
public class InternalMatchService {

  /** Versão explícita dos critérios usados nas sugestões. */
  public static final String CRITERIA_VERSION = "v1";

  private static final int TRANSFER_WINDOW_DAYS = 3;
  private static final int CARD_PAYMENT_WINDOW_DAYS = 7;
  private static final BigDecimal MIN_TOLERANCE = new BigDecimal("0.01");
  private static final BigDecimal TOLERANCE_RATE = new BigDecimal("0.005");
  private static final BigDecimal THRESHOLD = new BigDecimal("80");
  private static final int LOOKBACK_DAYS = 366;

  private final TransactionRepository transactionRepository;
  private final FinancialAccountRepository accountRepository;
  private final InternalMatchRepository matchRepository;
  private final CategoryRepository categoryRepository;
  private final CryptoService cryptoService;
  private final Clock clock;

  public InternalMatchService(
      TransactionRepository transactionRepository,
      FinancialAccountRepository accountRepository,
      InternalMatchRepository matchRepository,
      CategoryRepository categoryRepository,
      CryptoService cryptoService,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.matchRepository = matchRepository;
    this.categoryRepository = categoryRepository;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  public CursorPage<InternalMatchView> list(
      UUID userId, String statusValue, int limit, String cursor) {
    InternalMatchStatus status = parseStatus(statusValue);
    if (status == InternalMatchStatus.PENDING) {
      suggest(userId);
    }
    List<InternalMatch> all =
        matchRepository.findByUserIdAndStatusOrderByScoreDescCreatedAtAsc(userId, status);
    int pageSize = Math.min(100, Math.max(1, limit));
    int start = indexAfterCursor(all, cursor);
    int end = Math.min(all.size(), start + pageSize);
    boolean hasMore = end < all.size();
    List<InternalMatchView> items =
        all.subList(start, end).stream().map(InternalMatchView::from).toList();
    String nextCursor = hasMore && !items.isEmpty() ? encode(all.get(end - 1)) : null;
    return hasMore ? CursorPage.of(items, nextCursor) : CursorPage.last(items);
  }

  /** Quantidade de movimentos internos aguardando decisão do usuário. */
  public long countPending(UUID userId) {
    return matchRepository.countByUserIdAndStatus(userId, InternalMatchStatus.PENDING);
  }

  /** Gera sugestões PENDING ainda inexistentes, de forma idempotente. */
  @Transactional
  public int suggest(UUID userId) {
    LocalDate limit = clock.today().minusDays(LOOKBACK_DAYS);
    List<Transaction> transactions =
        transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(
                transaction ->
                    transaction.getPostedOn() != null
                        && !transaction.getPostedOn().isBefore(limit)
                        && (transaction.getStatus() == TransactionStatus.POSTED
                            || transaction.getStatus() == TransactionStatus.RECONCILED))
            .toList();
    Map<UUID, FinancialAccount> accounts = new HashMap<>();
    for (FinancialAccount account : accountRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
      accounts.put(account.getId(), account);
    }
    // Um ajuste de sinal/valor na importação pode invalidar uma correspondência confirmada
    // (por exemplo, pagamento de cartão que passou a ter o mesmo sinal dos demais débitos).
    // Reabre esses pares antes de calcular candidatos para que a decisão seja refeita.
    reopenInvalidConfirmedMatches(userId, accounts);
    if (transactions.size() < 2) {
      return 0;
    }
    Set<UUID> transferCategoryIds = transferCategoryIds(userId);
    Map<UUID, BigDecimal> amounts = new HashMap<>();
    for (Transaction transaction : transactions) {
      amounts.put(transaction.getId(), effectiveAmount(transaction));
    }

    List<InternalMatch> existing = matchRepository.findByUserIdOrderByCreatedAtDesc(userId);
    Set<String> knownPairs = new HashSet<>();
    Set<UUID> usedTransactions = new HashSet<>();
    for (InternalMatch match : existing) {
      knownPairs.add(pairKey(match.getDebitTransactionId(), match.getCreditTransactionId()));
      if (match.getStatus() != InternalMatchStatus.REJECTED) {
        usedTransactions.add(match.getDebitTransactionId());
        usedTransactions.add(match.getCreditTransactionId());
      }
    }

    List<Candidate> candidates = new ArrayList<>();
    for (Transaction debit : transactions) {
      BigDecimal debitAmount = amounts.get(debit.getId());
      if (debitAmount.signum() >= 0) {
        continue;
      }
      for (Transaction credit : transactions) {
        if (debit.getId().equals(credit.getId())
            || usedTransactions.contains(debit.getId())
            || usedTransactions.contains(credit.getId())
            || (isTransferCategory(debit, transferCategoryIds)
                && isTransferCategory(credit, transferCategoryIds))
            || debit.getAccountId().equals(credit.getAccountId())
            || knownPairs.contains(pairKey(debit.getId(), credit.getId()))) {
          continue;
        }
        BigDecimal creditAmount = amounts.get(credit.getId());
        if (creditAmount.signum() <= 0) {
          continue;
        }
        FinancialAccount debitAccount = accounts.get(debit.getAccountId());
        FinancialAccount creditAccount = accounts.get(credit.getAccountId());
        if (debitAccount == null || creditAccount == null) {
          continue;
        }
        InternalMatchKind kind = kindOf(debitAccount, creditAccount);
        if (kind == null) {
          continue;
        }
        int window =
            kind == InternalMatchKind.CARD_PAYMENT
                ? CARD_PAYMENT_WINDOW_DAYS
                : TRANSFER_WINDOW_DAYS;
        long dayDiff =
            Math.abs(
                Duration.between(
                        debit.getPostedOn().atStartOfDay(), credit.getPostedOn().atStartOfDay())
                    .toDays());
        if (dayDiff > window) {
          continue;
        }
        BigDecimal debitAbs = debitAmount.abs();
        BigDecimal maxAbs = debitAbs.max(creditAmount);
        BigDecimal tolerance = maxAbs.multiply(TOLERANCE_RATE).max(MIN_TOLERANCE);
        BigDecimal valueDiff = debitAbs.subtract(creditAmount).abs();
        if (valueDiff.compareTo(tolerance) > 0) {
          continue;
        }
        BigDecimal score = score(valueDiff, tolerance, dayDiff, window);
        if (score.compareTo(THRESHOLD) < 0) {
          continue;
        }
        candidates.add(new Candidate(kind, debit, credit, score, dayDiff, valueDiff, window));
      }
    }

    candidates.sort(
        Comparator.comparing(Candidate::score)
            .reversed()
            .thenComparingLong(Candidate::dayDiff)
            .thenComparing(Candidate::valueDiff)
            .thenComparing(candidate -> candidate.debit().getId())
            .thenComparing(candidate -> candidate.credit().getId()));

    int created = 0;
    Set<UUID> chosen = new HashSet<>();
    for (Candidate candidate : candidates) {
      if (chosen.contains(candidate.debit().getId())
          || chosen.contains(candidate.credit().getId())) {
        continue;
      }
      chosen.add(candidate.debit().getId());
      chosen.add(candidate.credit().getId());
      matchRepository.save(
          new InternalMatch(
              userId,
              candidate.kind(),
              candidate.debit().getId(),
              candidate.credit().getId(),
              candidate.score(),
              CRITERIA_VERSION,
              reason(candidate)));
      created++;
    }
    return created;
  }

  private BigDecimal effectiveAmount(Transaction transaction) {
    return new BigDecimal(
        cryptoService.decryptString(
            transaction.getAmountEffectiveEnc(),
            new CryptoContext(
                transaction.getUserId(), "transaction", "amount_effective", transaction.getId())));
  }

  private Set<UUID> transferCategoryIds(UUID userId) {
    Set<UUID> ids = new HashSet<>();
    for (Category category : categoryRepository.findByUserIdOrderByNameEncAsc(userId)) {
      if (category.getKind() == CategoryKind.TRANSFER) {
        ids.add(category.getId());
      }
    }
    return ids;
  }

  private static boolean isTransferCategory(
      Transaction transaction, Set<UUID> transferCategoryIds) {
    return transaction.getCategoryId() != null
        && transferCategoryIds.contains(transaction.getCategoryId());
  }

  private static InternalMatchKind kindOf(
      FinancialAccount debitAccount, FinancialAccount creditAccount) {
    boolean debitIsCreditCard = debitAccount.getType() == AccountType.CREDIT;
    boolean creditIsCreditCard = creditAccount.getType() == AccountType.CREDIT;
    if (debitIsCreditCard) {
      // Débito no cartão é compra/encargo; não é pagamento de fatura nem transferência padrão.
      return null;
    }
    return creditIsCreditCard ? InternalMatchKind.CARD_PAYMENT : InternalMatchKind.TRANSFER;
  }

  /**
   * Revalida correspondências automáticas confirmadas após uma atualização dos dados importados
   * (sinal, valor ou tipo das pernas).
   *
   * <p>Pares que deixaram de fazer sentido são removidos e as transações voltam a {@code NONE};
   * pares válidos recuperam {@code MATCHED} caso a transação tenha sido restaurada após uma remoção
   * temporária na origem. Assim a exclusão dos totais continua ligada a uma correspondência
   * confirmada, nunca apenas ao estado da transação.
   */
  private void reopenInvalidConfirmedMatches(UUID userId, Map<UUID, FinancialAccount> accounts) {
    for (InternalMatch match : matchRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
      if (match.getStatus() != InternalMatchStatus.CONFIRMED
          || !CRITERIA_VERSION.equals(match.getCriteriaVersion())) {
        continue;
      }
      Transaction debit =
          transactionRepository
              .findByIdAndUserId(match.getDebitTransactionId(), userId)
              .orElse(null);
      Transaction credit =
          transactionRepository
              .findByIdAndUserId(match.getCreditTransactionId(), userId)
              .orElse(null);
      boolean invalid = debit == null || credit == null;
      if (!invalid) {
        FinancialAccount debitAccount = accounts.get(debit.getAccountId());
        FinancialAccount creditAccount = accounts.get(credit.getAccountId());
        BigDecimal debitAmount = effectiveAmount(debit);
        BigDecimal creditAmount = effectiveAmount(credit);
        invalid =
            debitAccount == null
                || creditAccount == null
                || debitAmount.signum() >= 0
                || creditAmount.signum() <= 0
                || kindOf(debitAccount, creditAccount) == null;
      }
      if (!invalid) {
        ensureMatchedState(debit);
        ensureMatchedState(credit);
        continue;
      }
      matchRepository.delete(match);
      clearMatchedState(debit);
      clearMatchedState(credit);
    }
  }

  private void ensureMatchedState(Transaction transaction) {
    if (transaction == null || transaction.getReconciliationState() != ReconciliationState.NONE) {
      return;
    }
    transaction.setReconciliationState(ReconciliationState.MATCHED);
    transactionRepository.save(transaction);
  }

  private void clearMatchedState(Transaction transaction) {
    if (transaction == null
        || transaction.getReconciliationState() != ReconciliationState.MATCHED) {
      return;
    }
    transaction.setReconciliationState(ReconciliationState.NONE);
    transactionRepository.save(transaction);
  }

  private static BigDecimal score(
      BigDecimal valueDiff, BigDecimal tolerance, long dayDiff, int window) {
    BigDecimal valueScore =
        BigDecimal.valueOf(50)
            .multiply(
                BigDecimal.ONE.subtract(
                    valueDiff.divide(
                        tolerance.multiply(BigDecimal.valueOf(2)), 6, RoundingMode.HALF_EVEN)))
            .max(BigDecimal.ZERO);
    BigDecimal dateScore =
        BigDecimal.valueOf(30)
            .multiply(
                BigDecimal.ONE.subtract(
                    BigDecimal.valueOf(dayDiff)
                        .divide(BigDecimal.valueOf(window * 2L), 6, RoundingMode.HALF_EVEN)))
            .max(BigDecimal.ZERO);
    return valueScore
        .add(dateScore)
        .add(BigDecimal.valueOf(20))
        .setScale(2, RoundingMode.HALF_EVEN);
  }

  private static String reason(Candidate candidate) {
    return "Valor dentro da tolerância; diferença de "
        + candidate.dayDiff()
        + " dia(s); contas distintas.";
  }

  private static String pairKey(UUID debitId, UUID creditId) {
    return debitId + "|" + creditId;
  }

  private static InternalMatchStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return InternalMatchStatus.PENDING;
    }
    try {
      return InternalMatchStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("status", "Estado de correspondência inválido.");
    }
  }

  private static int indexAfterCursor(List<InternalMatch> items, String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return 0;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf('|');
      UUID id = UUID.fromString(raw.substring(separator + 1));
      for (int index = 0; index < items.size(); index++) {
        if (items.get(index).getId().equals(id)) {
          return index + 1;
        }
      }
      return 0;
    } catch (RuntimeException exception) {
      return 0;
    }
  }

  private static String encode(InternalMatch match) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((match.getScore() + "|" + match.getId()).getBytes(StandardCharsets.UTF_8));
  }

  private record Candidate(
      InternalMatchKind kind,
      Transaction debit,
      Transaction credit,
      BigDecimal score,
      long dayDiff,
      BigDecimal valueDiff,
      int window) {}
}
