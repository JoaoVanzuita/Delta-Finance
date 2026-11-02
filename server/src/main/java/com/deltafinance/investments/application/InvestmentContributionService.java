package com.deltafinance.investments.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.categories.application.CategoryTaxonomy;
import com.deltafinance.categories.application.CategoryView;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.investments.domain.ProviderInvestment;
import com.deltafinance.investments.domain.ProviderInvestmentRepository;
import com.deltafinance.investments.domain.ProviderInvestmentTransaction;
import com.deltafinance.investments.domain.ProviderInvestmentTransactionRepository;
import com.deltafinance.reconciliation.domain.InternalMatch;
import com.deltafinance.reconciliation.domain.InternalMatchRepository;
import com.deltafinance.reconciliation.domain.InternalMatchStatus;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.TransactionQueryService;
import com.deltafinance.transactions.application.TransactionView;
import com.deltafinance.transactions.domain.TransactionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Relatório de aportes e resgates do perímetro de investimento.
 *
 * <p>A fronteira de caixa é a conta marcada como {@code investment_account}. Créditos nessa conta
 * vindos de fora são aportes; débitos para fora são resgates; débitos de liquidação de corretagem
 * são aplicações e não resgates. As compras/vendas vêm de {@code provider_investment_transaction}.
 *
 * <p>O relatório nunca soma aporte e aplicação como o mesmo evento: o aporte é a entrada de caixa;
 * a aplicação é o uso desse caixa. Custos são a diferença entre o débito de corretagem e a soma das
 * compras do mesmo período, quando o provedor não informa {@code expenses}.
 */
@Service
public class InvestmentContributionService {

  private static final String INVESTMENT_TABLE = "provider_investment";
  private static final String TRANSACTION_TABLE = "provider_investment_transaction";
  private static final BigDecimal MAX_FEE_RATIO = new BigDecimal("0.20");
  private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
  private static final int MAX_RANGE_DAYS = 366;

  private final ProviderInvestmentRepository investmentRepository;
  private final ProviderInvestmentTransactionRepository transactionRepository;
  private final TransactionQueryService transactionQueryService;
  private final InternalMatchRepository matchRepository;
  private final AccountQueryService accountQueryService;
  private final CategoryService categoryService;
  private final InvestmentQueryService investmentQueryService;
  private final EncryptedValueReader encryptedValueReader;
  private final Clock clock;

  public InvestmentContributionService(
      ProviderInvestmentRepository investmentRepository,
      ProviderInvestmentTransactionRepository transactionRepository,
      TransactionQueryService transactionQueryService,
      InternalMatchRepository matchRepository,
      AccountQueryService accountQueryService,
      CategoryService categoryService,
      InvestmentQueryService investmentQueryService,
      EncryptedValueReader encryptedValueReader,
      Clock clock) {
    this.investmentRepository = investmentRepository;
    this.transactionRepository = transactionRepository;
    this.transactionQueryService = transactionQueryService;
    this.matchRepository = matchRepository;
    this.accountQueryService = accountQueryService;
    this.categoryService = categoryService;
    this.investmentQueryService = investmentQueryService;
    this.encryptedValueReader = encryptedValueReader;
    this.clock = clock;
  }

  public InvestmentContributionView contributions(
      UUID userId, LocalDate from, LocalDate to, UUID connectionId) {
    LocalDate today = clock.today();
    Period period = period(from, to, today);

    List<AccountView> accounts = accountQueryService.list(userId);
    Map<UUID, AccountView> accountsById = new HashMap<>();
    for (AccountView account : accounts) {
      accountsById.put(account.id(), account);
    }
    Map<UUID, UUID> connectionIdsByAccount = accountQueryService.connectionIdsByAccount(userId);

    List<ProviderInvestment> investments =
        investmentRepository.findByUserIdOrderByStatusAscTypeAscIdAsc(userId);
    Set<UUID> investmentConnections = new LinkedHashSet<>();
    Map<UUID, ProviderInvestment> investmentsById = new HashMap<>();
    for (ProviderInvestment investment : investments) {
      investmentConnections.add(investment.getConnectionId());
      investmentsById.put(investment.getId(), investment);
    }
    if (connectionId != null && !investmentConnections.contains(connectionId)) {
      throw new NotFoundException();
    }
    Set<UUID> selectedConnections =
        connectionId == null ? investmentConnections : Set.of(connectionId);

    Map<UUID, UUID> perimeter = new LinkedHashMap<>();
    Map<UUID, String> perimeterSources = new HashMap<>();
    for (UUID selected : selectedConnections) {
      List<AccountView> connectionAccounts =
          accounts.stream()
              .filter(account -> selected.equals(connectionIdsByAccount.get(account.id())))
              .filter(account -> !"CREDIT".equals(account.type()))
              .toList();
      List<AccountView> flagged =
          connectionAccounts.stream().filter(AccountView::investmentAccount).toList();
      if (!flagged.isEmpty()) {
        perimeterSources.put(selected, "ACCOUNT_FLAG");
      } else {
        // Sem conta marcada não há como saber qual saldo é caixa de investimento. Não infira
        // todas as contas: isso transformaria salário em aporte e débito em resgate.
        perimeterSources.put(selected, "NOT_CONFIGURED");
      }
      for (AccountView account : flagged) {
        perimeter.put(account.id(), selected);
      }
    }

    boolean trackingAvailable =
        !selectedConnections.isEmpty()
            && selectedConnections.stream()
                .noneMatch(id -> "NOT_CONFIGURED".equals(perimeterSources.get(id)));

    Map<UUID, CategoryKind> categoryKinds = categoryKinds(userId);
    Map<UUID, InternalMatch> matches = matches(userId);

    BankFlows bank =
        bankFlows(
            userId, period.from(), period.toExclusive(), perimeter, categoryKinds, matches, true);
    ProviderFlows provider =
        providerFlows(userId, period.from(), period.to(), investmentsById, selectedConnections);
    BankFlows cumulative =
        bankFlows(userId, EARLIEST, period.toExclusive(), perimeter, categoryKinds, matches, false);

    Map<YearMonth, MonthAccumulator> months = new TreeMap<>();
    bank.addTo(months);
    provider.addTo(months);

    BigDecimal totalFees = BigDecimal.ZERO;
    boolean providerFee = false;
    boolean derivedFee = false;
    for (Map.Entry<YearMonth, MonthAccumulator> entry : months.entrySet()) {
      MonthAccumulator month = entry.getValue();
      BigDecimal fees = feeForMonth(month, provider.expensesByMonth().get(entry.getKey()));
      month.fees = fees;
      if (fees.signum() > 0) {
        if (provider.expensesByMonth().containsKey(entry.getKey())) {
          providerFee = true;
        } else {
          derivedFee = true;
        }
      }
      totalFees = totalFees.add(fees);
    }

    BigDecimal idleCash =
        trackingAvailable
            ? bank.contributions()
                .subtract(bank.withdrawals())
                .subtract(provider.applied())
                .add(provider.redeemed())
                .add(provider.income())
                .subtract(totalFees)
            : BigDecimal.ZERO;

    List<InvestmentHoldingView> active =
        investmentQueryService.holdings(userId, "ACTIVE", null).stream()
            .filter(holding -> selectedConnections.contains(holding.connectionId()))
            .filter(holding -> "BRL".equalsIgnoreCase(holding.currency()))
            .toList();
    BigDecimal portfolioValue =
        active.stream()
            .map(InvestmentContributionService::decimal)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal cashBalance =
        perimeter.keySet().stream()
            .map(accountsById::get)
            .filter(account -> account != null && "BRL".equalsIgnoreCase(account.currency()))
            .map(account -> decimal(account.currentBalance()))
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal cumulativeNet = cumulative.contributions().subtract(cumulative.withdrawals());
    BigDecimal result = null;
    String resultPercentage = null;
    if (!period.to().isBefore(today) && cumulativeNet.signum() > 0) {
      result = Money.scaled(portfolioValue.add(cashBalance).subtract(cumulativeNet));
      resultPercentage =
          format(
              result
                  .multiply(BigDecimal.valueOf(100))
                  .divide(cumulativeNet, 2, RoundingMode.HALF_EVEN));
    }

    BigDecimal reconciliationDifference = null;
    if (bank.settlementDebits().signum() > 0) {
      reconciliationDifference =
          Money.scaled(bank.settlementDebits().subtract(provider.applied()).subtract(totalFees));
    }

    List<InvestmentContributionView.Flow> flows = new ArrayList<>();
    flows.addAll(bank.flows());
    flows.addAll(provider.flows());
    flows.sort(
        Comparator.comparing(InvestmentContributionView.Flow::date)
            .reversed()
            .thenComparing(InvestmentContributionView.Flow::kind)
            .thenComparing(flow -> flow.description() == null ? "" : flow.description()));

    String feeSource = providerFee ? "PROVIDER" : derivedFee ? "DERIVED" : "UNAVAILABLE";

    return new InvestmentContributionView(
        period.from(),
        period.to(),
        "BRL",
        format(bank.contributions()),
        format(bank.withdrawals()),
        format(bank.contributions().subtract(bank.withdrawals())),
        format(provider.applied()),
        format(provider.redeemed()),
        format(provider.income()),
        format(totalFees),
        format(idleCash),
        format(portfolioValue),
        format(cashBalance),
        format(bank.settlementDebits()),
        result == null ? null : format(result),
        resultPercentage,
        format(cumulative.contributions()),
        format(cumulative.withdrawals()),
        feeSource,
        trackingAvailable,
        reconciliationDifference == null ? null : format(reconciliationDifference),
        bank.contributionCount(),
        bank.withdrawalCount(),
        provider.applicationCount(),
        active.size(),
        months.entrySet().stream().map(entry -> entry.getValue().toView(entry.getKey())).toList(),
        flows,
        sources(selectedConnections, perimeter, perimeterSources, accountsById, investmentsById));
  }

  private BankFlows bankFlows(
      UUID userId,
      LocalDate from,
      LocalDate toExclusive,
      Map<UUID, UUID> perimeter,
      Map<UUID, CategoryKind> categoryKinds,
      Map<UUID, InternalMatch> matches,
      boolean collectFlows) {
    BigDecimal contributions = BigDecimal.ZERO;
    BigDecimal withdrawals = BigDecimal.ZERO;
    BigDecimal settlementDebits = BigDecimal.ZERO;
    int contributionCount = 0;
    int withdrawalCount = 0;
    List<InvestmentContributionView.Flow> flows = new ArrayList<>();
    Map<YearMonth, BigDecimal> contributionsByMonth = new HashMap<>();
    Map<YearMonth, BigDecimal> withdrawalsByMonth = new HashMap<>();
    Map<YearMonth, BigDecimal> settlementByMonth = new HashMap<>();
    Set<UUID> processedInternal = new HashSet<>();

    for (TransactionView transaction :
        transactionQueryService.listForReporting(
            userId,
            from,
            toExclusive,
            null,
            null,
            null,
            null,
            List.of(TransactionStatus.POSTED, TransactionStatus.RECONCILED))) {
      UUID connectionId = perimeter.get(transaction.accountId());
      if (connectionId == null || !"BRL".equalsIgnoreCase(transaction.currency())) {
        continue;
      }
      BigDecimal amount = decimal(transaction.amount());
      if (amount == null) {
        continue;
      }
      if ("REFUND".equals(transaction.type()) || "CHARGEBACK".equals(transaction.type())) {
        // Estorno reduz despesa; não é aporte nem resgate do perímetro.
        continue;
      }
      YearMonth month = YearMonth.from(transaction.postedOn());
      InternalMatch match = matches.get(transaction.id());
      if (match != null && isInternal(match, transaction, perimeter)) {
        if (collectFlows
            && transaction.id().equals(match.getDebitTransactionId())
            && processedInternal.add(match.getId())) {
          flows.add(
              new InvestmentContributionView.Flow(
                  transaction.postedOn(),
                  "INTERNAL_TRANSFER",
                  format(amount.abs()),
                  "Transferência interna entre contas de investimento",
                  transaction.accountId(),
                  connectionId,
                  transaction.id(),
                  null,
                  null,
                  null,
                  "BANK",
                  matchStatus(match)));
        }
        continue;
      }
      CategoryKind kind = categoryKinds.get(transaction.categoryId());
      if (amount.signum() > 0) {
        // Proventos bancários já aparecem no Dashboard; no relatório a fonte é o provedor
        // (INTEREST), evitando dupla contagem entre as duas trilhas.
        if (kind == CategoryKind.INCOME) {
          continue;
        }
        contributions = contributions.add(amount);
        contributionsByMonth.merge(month, amount, BigDecimal::add);
        contributionCount++;
        if (collectFlows) {
          flows.add(
              new InvestmentContributionView.Flow(
                  transaction.postedOn(),
                  "CONTRIBUTION",
                  format(amount),
                  transaction.description(),
                  transaction.accountId(),
                  connectionId,
                  transaction.id(),
                  null,
                  null,
                  null,
                  "BANK",
                  match == null ? "SUGGESTED" : matchStatus(match)));
        }
      } else if (CategoryTaxonomy.isBrokerageSettlementDescription(transaction.description())) {
        BigDecimal value = amount.abs();
        settlementDebits = settlementDebits.add(value);
        settlementByMonth.merge(month, value, BigDecimal::add);
        // A aplicação correspondente vem das movimentações BUY do provedor.
      } else {
        BigDecimal value = amount.abs();
        withdrawals = withdrawals.add(value);
        withdrawalsByMonth.merge(month, value, BigDecimal::add);
        withdrawalCount++;
        if (collectFlows) {
          flows.add(
              new InvestmentContributionView.Flow(
                  transaction.postedOn(),
                  "WITHDRAWAL",
                  format(amount),
                  transaction.description(),
                  transaction.accountId(),
                  connectionId,
                  transaction.id(),
                  null,
                  null,
                  null,
                  "BANK",
                  match == null ? "SUGGESTED" : matchStatus(match)));
        }
      }
    }
    return new BankFlows(
        contributions,
        withdrawals,
        settlementDebits,
        contributionCount,
        withdrawalCount,
        flows,
        contributionsByMonth,
        withdrawalsByMonth,
        settlementByMonth);
  }

  private ProviderFlows providerFlows(
      UUID userId,
      LocalDate from,
      LocalDate to,
      Map<UUID, ProviderInvestment> investmentsById,
      Set<UUID> selectedConnections) {
    BigDecimal applied = BigDecimal.ZERO;
    BigDecimal redeemed = BigDecimal.ZERO;
    BigDecimal income = BigDecimal.ZERO;
    int applicationCount = 0;
    List<InvestmentContributionView.Flow> flows = new ArrayList<>();
    Map<YearMonth, BigDecimal> appliedByMonth = new HashMap<>();
    Map<YearMonth, BigDecimal> redeemedByMonth = new HashMap<>();
    Map<YearMonth, BigDecimal> incomeByMonth = new HashMap<>();
    Map<YearMonth, BigDecimal> expensesByMonth = new HashMap<>();

    for (ProviderInvestmentTransaction transaction :
        transactionRepository.findByUserIdAndRemovedAtIsNull(userId)) {
      ProviderInvestment investment = investmentsById.get(transaction.getProviderInvestmentId());
      if (investment == null || !selectedConnections.contains(investment.getConnectionId())) {
        continue;
      }
      // O relatório é consolidado em BRL. Fluxos de posições em outra moeda não podem ser somados
      // como reais; a conversão cambial não é inferida.
      if (!"BRL".equalsIgnoreCase(investment.getCurrency())) {
        continue;
      }
      LocalDate date =
          transaction.getTransactionDate() == null
              ? transaction.getTradeDate()
              : transaction.getTransactionDate();
      if (date == null || date.isBefore(from) || date.isAfter(to)) {
        continue;
      }
      YearMonth month = YearMonth.from(date);
      String type = transaction.getType();
      BigDecimal amount = decimal(decryptTransaction(userId, transaction, "net_amount"));
      if (amount == null) {
        amount = decimal(decryptTransaction(userId, transaction, "amount"));
      }
      if (amount == null) {
        continue;
      }
      String name = decryptInvestment(userId, investment, "name");
      String code = decryptInvestment(userId, investment, "code");
      if ("BUY".equals(type)) {
        applied = applied.add(amount);
        appliedByMonth.merge(month, amount, BigDecimal::add);
        applicationCount++;
        flows.add(
            new InvestmentContributionView.Flow(
                date,
                "APPLICATION",
                format(amount.negate()),
                decryptTransaction(userId, transaction, "description"),
                null,
                investment.getConnectionId(),
                null,
                investment.getId(),
                name,
                code,
                "PROVIDER",
                "PROVIDER"));
      } else if ("SELL".equals(type) || "AMORTIZATION".equals(type)) {
        redeemed = redeemed.add(amount);
        redeemedByMonth.merge(month, amount, BigDecimal::add);
        flows.add(
            new InvestmentContributionView.Flow(
                date,
                "REDEMPTION",
                format(amount),
                decryptTransaction(userId, transaction, "description"),
                null,
                investment.getConnectionId(),
                null,
                investment.getId(),
                name,
                code,
                "PROVIDER",
                "PROVIDER"));
      } else if ("INTEREST".equals(type)) {
        income = income.add(amount);
        incomeByMonth.merge(month, amount, BigDecimal::add);
        flows.add(
            new InvestmentContributionView.Flow(
                date,
                "INCOME",
                format(amount),
                decryptTransaction(userId, transaction, "description"),
                null,
                investment.getConnectionId(),
                null,
                investment.getId(),
                name,
                code,
                "PROVIDER",
                "PROVIDER"));
      }

      BigDecimal expenses = decimal(decryptTransaction(userId, transaction, "expenses"));
      if (expenses != null && expenses.signum() > 0) {
        expensesByMonth.merge(month, expenses, BigDecimal::add);
        flows.add(
            new InvestmentContributionView.Flow(
                date,
                "FEE",
                format(expenses.negate()),
                "Custos informados pelo provedor",
                null,
                investment.getConnectionId(),
                null,
                investment.getId(),
                name,
                code,
                "PROVIDER",
                "PROVIDER"));
      }
    }
    return new ProviderFlows(
        applied,
        redeemed,
        income,
        applicationCount,
        flows,
        appliedByMonth,
        redeemedByMonth,
        incomeByMonth,
        expensesByMonth);
  }

  private BigDecimal feeForMonth(MonthAccumulator month, BigDecimal providerExpenses) {
    if (providerExpenses != null && providerExpenses.signum() > 0) {
      return providerExpenses;
    }
    BigDecimal settlement = month.settlementDebits;
    BigDecimal applied = month.applied;
    if (settlement.signum() <= 0 || applied.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal difference = settlement.subtract(applied);
    if (difference.signum() < 0 || difference.compareTo(applied.multiply(MAX_FEE_RATIO)) > 0) {
      return BigDecimal.ZERO;
    }
    return difference;
  }

  private List<InvestmentContributionView.Source> sources(
      Set<UUID> selectedConnections,
      Map<UUID, UUID> perimeter,
      Map<UUID, String> perimeterSources,
      Map<UUID, AccountView> accountsById,
      Map<UUID, ProviderInvestment> investmentsById) {
    List<InvestmentContributionView.Source> sources = new ArrayList<>();
    for (UUID connectionId : selectedConnections) {
      int accountCount = 0;
      String name = null;
      for (Map.Entry<UUID, UUID> entry : perimeter.entrySet()) {
        if (!connectionId.equals(entry.getValue())) {
          continue;
        }
        accountCount++;
        AccountView account = accountsById.get(entry.getKey());
        if (name == null && account != null) {
          name =
              account.institutionName() != null && !account.institutionName().isBlank()
                  ? account.institutionName()
                  : account.alias();
        }
      }
      int positionCount = 0;
      for (ProviderInvestment investment : investmentsById.values()) {
        if (connectionId.equals(investment.getConnectionId())) {
          positionCount++;
        }
      }
      sources.add(
          new InvestmentContributionView.Source(
              connectionId,
              name,
              accountCount,
              positionCount,
              perimeterSources.getOrDefault(connectionId, "CONNECTION_FALLBACK")));
    }
    return sources;
  }

  private Map<UUID, CategoryKind> categoryKinds(UUID userId) {
    Map<UUID, CategoryKind> kinds = new HashMap<>();
    for (CategoryView category : categoryService.listExisting(userId)) {
      kinds.put(category.id(), CategoryKind.valueOf(category.kind()));
    }
    return kinds;
  }

  private Map<UUID, InternalMatch> matches(UUID userId) {
    Map<UUID, InternalMatch> matches = new HashMap<>();
    // Somente correspondências confirmadas podem excluir os dois lados dos totais. Fluxos sem
    // correspondência confirmada aparecem como "SUGGESTED" para o usuário revisar.
    for (InternalMatch match :
        matchRepository.findByUserIdAndStatusOrderByScoreDescCreatedAtAsc(
            userId, InternalMatchStatus.CONFIRMED)) {
      matches.putIfAbsent(match.getDebitTransactionId(), match);
      matches.putIfAbsent(match.getCreditTransactionId(), match);
    }
    return matches;
  }

  private boolean isInternal(
      InternalMatch match, TransactionView transaction, Map<UUID, UUID> perimeter) {
    UUID counterpart =
        transaction.id().equals(match.getDebitTransactionId())
            ? match.getCreditTransactionId()
            : match.getDebitTransactionId();
    return perimeter.containsKey(counterpart);
  }

  private String matchStatus(InternalMatch match) {
    return match.getStatus() == InternalMatchStatus.CONFIRMED ? "CONFIRMED" : "SUGGESTED";
  }

  private Period period(LocalDate from, LocalDate to, LocalDate today) {
    LocalDate start = from == null ? today.withDayOfMonth(1) : from;
    LocalDate end = to == null ? today : to;
    if (start.isAfter(end)) {
      throw new ValidationException("from", "A data inicial deve ser anterior à final.");
    }
    if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
      throw new ValidationException(
          "from",
          "O período do relatório de aportes deve ter no máximo " + MAX_RANGE_DAYS + " dias.");
    }
    return new Period(start, end, end.plusDays(1));
  }

  private String decryptTransaction(
      UUID userId, ProviderInvestmentTransaction transaction, String column) {
    byte[] value =
        switch (column) {
          case "amount" -> transaction.getAmountEnc();
          case "net_amount" -> transaction.getNetAmountEnc();
          case "description" -> transaction.getDescriptionEnc();
          case "expenses" -> transaction.getExpensesEnc();
          default -> null;
        };
    return encryptedValueReader
        .tryRead(userId, TRANSACTION_TABLE, column, transaction.getId(), value)
        .orElse(null);
  }

  private String decryptInvestment(UUID userId, ProviderInvestment investment, String column) {
    byte[] value = "name".equals(column) ? investment.getNameEnc() : investment.getCodeEnc();
    return encryptedValueReader
        .tryRead(userId, INVESTMENT_TABLE, column, investment.getId(), value)
        .orElse(null);
  }

  private static BigDecimal decimal(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static BigDecimal decimal(InvestmentHoldingView holding) {
    return decimal(holding.balance());
  }

  private static String format(BigDecimal value) {
    return Money.plain(value);
  }

  private record Period(LocalDate from, LocalDate to, LocalDate toExclusive) {}

  private record BankFlows(
      BigDecimal contributions,
      BigDecimal withdrawals,
      BigDecimal settlementDebits,
      int contributionCount,
      int withdrawalCount,
      List<InvestmentContributionView.Flow> flows,
      Map<YearMonth, BigDecimal> contributionsByMonth,
      Map<YearMonth, BigDecimal> withdrawalsByMonth,
      Map<YearMonth, BigDecimal> settlementByMonth) {

    void addTo(Map<YearMonth, MonthAccumulator> months) {
      contributionsByMonth.forEach(
          (month, value) ->
              months.computeIfAbsent(month, key -> new MonthAccumulator()).contributions = value);
      withdrawalsByMonth.forEach(
          (month, value) ->
              months.computeIfAbsent(month, key -> new MonthAccumulator()).withdrawals = value);
      settlementByMonth.forEach(
          (month, value) ->
              months.computeIfAbsent(month, key -> new MonthAccumulator()).settlementDebits =
                  value);
    }
  }

  private record ProviderFlows(
      BigDecimal applied,
      BigDecimal redeemed,
      BigDecimal income,
      int applicationCount,
      List<InvestmentContributionView.Flow> flows,
      Map<YearMonth, BigDecimal> appliedByMonth,
      Map<YearMonth, BigDecimal> redeemedByMonth,
      Map<YearMonth, BigDecimal> incomeByMonth,
      Map<YearMonth, BigDecimal> expensesByMonth) {

    void addTo(Map<YearMonth, MonthAccumulator> months) {
      appliedByMonth.forEach(
          (month, value) ->
              months.computeIfAbsent(month, key -> new MonthAccumulator()).applied = value);
      redeemedByMonth.forEach(
          (month, value) ->
              months.computeIfAbsent(month, key -> new MonthAccumulator()).redeemed = value);
      incomeByMonth.forEach(
          (month, value) ->
              months.computeIfAbsent(month, key -> new MonthAccumulator()).income = value);
    }
  }

  private static final class MonthAccumulator {
    private BigDecimal contributions = BigDecimal.ZERO;
    private BigDecimal withdrawals = BigDecimal.ZERO;
    private BigDecimal applied = BigDecimal.ZERO;
    private BigDecimal redeemed = BigDecimal.ZERO;
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal settlementDebits = BigDecimal.ZERO;
    private BigDecimal fees = BigDecimal.ZERO;

    InvestmentContributionView.Month toView(YearMonth month) {
      return new InvestmentContributionView.Month(
          month.toString(),
          format(contributions),
          format(withdrawals),
          format(contributions.subtract(withdrawals)),
          format(applied),
          format(redeemed),
          format(income),
          format(fees),
          format(
              contributions
                  .subtract(withdrawals)
                  .subtract(applied)
                  .add(redeemed)
                  .add(income)
                  .subtract(fees)));
    }
  }
}
