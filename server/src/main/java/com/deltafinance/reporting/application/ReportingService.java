package com.deltafinance.reporting.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.categories.application.CategoryView;
import com.deltafinance.connections.application.SyncQueryService;
import com.deltafinance.invoices.application.InvoiceReportingService;
import com.deltafinance.invoices.application.InvoiceReportingService.InvoiceReportItem;
import com.deltafinance.reconciliation.application.InternalMatchService;
import com.deltafinance.reporting.application.ReportEntry.Direction;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.TransactionQueryService;
import com.deltafinance.transactions.application.TransactionView;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Dashboard e relatórios consolidados (US4).
 *
 * <p>Regras: períodos calendário em {@code America/Sao_Paulo}; somente {@code POSTED}/{@code
 * RECONCILED} entram nos totais; transferências e movimentos internos confirmados não entram em
 * receitas/despesas; itens espelho de fatura não entram novamente; {@code PENDING} aparece em bloco
 * separado, sem total líquido; {@code currentBalance} só soma contas {@code BANK}/{@code
 * CASH}/{@code WALLET} ativas e cartões aparecem em {@code cardOutstandingBalance}.
 */
@Service
public class ReportingService {

  /** Categoria sintética para lançamentos sem categoria. */
  public static final UUID UNCATEGORIZED_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  public static final String UNCATEGORIZED_NAME = "Sem categoria";

  private static final int MAX_RANGE_DAYS = 366;
  private static final List<TransactionStatus> EFFECTIVE_STATUSES =
      List.of(TransactionStatus.POSTED, TransactionStatus.RECONCILED);
  private static final List<TransactionStatus> PENDING_STATUSES =
      List.of(TransactionStatus.PENDING);
  private static final Set<String> BALANCE_TYPES = Set.of("BANK", "CASH", "WALLET");
  private static final DateTimeFormatter DAY_LABEL =
      DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"));
  private static final DateTimeFormatter MONTH_LABEL =
      DateTimeFormatter.ofPattern("MMM/yyyy", Locale.forLanguageTag("pt-BR"));

  private final AccountQueryService accountQueryService;
  private final TransactionQueryService transactionQueryService;
  private final InvoiceReportingService invoiceReportingService;
  private final CategoryService categoryService;
  private final SyncQueryService syncQueryService;
  private final InternalMatchService internalMatchService;
  private final Clock clock;

  public ReportingService(
      AccountQueryService accountQueryService,
      TransactionQueryService transactionQueryService,
      InvoiceReportingService invoiceReportingService,
      CategoryService categoryService,
      SyncQueryService syncQueryService,
      InternalMatchService internalMatchService,
      Clock clock) {
    this.accountQueryService = accountQueryService;
    this.transactionQueryService = transactionQueryService;
    this.invoiceReportingService = invoiceReportingService;
    this.categoryService = categoryService;
    this.syncQueryService = syncQueryService;
    this.internalMatchService = internalMatchService;
    this.clock = clock;
  }

  public DashboardSummaryView summary(UUID userId, ReportQuery query) {
    Resolved resolved = resolve(userId, query);
    List<ReportEntry> entries = entries(userId, resolved);
    Map<UUID, CategoryAggregate> categories = categoriesOf(entries);
    BigDecimal income = incomeOf(entries);
    BigDecimal expenses = expensesOf(categories);
    BigDecimal periodResult = income.subtract(expenses);
    Pending pending = pending(userId, resolved);
    Balance balance = currentBalance(resolved);
    BigDecimal cardOutstanding = cardOutstanding(userId, resolved);

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
        MoneyAggregator.format(cardOutstanding));
  }

  public List<DashboardCategoryView> categories(UUID userId, ReportQuery query) {
    Resolved resolved = resolve(userId, query);
    Map<UUID, CategoryAggregate> aggregates = categoriesOf(entries(userId, resolved));
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
    Resolved resolved = resolve(userId, query);
    List<ReportEntry> entries = entries(userId, resolved);
    List<Bucket> buckets = buckets(resolved);
    Map<LocalDate, BucketAggregate> aggregates = new HashMap<>();
    for (ReportEntry entry : entries) {
      LocalDate date = bucketStartFor(entry.occurredOn(), resolved.period());
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

  /** Entradas que compõem os totais, na mesma projeção usada pelo drill-down. */
  public List<ReportEntry> entries(UUID userId, ReportQuery query) {
    return entries(userId, resolve(userId, query));
  }

  /** Limite restante de redução de despesa para um estorno/chargeback vinculado (FR-057). */
  private BigDecimal remainingReduction(UUID userId, UUID originalId, LocalDate from) {
    TransactionView original;
    try {
      original = transactionQueryService.get(userId, originalId);
    } catch (NotFoundException exception) {
      return null;
    }
    BigDecimal originalAmount = MoneyAggregator.parse(original.amount()).abs();
    BigDecimal prior =
        transactionQueryService
            .listLinkedReductionsBefore(userId, originalId, from, EFFECTIVE_STATUSES)
            .stream()
            .map(view -> MoneyAggregator.parse(view.amount()).abs())
            .reduce(MoneyAggregator.zero(), MoneyAggregator::sum);
    return originalAmount.subtract(prior).max(MoneyAggregator.zero());
  }

  private List<ReportEntry> entries(UUID userId, Resolved resolved) {
    List<ReportEntry> entries = new ArrayList<>();
    List<InvoiceReportItem> decreases = new ArrayList<>();
    Map<UUID, BigDecimal> remainingReductions = new HashMap<>();
    Set<UUID> creditCardAccounts = new HashSet<>();
    for (AccountView account : resolved.accounts()) {
      if ("CREDIT".equals(account.type())) {
        creditCardAccounts.add(account.id());
      }
    }
    Set<UUID> installmentTransactionIds = invoiceReportingService.installmentTransactionIds(userId);

    UUID effectiveCategoryId =
        ReportingService.UNCATEGORIZED_ID.equals(resolved.categoryId())
            ? null
            : resolved.categoryId();

    for (TransactionView transaction :
        transactionQueryService.listForReporting(
            userId,
            resolved.from(),
            resolved.toExclusive(),
            singleAccountId(resolved),
            effectiveCategoryId,
            resolved.type(),
            resolved.origin(),
            EFFECTIVE_STATUSES)) {
      if (!isTransactionIncluded(transaction, resolved)) {
        continue;
      }
      if (installmentTransactionIds.contains(transaction.id())) {
        // Compra parcelada: cada parcela é contabilizada no seu próprio ciclo.
        continue;
      }
      BigDecimal amount = MoneyAggregator.parse(transaction.amount());
      if (amount.signum() == 0) {
        continue;
      }
      Direction direction;
      BigDecimal reportAmount;
      if ("REFUND".equals(transaction.type()) || "CHARGEBACK".equals(transaction.type())) {
        UUID relatedId = transaction.relatedTransactionId();
        if (relatedId == null) {
          // ADR-0001: reembolso sem vínculo não reduz despesa nem vira receita.
          continue;
        }
        direction = Direction.EXPENSE_REDUCTION;
        reportAmount = amount.abs();
        BigDecimal remaining = remainingReductions.get(relatedId);
        if (remaining == null && !remainingReductions.containsKey(relatedId)) {
          remaining = remainingReduction(userId, relatedId, resolved.from());
          if (remaining != null) {
            remainingReductions.put(relatedId, remaining);
          }
        }
        if (remaining != null) {
          if (remaining.signum() <= 0) {
            // Excedente ao valor efetivo do original não reduz despesas novamente (FR-057).
            continue;
          }
          BigDecimal applied = reportAmount.min(remaining).setScale(Money.SCALE);
          remainingReductions.put(relatedId, remaining.subtract(applied));
          reportAmount = applied;
        }
      } else if (amount.signum() > 0) {
        // Entrada em cartão é crédito/estorno: reduz despesa e nunca vira receita.
        direction =
            creditCardAccounts.contains(transaction.accountId())
                ? Direction.EXPENSE_REDUCTION
                : Direction.INCOME;
        reportAmount = amount;
      } else {
        direction = Direction.EXPENSE;
        reportAmount = amount;
      }
      entries.add(
          new ReportEntry(
              transaction.id(),
              "TRANSACTION",
              transaction.id(),
              transaction.source(),
              transaction.postedOn(),
              reportAmount,
              transaction.description() == null ? "" : transaction.description(),
              transaction.categoryId(),
              transaction.accountId(),
              transaction.invoiceId(),
              transaction.id(),
              transaction.type(),
              direction));
    }

    entries.addAll(
        installmentEntries(userId, resolved, effectiveCategoryId, installmentTransactionIds));

    boolean includesExpenseIncrease =
        resolved.type() == null || resolved.type() == TransactionType.DEBIT;
    boolean includesExpenseDecrease =
        resolved.type() == null
            || resolved.type() == TransactionType.REFUND
            || resolved.type() == TransactionType.CHARGEBACK;
    List<InvoiceReportItem> invoiceItems =
        invoiceReportingService.unlinkedItems(
            userId, resolved.from(), resolved.toExclusive(), singleAccountId(resolved));
    for (InvoiceReportItem item : invoiceItems) {
      if (resolved.categoryId() != null
          && !ReportingService.UNCATEGORIZED_ID.equals(resolved.categoryId())) {
        // Itens de fatura não carregam categoria própria; só aparecem sem filtro ou em
        // "Sem categoria".
        continue;
      }
      if (!accountIncluded(resolved, item.cardAccountId())) {
        continue;
      }
      String origin = normalizedInvoiceOrigin(item.source());
      if (resolved.origin() != null && !resolved.origin().name().equals(origin)) {
        continue;
      }
      if (!accountCurrencyIsBrl(resolved, item.cardAccountId())) {
        continue;
      }
      if (!MoneyAggregator.withinRange(item.amount(), resolved.minAmount(), resolved.maxAmount())) {
        continue;
      }
      if ("EXPENSE_INCREASE".equals(item.reportingEffect())) {
        if (!includesExpenseIncrease) {
          continue;
        }
        entries.add(invoiceEntry(item, origin, item.amount().negate(), Direction.EXPENSE));
      } else if ("EXPENSE_DECREASE".equals(item.reportingEffect())) {
        if (includesExpenseDecrease) {
          decreases.add(item);
        }
      }
    }

    Map<UUID, BigDecimal> grossExpensesByAccount =
        decreases.isEmpty()
            ? Map.of()
            : grossCardExpenses(userId, resolved, invoiceItems, installmentTransactionIds);
    for (InvoiceReportItem item : decreases) {
      BigDecimal cardExpenses =
          grossExpensesByAccount.getOrDefault(item.cardAccountId(), BigDecimal.ZERO);
      BigDecimal effective =
          item.amount()
              .min(item.invoiceTotalBeforeItem().max(BigDecimal.ZERO))
              .min(cardExpenses)
              .max(BigDecimal.ZERO);
      if (effective.signum() <= 0) {
        continue;
      }
      entries.add(
          invoiceEntry(
              item,
              normalizedInvoiceOrigin(item.source()),
              effective,
              Direction.EXPENSE_REDUCTION));
    }

    entries.sort(Comparator.comparing(ReportEntry::occurredOn).thenComparing(ReportEntry::id));
    return entries;
  }

  /**
   * Despesas brutas por cartão no período, independentes dos filtros de origem, tipo, categoria e
   * faixa de valor. É o limite de FR-077 para itens de redução de despesa.
   */
  private Map<UUID, BigDecimal> grossCardExpenses(
      UUID userId,
      Resolved resolved,
      List<InvoiceReportItem> invoiceItems,
      Set<UUID> installmentTransactionIds) {
    Map<UUID, BigDecimal> gross = new HashMap<>();
    for (TransactionView transaction :
        transactionQueryService.listForReporting(
            userId,
            resolved.from(),
            resolved.toExclusive(),
            singleAccountId(resolved),
            null,
            null,
            null,
            EFFECTIVE_STATUSES)) {
      if (!accountIncluded(resolved, transaction.accountId())
          || !"BRL".equalsIgnoreCase(transaction.currency())
          || "TRANSFER".equals(transaction.type())
          || (transaction.categoryId() != null
              && resolved.transferCategoryIds().contains(transaction.categoryId()))
          || "MATCHED".equals(transaction.reconciliationState())
          || installmentTransactionIds.contains(transaction.id())
          || !isCreditAccount(resolved, transaction.accountId())) {
        continue;
      }
      BigDecimal amount = MoneyAggregator.parse(transaction.amount());
      if (amount.signum() < 0) {
        gross.merge(transaction.accountId(), amount.abs(), BigDecimal::add);
      }
    }
    for (InvoiceReportItem item : invoiceItems) {
      if ("EXPENSE_INCREASE".equals(item.reportingEffect())
          && isCreditAccount(resolved, item.cardAccountId())) {
        gross.merge(item.cardAccountId(), item.amount(), BigDecimal::add);
      }
    }
    if (!installmentTransactionIds.isEmpty()) {
      for (InvoiceReportItem item :
          invoiceReportingService.installmentItems(
              userId, resolved.from(), resolved.toExclusive(), singleAccountId(resolved))) {
        if (isCreditAccount(resolved, item.cardAccountId())) {
          gross.merge(item.cardAccountId(), item.amount(), BigDecimal::add);
        }
      }
    }
    return gross;
  }

  private static boolean accountIncluded(Resolved resolved, UUID accountId) {
    return resolved.accountIds().isEmpty() || resolved.accountIds().contains(accountId);
  }

  /** O repositório ainda filtra por uma conta; para múltiplas, o recorte é feito em memória. */
  private static UUID singleAccountId(Resolved resolved) {
    return resolved.accountIds().size() == 1 ? resolved.accountIds().iterator().next() : null;
  }

  private boolean isCreditAccount(Resolved resolved, UUID accountId) {
    for (AccountView account : resolved.accounts()) {
      if (account.id().equals(accountId)) {
        return "CREDIT".equals(account.type());
      }
    }
    return false;
  }

  private List<ReportEntry> installmentEntries(
      UUID userId,
      Resolved resolved,
      UUID effectiveCategoryId,
      Set<UUID> installmentTransactionIds) {
    if (installmentTransactionIds.isEmpty()) {
      return List.of();
    }
    List<InvoiceReportItem> items =
        invoiceReportingService.installmentItems(
            userId, resolved.from(), resolved.toExclusive(), singleAccountId(resolved));
    if (items.isEmpty()) {
      return List.of();
    }
    Map<UUID, TransactionView> parents = new HashMap<>();
    List<ReportEntry> result = new ArrayList<>();
    for (InvoiceReportItem item : items) {
      if (!accountIncluded(resolved, item.cardAccountId())) {
        continue;
      }
      if (!accountCurrencyIsBrl(resolved, item.cardAccountId())) {
        continue;
      }
      if (!moneyRange(item.amount(), resolved.minAmount(), resolved.maxAmount())) {
        continue;
      }
      TransactionView parent = parents.get(item.transactionId());
      if (parent == null) {
        try {
          parent = transactionQueryService.get(userId, item.transactionId());
        } catch (NotFoundException exception) {
          continue;
        }
        parents.put(item.transactionId(), parent);
      }
      if (resolved.categoryId() != null
          && !java.util.Objects.equals(parent.categoryId(), effectiveCategoryId)) {
        continue;
      }
      if (resolved.type() != null && !resolved.type().name().equals(parent.type())) {
        continue;
      }
      String origin = normalizedInvoiceOrigin(item.source());
      if (resolved.origin() != null && !resolved.origin().name().equals(origin)) {
        continue;
      }

      Direction direction;
      BigDecimal amount;
      if ("PURCHASE".equals(item.kind()) || "INSTALLMENT".equals(item.kind())) {
        direction = Direction.EXPENSE;
        amount = item.amount().negate();
      } else if ("CREDIT".equals(item.kind()) || "REFUND".equals(item.kind())) {
        direction = Direction.EXPENSE_REDUCTION;
        amount = item.amount();
      } else if ("EXPENSE_INCREASE".equals(item.reportingEffect())) {
        direction = Direction.EXPENSE;
        amount = item.amount().negate();
      } else if ("EXPENSE_DECREASE".equals(item.reportingEffect())) {
        direction = Direction.EXPENSE_REDUCTION;
        amount = item.amount();
      } else {
        continue;
      }
      result.add(
          new ReportEntry(
              item.itemId(),
              "INVOICE_ITEM",
              item.itemId(),
              origin,
              item.occurredOn(),
              amount,
              item.description() == null ? "" : item.description(),
              parent.categoryId(),
              item.cardAccountId(),
              item.invoiceId(),
              parent.id(),
              parent.type(),
              direction));
    }
    return result;
  }

  private static boolean moneyRange(BigDecimal value, BigDecimal min, BigDecimal max) {
    BigDecimal magnitude = value.abs();
    if (min != null && magnitude.compareTo(min.abs()) < 0) {
      return false;
    }
    return max == null || magnitude.compareTo(max.abs()) <= 0;
  }

  private ReportEntry invoiceEntry(
      InvoiceReportItem item, String origin, BigDecimal amount, Direction direction) {
    return new ReportEntry(
        item.itemId(),
        "INVOICE_ITEM",
        item.itemId(),
        origin,
        item.occurredOn(),
        amount,
        item.description() == null ? "" : item.description(),
        null,
        item.cardAccountId(),
        item.invoiceId(),
        null,
        item.kind(),
        direction);
  }

  private boolean isTransactionIncluded(TransactionView transaction, Resolved resolved) {
    if (!accountIncluded(resolved, transaction.accountId())) {
      return false;
    }
    if (!"BRL".equalsIgnoreCase(transaction.currency())) {
      return false;
    }
    if ("TRANSFER".equals(transaction.type())) {
      return false;
    }
    if (transaction.categoryId() != null
        && resolved.transferCategoryIds().contains(transaction.categoryId())) {
      return false;
    }
    if ("MATCHED".equals(transaction.reconciliationState())) {
      return false;
    }
    if (ReportingService.UNCATEGORIZED_ID.equals(resolved.categoryId())
        && transaction.categoryId() != null) {
      return false;
    }
    return MoneyAggregator.withinRange(
        MoneyAggregator.parse(transaction.amount()), resolved.minAmount(), resolved.maxAmount());
  }

  private static String normalizedInvoiceOrigin(String source) {
    return "MANUAL".equals(source) ? "MANUAL" : "IMPORTED";
  }

  private boolean accountCurrencyIsBrl(Resolved resolved, UUID accountId) {
    for (AccountView account : resolved.accounts()) {
      if (account.id().equals(accountId)) {
        return "BRL".equalsIgnoreCase(account.currency());
      }
    }
    return false;
  }

  private Map<UUID, CategoryAggregate> categoriesOf(List<ReportEntry> entries) {
    Map<UUID, CategoryAggregate> aggregates = new LinkedHashMap<>();
    for (ReportEntry entry : entries) {
      if (entry.direction() == Direction.INCOME) {
        continue;
      }
      UUID key = entry.categoryId() == null ? UNCATEGORIZED_ID : entry.categoryId();
      CategoryAggregate aggregate =
          aggregates.computeIfAbsent(key, ignored -> new CategoryAggregate());
      if (entry.direction() == Direction.EXPENSE) {
        aggregate.amount = aggregate.amount.add(entry.amount().abs());
      } else {
        aggregate.amount = aggregate.amount.subtract(entry.amount().abs());
      }
      aggregate.count++;
    }
    // Nenhuma categoria pode exibir despesa negativa: a redução é limitada ao que entrou nela.
    for (CategoryAggregate aggregate : aggregates.values()) {
      aggregate.amount = aggregate.amount.max(BigDecimal.ZERO);
    }
    return aggregates;
  }

  private static BigDecimal incomeOf(List<ReportEntry> entries) {
    BigDecimal income = BigDecimal.ZERO;
    for (ReportEntry entry : entries) {
      if (entry.direction() == Direction.INCOME) {
        income = income.add(entry.amount());
      }
    }
    return income.max(BigDecimal.ZERO);
  }

  private static BigDecimal expensesOf(Map<UUID, CategoryAggregate> categories) {
    BigDecimal expenses = BigDecimal.ZERO;
    for (CategoryAggregate aggregate : categories.values()) {
      expenses = expenses.add(aggregate.amount);
    }
    return expenses.max(BigDecimal.ZERO);
  }

  private Pending pending(UUID userId, Resolved resolved) {
    UUID effectiveCategoryId =
        ReportingService.UNCATEGORIZED_ID.equals(resolved.categoryId())
            ? null
            : resolved.categoryId();
    int count = 0;
    BigDecimal income = BigDecimal.ZERO;
    BigDecimal expenses = BigDecimal.ZERO;
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
      if (!isTransactionIncluded(transaction, resolved)) {
        continue;
      }
      BigDecimal amount = MoneyAggregator.parse(transaction.amount());
      if (amount.signum() == 0) {
        continue;
      }
      count++;
      if (amount.signum() > 0) {
        income = income.add(amount);
      } else {
        expenses = expenses.add(amount.abs());
      }
    }
    return new Pending(count, income, expenses);
  }

  private Balance currentBalance(Resolved resolved) {
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

  private BigDecimal cardOutstanding(UUID userId, Resolved resolved) {
    Set<UUID> cardIds = new HashSet<>();
    for (AccountView account : resolved.accounts()) {
      if (!"CREDIT".equals(account.type())) {
        continue;
      }
      if (!accountIncluded(resolved, account.id())) {
        continue;
      }
      cardIds.add(account.id());
    }
    return invoiceReportingService.outstandingBalance(userId, cardIds);
  }

  private Instant lastSuccessfulSync(UUID userId, Resolved resolved) {
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

  private Resolved resolve(UUID userId, ReportQuery query) {
    ReportPeriod period = parsePeriod(query.period());
    LocalDate today = clock.today();
    LocalDate from = query.from();
    LocalDate to = query.to();
    if (from == null && to == null) {
      from = startOf(period, today);
      to = endOf(period, today);
    } else if (from == null) {
      from = startOf(period, to);
    } else if (to == null) {
      to = endOf(period, from);
    }
    if (from.isAfter(to)) {
      throw new ValidationException("from", "A data inicial deve ser anterior à data final.");
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    if (days > MAX_RANGE_DAYS) {
      throw new ValidationException(
          "from",
          "O intervalo máximo é de " + MAX_RANGE_DAYS + " dias. Estreite o filtro para continuar.");
    }

    List<AccountView> accounts = accountQueryService.list(userId);
    Set<UUID> accountIds =
        query.accountIds() == null
            ? Set.of()
            : query.accountIds().stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    for (UUID accountId : accountIds) {
      if (accounts.stream().noneMatch(account -> account.id().equals(accountId))) {
        throw new NotFoundException();
      }
    }

    Map<UUID, String> categoryNames = new HashMap<>();
    Set<UUID> transferCategoryIds = new HashSet<>();
    for (CategoryView category : categoryService.listExisting(userId)) {
      categoryNames.put(category.id(), category.name());
      if ("TRANSFER".equals(category.kind())) {
        // Categorias do tipo transferência (inclusive as criadas a partir de "Same person
        // transfer" e "Transfer - Internal" do provedor) não compõem receitas nem despesas.
        transferCategoryIds.add(category.id());
      }
    }
    if (query.categoryId() != null
        && !UNCATEGORIZED_ID.equals(query.categoryId())
        && !categoryNames.containsKey(query.categoryId())) {
      throw new NotFoundException();
    }

    TransactionType type = parseEnum(TransactionType.class, query.type(), "type");
    TransactionSource origin = parseEnum(TransactionSource.class, query.origin(), "origin");
    BigDecimal minAmount = parseAmount(query.minAmount(), "minAmount");
    BigDecimal maxAmount = parseAmount(query.maxAmount(), "maxAmount");
    if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
      throw new ValidationException(
          "minAmount", "O valor mínimo não pode ser maior que o valor máximo.");
    }

    return new Resolved(
        from,
        to,
        to.plusDays(1),
        period,
        accountIds,
        query.categoryId(),
        type,
        origin,
        minAmount,
        maxAmount,
        accounts,
        categoryNames,
        transferCategoryIds,
        accountQueryService.connectionIdsByAccount(userId));
  }

  private String categoryName(Resolved resolved, UUID categoryId) {
    if (UNCATEGORIZED_ID.equals(categoryId)) {
      return UNCATEGORIZED_NAME;
    }
    return resolved.categoryNames().getOrDefault(categoryId, UNCATEGORIZED_NAME);
  }

  private List<Bucket> buckets(Resolved resolved) {
    List<Bucket> buckets = new ArrayList<>();
    if (resolved.period() == ReportPeriod.YEAR) {
      LocalDate cursor = resolved.from().with(TemporalAdjusters.firstDayOfMonth());
      LocalDate last = resolved.to().with(TemporalAdjusters.firstDayOfMonth());
      while (!cursor.isAfter(last)) {
        LocalDate end = cursor.plusMonths(1);
        buckets.add(new Bucket(cursor.format(MONTH_LABEL), cursor, end));
        cursor = end;
      }
      return buckets;
    }
    LocalDate cursor = resolved.from();
    while (!cursor.isAfter(resolved.to())) {
      LocalDate end = cursor.plusDays(1);
      buckets.add(new Bucket(cursor.format(DAY_LABEL), cursor, end));
      cursor = end;
    }
    return buckets;
  }

  private LocalDate bucketStartFor(LocalDate date, ReportPeriod period) {
    if (period == ReportPeriod.YEAR) {
      return date.withDayOfMonth(1);
    }
    return date;
  }

  private static LocalDate startOf(ReportPeriod period, LocalDate date) {
    return switch (period) {
      case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTH -> date.withDayOfMonth(1);
      case YEAR -> date.withDayOfYear(1);
    };
  }

  private static LocalDate endOf(ReportPeriod period, LocalDate date) {
    return switch (period) {
      case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6);
      case MONTH -> date.with(TemporalAdjusters.lastDayOfMonth());
      case YEAR -> date.with(TemporalAdjusters.lastDayOfYear());
    };
  }

  private static ReportPeriod parsePeriod(String value) {
    if (value == null || value.isBlank()) {
      return ReportPeriod.MONTH;
    }
    try {
      return ReportPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("period", "Período inválido. Use WEEK, MONTH ou YEAR.");
    }
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(field, "Valor inválido para " + field + ".");
    }
  }

  private static BigDecimal parseAmount(String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MoneyAggregator.parse(value);
    } catch (RuntimeException exception) {
      throw new ValidationException(field, "Valor decimal inválido.");
    }
  }

  private record Resolved(
      LocalDate from,
      LocalDate to,
      LocalDate toExclusive,
      ReportPeriod period,
      Set<UUID> accountIds,
      UUID categoryId,
      TransactionType type,
      TransactionSource origin,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      List<AccountView> accounts,
      Map<UUID, String> categoryNames,
      Set<UUID> transferCategoryIds,
      Map<UUID, UUID> connectionIdsByAccount) {}

  private enum ReportPeriod {
    WEEK,
    MONTH,
    YEAR
  }

  private record Bucket(String label, LocalDate start, LocalDate end) {}

  private static final class CategoryAggregate {
    private BigDecimal amount = BigDecimal.ZERO;
    private int count;
  }

  private static final class BucketAggregate {
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal grossExpenses = BigDecimal.ZERO;
    private BigDecimal expenseReductions = BigDecimal.ZERO;
  }

  private record Pending(int count, BigDecimal income, BigDecimal expenses) {}

  private record Balance(BigDecimal amount, Instant referenceAt) {}
}
