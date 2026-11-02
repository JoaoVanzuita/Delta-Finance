package com.deltafinance.reporting.application;

import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.connections.application.SyncQueryService;
import com.deltafinance.investments.application.InvestmentContributionService;
import com.deltafinance.investments.application.InvestmentContributionView;
import com.deltafinance.invoices.application.CardDebtService;
import com.deltafinance.reconciliation.application.InternalMatchService;
import com.deltafinance.reporting.application.ReportEntry.Direction;
import com.deltafinance.transactions.application.TransactionQueryService;
import com.deltafinance.transactions.application.TransactionView;
import com.deltafinance.transactions.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Dashboard e relatórios consolidados.
 *
 * <p>Resolução de filtros, montagem das entradas e agregação por categoria ficam em componentes
 * próprios. Este serviço compõe o resumo, as categorias, a série temporal e os blocos auxiliares
 * (pendentes, saldo, cartão e sincronização).
 */
@Service
public class ReportingService {

  public static final UUID UNCATEGORIZED_ID = ReportQueryResolver.UNCATEGORIZED_ID;
  public static final String UNCATEGORIZED_NAME = ReportQueryResolver.UNCATEGORIZED_NAME;

  private static final List<TransactionStatus> PENDING_STATUSES =
      List.of(TransactionStatus.PENDING);
  private static final Set<String> BALANCE_TYPES = Set.of("BANK", "CASH", "WALLET");

  private final TransactionQueryService transactionQueryService;
  private final CardDebtService cardDebtService;
  private final SyncQueryService syncQueryService;
  private final InternalMatchService internalMatchService;
  private final InvestmentContributionService investmentContributionService;
  private final ReportQueryResolver queryResolver;
  private final ReportEntryAssembler entryAssembler;
  private final ReportCategoryAggregator categoryAggregator;

  public ReportingService(
      TransactionQueryService transactionQueryService,
      CardDebtService cardDebtService,
      SyncQueryService syncQueryService,
      InternalMatchService internalMatchService,
      InvestmentContributionService investmentContributionService,
      ReportQueryResolver queryResolver,
      ReportEntryAssembler entryAssembler,
      ReportCategoryAggregator categoryAggregator) {
    this.transactionQueryService = transactionQueryService;
    this.cardDebtService = cardDebtService;
    this.syncQueryService = syncQueryService;
    this.internalMatchService = internalMatchService;
    this.investmentContributionService = investmentContributionService;
    this.queryResolver = queryResolver;
    this.entryAssembler = entryAssembler;
    this.categoryAggregator = categoryAggregator;
  }

  public DashboardSummaryView summary(UUID userId, ReportQuery query) {
    ResolvedReportQuery resolved = queryResolver.resolve(userId, query);
    List<ReportEntry> entries = entryAssembler.entries(userId, resolved);
    Map<UUID, CategoryAggregate> categories =
        categoryAggregator.categoriesOf(entries, false, resolved);
    BigDecimal income = categoryAggregator.incomeOf(entries);
    BigDecimal expenses = categoryAggregator.expensesOf(categories);
    BigDecimal periodResult = income.subtract(expenses);
    Pending pending = pending(userId, resolved);
    Balance balance = currentBalance(resolved);
    BigDecimal cardOutstanding = cardOutstanding(userId, resolved);
    InvestmentContributionView investments =
        investmentContributionService.contributions(userId, resolved.from(), resolved.to(), null);

    return new DashboardSummaryView(
        resolved.from(),
        resolved.to(),
        MoneyAggregator.format(periodResult),
        MoneyAggregator.format(balance.amount()),
        balance.referenceAt(),
        MoneyAggregator.format(income),
        MoneyAggregator.format(expenses),
        pending.count(),
        MoneyAggregator.format(pending.income()),
        MoneyAggregator.format(pending.expenses()),
        internalMatchService.countPending(userId),
        lastSuccessfulSync(userId, resolved),
        MoneyAggregator.format(cardOutstanding),
        investments.contributions(),
        investments.withdrawals(),
        investments.netContributions(),
        investments.trackingAvailable());
  }

  public List<DashboardCategoryView> categories(
      UUID userId, ReportQuery query, boolean incomeOnly) {
    ResolvedReportQuery resolved = queryResolver.resolve(userId, query);
    Map<UUID, CategoryAggregate> aggregates =
        categoryAggregator.categoriesOf(
            entryAssembler.entries(userId, resolved), incomeOnly, resolved);
    return aggregates.entrySet().stream()
        .filter(entry -> entry.getValue().count > 0)
        .sorted(
            Map.Entry.<UUID, CategoryAggregate>comparingByValue(
                    Comparator.comparing((CategoryAggregate aggregate) -> aggregate.amount))
                .reversed()
                .thenComparing(entry -> categoryName(resolved, entry.getKey())))
        .map(
            entry ->
                new DashboardCategoryView(
                    entry.getKey(),
                    categoryName(resolved, entry.getKey()),
                    MoneyAggregator.format(entry.getValue().amount),
                    entry.getValue().count))
        .toList();
  }

  public List<DashboardBucketView> timeseries(UUID userId, ReportQuery query) {
    ResolvedReportQuery resolved = queryResolver.resolve(userId, query);
    List<ReportEntry> entries = entryAssembler.entries(userId, resolved);
    List<Bucket> buckets = queryResolver.buckets(resolved);
    Map<LocalDate, BucketAggregate> aggregates = new HashMap<>();
    for (ReportEntry entry : entries) {
      LocalDate date = queryResolver.bucketStartFor(entry.occurredOn(), resolved.period());
      if (date == null) {
        continue;
      }
      BucketAggregate aggregate =
          aggregates.computeIfAbsent(date, ignored -> new BucketAggregate());
      if (entry.direction() == Direction.INCOME) {
        aggregate.income = aggregate.income.add(entry.amount());
      } else if (entry.direction() == Direction.EXPENSE) {
        aggregate.grossExpenses = aggregate.grossExpenses.add(entry.amount().abs());
      } else {
        aggregate.expenseReductions = aggregate.expenseReductions.add(entry.amount().abs());
      }
    }
    List<BucketNet> nets = new ArrayList<>();
    for (Bucket bucket : buckets) {
      BucketAggregate aggregate = aggregates.getOrDefault(bucket.start(), new BucketAggregate());
      nets.add(
          new BucketNet(
              bucket,
              aggregate.income,
              aggregate.grossExpenses.subtract(aggregate.expenseReductions)));
    }
    // Redução sem despesa no mesmo dia é aplicada ao próximo dia com despesa; o que sobra no fim
    // volta para os últimos dias. Nenhum ponto fica negativo e a soma da série continua igual ao
    // total de despesas.
    BigDecimal carry = BigDecimal.ZERO;
    for (BucketNet net : nets) {
      BigDecimal expenses = net.expenses.subtract(carry);
      if (expenses.signum() < 0) {
        carry = expenses.negate();
        net.expenses = BigDecimal.ZERO;
      } else {
        carry = BigDecimal.ZERO;
        net.expenses = expenses;
      }
    }
    for (int index = nets.size() - 1; index >= 0 && carry.signum() > 0; index--) {
      BucketNet net = nets.get(index);
      BigDecimal applied = carry.min(net.expenses);
      net.expenses = net.expenses.subtract(applied);
      carry = carry.subtract(applied);
    }
    return nets.stream()
        .map(
            net ->
                new DashboardBucketView(
                    net.bucket.label(),
                    net.bucket.start(),
                    net.bucket.end(),
                    MoneyAggregator.format(net.income),
                    MoneyAggregator.format(net.expenses),
                    MoneyAggregator.format(net.income.subtract(net.expenses))))
        .toList();
  }

  /** Entradas que compõem os totais, na mesma projeção usada pelo drill-down. */
  public List<ReportEntry> entries(UUID userId, ReportQuery query) {
    return entryAssembler.entries(userId, queryResolver.resolve(userId, query));
  }

  private Pending pending(UUID userId, ResolvedReportQuery resolved) {
    UUID effectiveCategoryId =
        ReportQueryResolver.UNCATEGORIZED_ID.equals(resolved.categoryId())
            ? null
            : resolved.categoryId();
    Set<UUID> creditCardAccounts = new HashSet<>();
    for (AccountView account : resolved.accounts()) {
      if ("CREDIT".equals(account.type())) {
        creditCardAccounts.add(account.id());
      }
    }
    int count = 0;
    BigDecimal income = BigDecimal.ZERO;
    BigDecimal expenses = BigDecimal.ZERO;
    BigDecimal expenseReductions = BigDecimal.ZERO;
    for (TransactionView transaction :
        transactionQueryService.listForReporting(
            userId,
            resolved.from(),
            resolved.toExclusive(),
            singleAccountId(resolved),
            effectiveCategoryId,
            resolved.type(),
            resolved.origin(),
            PENDING_STATUSES)) {
      if (!entryAssembler.isTransactionIncluded(transaction, resolved)) {
        continue;
      }
      BigDecimal amount = MoneyAggregator.parse(transaction.amount());
      if (amount.signum() == 0) {
        continue;
      }
      count++;
      if (amount.signum() > 0) {
        if (creditCardAccounts.contains(transaction.accountId())) {
          // Entrada pendente em cartão é pagamento/estorno: reduz despesa e nunca vira receita.
          expenseReductions = expenseReductions.add(amount);
        } else {
          income = income.add(amount);
        }
      } else {
        expenses = expenses.add(amount.abs());
      }
    }
    return new Pending(count, income, expenses.subtract(expenseReductions).max(BigDecimal.ZERO));
  }

  private Balance currentBalance(ResolvedReportQuery resolved) {
    BigDecimal total = BigDecimal.ZERO;
    Instant reference = null;
    for (AccountView account : resolved.accounts()) {
      if (!accountIncluded(resolved, account.id())) {
        continue;
      }
      if (!BALANCE_TYPES.contains(account.type()) || !"ACTIVE".equals(account.status())) {
        continue;
      }
      if (!"BRL".equalsIgnoreCase(account.currency())) {
        continue;
      }
      BigDecimal balance = MoneyAggregator.parse(account.currentBalance());
      total = total.add(balance);
      Instant accountReference = account.balanceReferenceAt();
      if (accountReference != null && (reference == null || accountReference.isBefore(reference))) {
        reference = accountReference;
      }
    }
    return new Balance(total, reference);
  }

  private BigDecimal cardOutstanding(UUID userId, ResolvedReportQuery resolved) {
    Set<UUID> cardIds = new HashSet<>();
    for (AccountView account : resolved.accounts()) {
      if (!"CREDIT".equals(account.type())) {
        continue;
      }
      if (!accountIncluded(resolved, account.id())) {
        continue;
      }
      if (!"BRL".equalsIgnoreCase(account.currency())) {
        continue;
      }
      cardIds.add(account.id());
    }
    return cardDebtService.total(userId, cardIds);
  }

  private Instant lastSuccessfulSync(UUID userId, ResolvedReportQuery resolved) {
    Set<UUID> connectionIds = new HashSet<>();
    for (AccountView account : resolved.accounts()) {
      if (!accountIncluded(resolved, account.id())) {
        continue;
      }
      UUID connectionId = resolved.connectionIdsByAccount().get(account.id());
      if (connectionId != null) {
        connectionIds.add(connectionId);
      }
    }
    return syncQueryService.lastSuccessfulSyncAt(userId, connectionIds);
  }

  private String categoryName(ResolvedReportQuery resolved, UUID categoryId) {
    if (ReportQueryResolver.UNCATEGORIZED_ID.equals(categoryId)) {
      return ReportQueryResolver.UNCATEGORIZED_NAME;
    }
    return resolved
        .categoryNames()
        .getOrDefault(categoryId, ReportQueryResolver.UNCATEGORIZED_NAME);
  }

  private static boolean accountIncluded(ResolvedReportQuery resolved, UUID accountId) {
    return resolved.accountIds().isEmpty() || resolved.accountIds().contains(accountId);
  }

  private static UUID singleAccountId(ResolvedReportQuery resolved) {
    return resolved.accountIds().size() == 1 ? resolved.accountIds().iterator().next() : null;
  }

  private static final class BucketNet {
    private final Bucket bucket;
    private final BigDecimal income;
    private BigDecimal expenses;

    private BucketNet(Bucket bucket, BigDecimal income, BigDecimal expenses) {
      this.bucket = bucket;
      this.income = income;
      this.expenses = expenses;
    }
  }

  private static final class BucketAggregate {
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal grossExpenses = BigDecimal.ZERO;
    private BigDecimal expenseReductions = BigDecimal.ZERO;
  }

  private record Pending(int count, BigDecimal income, BigDecimal expenses) {}

  private record Balance(BigDecimal amount, Instant referenceAt) {}
}
