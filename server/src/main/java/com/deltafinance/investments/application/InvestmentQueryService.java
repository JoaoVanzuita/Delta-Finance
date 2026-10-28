package com.deltafinance.investments.application;

import com.deltafinance.investments.domain.ProviderInvestment;
import com.deltafinance.investments.domain.ProviderInvestmentRepository;
import com.deltafinance.investments.domain.ProviderInvestmentSync;
import com.deltafinance.investments.domain.ProviderInvestmentSyncRepository;
import com.deltafinance.investments.domain.ProviderInvestmentTransaction;
import com.deltafinance.investments.domain.ProviderInvestmentTransactionRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Leitura das posições importadas, cobertura e proventos do provedor.
 *
 * <p>Não estima valores ausentes e não mistura posições manuais na alocação importada. Posições
 * encerradas ou indisponíveis permanecem visíveis, mas não compõem o valor atual.
 */
@Service
public class InvestmentQueryService {

  private static final String INVESTMENT_TABLE = "provider_investment";
  private static final String TRANSACTION_TABLE = "provider_investment_transaction";
  private static final int STALE_DAYS = 30;
  private static final String INTEREST = "INTEREST";

  private final ProviderInvestmentRepository investmentRepository;
  private final ProviderInvestmentTransactionRepository transactionRepository;
  private final ProviderInvestmentSyncRepository syncRepository;
  private final InvestmentPositionService positionService;
  private final InvestmentIncomeService incomeService;
  private final CryptoService cryptoService;
  private final Clock clock;

  public InvestmentQueryService(
      ProviderInvestmentRepository investmentRepository,
      ProviderInvestmentTransactionRepository transactionRepository,
      ProviderInvestmentSyncRepository syncRepository,
      InvestmentPositionService positionService,
      InvestmentIncomeService incomeService,
      CryptoService cryptoService,
      Clock clock) {
    this.investmentRepository = investmentRepository;
    this.transactionRepository = transactionRepository;
    this.syncRepository = syncRepository;
    this.positionService = positionService;
    this.incomeService = incomeService;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  public InvestmentOverviewView overview(UUID userId) {
    LocalDate today = clock.today();
    List<InvestmentHoldingView> views = holdingsAll(userId, today);
    List<InvestmentHoldingView> active =
        views.stream().filter(InvestmentQueryService::isCurrent).toList();

    BigDecimal providerValue =
        active.stream()
            .map(InvestmentQueryService::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    List<InvestmentPositionView> manual = positionService.listAll(userId);
    BigDecimal manualValue =
        manual.stream()
            .filter(position -> !position.excludeFromWealth())
            .map(InvestmentPositionView::total)
            .map(InvestmentQueryService::decimal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalValue = providerValue.add(manualValue);

    BigDecimal profit = BigDecimal.ZERO;
    int profitCoverage = 0;
    BigDecimal estimatedProfit = BigDecimal.ZERO;
    int estimatedCoverage = 0;
    for (InvestmentHoldingView holding : active) {
      BigDecimal reported = decimal(holding.amountProfit());
      if (reported != null) {
        profit = profit.add(reported);
        profitCoverage++;
      }
      BigDecimal estimated = decimal(holding.estimatedProfit());
      if (estimated != null) {
        estimatedProfit = estimatedProfit.add(estimated);
        estimatedCoverage++;
      }
    }

    InvestmentIncomeView income = income(userId, today.minusMonths(12), today);
    List<InvestmentSourceView> coverage = sources(userId);
    Instant lastSyncedAt = lastSyncedAt(coverage, views);
    boolean stale = active.stream().anyMatch(InvestmentHoldingView::stale);

    return new InvestmentOverviewView(
        today,
        format(providerValue),
        format(manualValue),
        format(totalValue),
        active.size(),
        (int) views.stream().filter(InvestmentQueryService::isClosed).count(),
        (int) views.stream().filter(holding -> holding.unavailableAt() != null).count(),
        manual.size(),
        income.total(),
        income.source(),
        profitCoverage == 0 ? null : format(profit),
        profitCoverage,
        estimatedCoverage == 0 ? null : format(estimatedProfit),
        estimatedCoverage,
        lastSyncedAt,
        stale,
        allocation(active, providerValue),
        coverage,
        duplicates(views, manual));
  }

  public List<InvestmentHoldingView> holdings(UUID userId, String status, String assetClass) {
    LocalDate today = clock.today();
    String normalizedStatus = normalize(status);
    String normalizedClass = normalize(assetClass);
    return holdingsAll(userId, today).stream()
        .filter(holding -> matchesStatus(holding, normalizedStatus))
        .filter(holding -> normalizedClass == null || normalizedClass.equals(holding.assetClass()))
        .sorted(
            Comparator.comparing((InvestmentHoldingView holding) -> statusRank(holding))
                .thenComparing(InvestmentHoldingView::assetClass)
                .thenComparing(
                    holding -> holding.name() == null ? "" : holding.name(),
                    String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public InvestmentHoldingView holding(UUID userId, UUID holdingId) {
    ProviderInvestment investment =
        investmentRepository
            .findByIdAndUserId(holdingId, userId)
            .orElseThrow(NotFoundException::new);
    Flow flow = flows(userId).getOrDefault(holdingId, new Flow());
    BigDecimal manualIncome =
        incomeService.byProviderInvestment(userId).getOrDefault(holdingId, BigDecimal.ZERO);
    return toView(userId, investment, clock.today(), flow, manualIncome);
  }

  public List<InvestmentSourceView> sources(UUID userId) {
    return syncRepository.findByUserIdOrderByLastSyncedAtDesc(userId).stream()
        .map(this::toSource)
        .toList();
  }

  public InvestmentIncomeView income(UUID userId, LocalDate from, LocalDate to) {
    LocalDate start = from == null ? clock.today().minusMonths(12) : from;
    LocalDate end = to == null ? clock.today() : to;
    if (start.isAfter(end)) {
      throw new ValidationException("from", "A data inicial deve ser anterior à final.");
    }

    Map<UUID, ProviderInvestment> investments = new HashMap<>();
    for (ProviderInvestment investment :
        investmentRepository.findByUserIdOrderByStatusAscTypeAscIdAsc(userId)) {
      investments.put(investment.getId(), investment);
    }

    List<InvestmentIncomeView.Item> items = new ArrayList<>();
    TreeMap<YearMonth, BigDecimal> byMonth = new TreeMap<>();
    Map<YearMonth, Integer> monthCounts = new HashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    boolean hasProvider = false;
    boolean hasManual = false;

    for (ProviderInvestmentTransaction transaction :
        transactionRepository.findByUserIdAndTypeAndRemovedAtIsNull(userId, INTEREST)) {
      LocalDate date =
          transaction.getTransactionDate() == null
              ? transaction.getTradeDate()
              : transaction.getTransactionDate();
      if (date == null || date.isBefore(start) || date.isAfter(end)) {
        continue;
      }
      BigDecimal amount =
          decimal(
              decrypt(
                  userId,
                  TRANSACTION_TABLE,
                  "amount",
                  transaction.getId(),
                  transaction.getAmountEnc()));
      if (amount == null) {
        continue;
      }
      ProviderInvestment investment = investments.get(transaction.getProviderInvestmentId());
      items.add(
          new InvestmentIncomeView.Item(
              null,
              "PROVIDER",
              transaction.getProviderInvestmentId(),
              investment == null
                  ? null
                  : decrypt(
                      userId,
                      INVESTMENT_TABLE,
                      "name",
                      investment.getId(),
                      investment.getNameEnc()),
              investment == null
                  ? null
                  : decrypt(
                      userId,
                      INVESTMENT_TABLE,
                      "code",
                      investment.getId(),
                      investment.getCodeEnc()),
              investment == null ? null : investment.getType(),
              "INTEREST",
              date,
              format(amount),
              decrypt(
                  userId,
                  TRANSACTION_TABLE,
                  "description",
                  transaction.getId(),
                  transaction.getDescriptionEnc()),
              "PROVIDER",
              false));
      hasProvider = true;
      total = total.add(amount);
      YearMonth month = YearMonth.from(date);
      byMonth.merge(month, amount, BigDecimal::add);
      monthCounts.merge(month, 1, Integer::sum);
    }

    for (InvestmentIncomeEntryView entry : incomeService.list(userId, start, end)) {
      BigDecimal amount = decimal(entry.amount());
      if (amount == null) {
        continue;
      }
      ProviderInvestment target =
          "PROVIDER".equals(entry.targetType()) ? investments.get(entry.targetId()) : null;
      items.add(
          new InvestmentIncomeView.Item(
              entry.id(),
              entry.targetType(),
              entry.targetId(),
              entry.targetName(),
              entry.targetCode(),
              target == null ? "MANUAL" : target.getType(),
              entry.incomeType(),
              entry.incomeDate(),
              format(amount),
              entry.description(),
              "MANUAL",
              true));
      hasManual = true;
      total = total.add(amount);
      YearMonth month = YearMonth.from(entry.incomeDate());
      byMonth.merge(month, amount, BigDecimal::add);
      monthCounts.merge(month, 1, Integer::sum);
    }

    items.sort(
        Comparator.comparing(InvestmentIncomeView.Item::date)
            .reversed()
            .thenComparing(item -> item.investmentName() == null ? "" : item.investmentName()));
    List<InvestmentIncomeView.Month> months =
        byMonth.entrySet().stream()
            .map(
                entry ->
                    new InvestmentIncomeView.Month(
                        entry.getKey().toString(),
                        format(entry.getValue()),
                        monthCounts.getOrDefault(entry.getKey(), 0)))
            .toList();
    String source =
        hasProvider && hasManual
            ? "MIXED"
            : hasProvider ? "PROVIDER" : hasManual ? "MANUAL" : "UNAVAILABLE";
    return new InvestmentIncomeView(start, end, format(total), source, months, items);
  }

  private List<InvestmentHoldingView> holdingsAll(UUID userId, LocalDate today) {
    Map<UUID, Flow> flows = flows(userId);
    Map<UUID, BigDecimal> manualIncome = incomeService.byProviderInvestment(userId);
    return investmentRepository.findByUserIdOrderByStatusAscTypeAscIdAsc(userId).stream()
        .map(
            investment ->
                toView(
                    userId,
                    investment,
                    today,
                    flows.getOrDefault(investment.getId(), new Flow()),
                    manualIncome.getOrDefault(investment.getId(), BigDecimal.ZERO)))
        .toList();
  }

  private Map<UUID, Flow> flows(UUID userId) {
    Map<UUID, Flow> flows = new HashMap<>();
    for (ProviderInvestmentTransaction transaction :
        transactionRepository.findByUserIdAndRemovedAtIsNull(userId)) {
      BigDecimal amount =
          decimal(
              decrypt(
                  userId,
                  TRANSACTION_TABLE,
                  "amount",
                  transaction.getId(),
                  transaction.getAmountEnc()));
      if (amount == null) {
        continue;
      }
      Flow flow = flows.computeIfAbsent(transaction.getProviderInvestmentId(), key -> new Flow());
      String type = transaction.getType();
      if (INTEREST.equals(type)) {
        flow.income = flow.income.add(amount);
      } else if ("SELL".equals(type) || "AMORTIZATION".equals(type)) {
        flow.proceeds = flow.proceeds.add(amount);
      }
    }
    return flows;
  }

  private InvestmentHoldingView toView(
      UUID userId,
      ProviderInvestment investment,
      LocalDate today,
      Flow flow,
      BigDecimal manualIncome) {
    UUID id = investment.getId();
    String name = decrypt(userId, INVESTMENT_TABLE, "name", id, investment.getNameEnc());
    String code = decrypt(userId, INVESTMENT_TABLE, "code", id, investment.getCodeEnc());
    InvestmentClassification.Result classification =
        InvestmentClassification.classify(investment, code);
    String profit =
        decrypt(userId, INVESTMENT_TABLE, "amount_profit", id, investment.getAmountProfitEnc());
    String manualCostBasis =
        decrypt(
            userId, INVESTMENT_TABLE, "manual_cost_basis", id, investment.getManualCostBasisEnc());
    String amountOriginal =
        decrypt(userId, INVESTMENT_TABLE, "amount_original", id, investment.getAmountOriginalEnc());
    BigDecimal manualCost = decimal(manualCostBasis);
    BigDecimal providerCost = decimal(amountOriginal);
    BigDecimal costBasis = manualCost == null ? providerCost : manualCost;
    String costBasisSource =
        manualCost == null ? (providerCost == null ? "UNAVAILABLE" : "PROVIDER") : "MANUAL";
    BigDecimal balance =
        decimal(decrypt(userId, INVESTMENT_TABLE, "balance", id, investment.getBalanceEnc()));
    BigDecimal incomeTotal = flow.income.add(manualIncome);
    BigDecimal estimatedProfit = null;
    String estimatedReturn = null;
    if (costBasis != null && balance != null) {
      estimatedProfit =
          balance
              .add(incomeTotal)
              .add(flow.proceeds)
              .subtract(costBasis)
              .setScale(2, RoundingMode.HALF_EVEN);
      if (costBasis.signum() > 0) {
        estimatedReturn =
            estimatedProfit
                .multiply(BigDecimal.valueOf(100))
                .divide(costBasis, 2, RoundingMode.HALF_EVEN)
                .toPlainString();
      }
    }
    LocalDate referenceDate = investment.getReferenceDate();
    boolean stale = referenceDate != null && referenceDate.isBefore(today.minusDays(STALE_DAYS));
    return new InvestmentHoldingView(
        id,
        investment.getConnectionId(),
        name,
        code,
        decrypt(userId, INVESTMENT_TABLE, "isin", id, investment.getIsinEnc()),
        investment.getType(),
        investment.getSubtype(),
        classification.assetClass(),
        classification.source(),
        investment.getStatus(),
        investment.getCurrency(),
        decrypt(userId, INVESTMENT_TABLE, "quantity", id, investment.getQuantityEnc()),
        decrypt(userId, INVESTMENT_TABLE, "unit_value", id, investment.getUnitValueEnc()),
        decrypt(userId, INVESTMENT_TABLE, "amount", id, investment.getAmountEnc()),
        decrypt(userId, INVESTMENT_TABLE, "balance", id, investment.getBalanceEnc()),
        amountOriginal,
        profit,
        decrypt(
            userId, INVESTMENT_TABLE, "amount_withdrawal", id, investment.getAmountWithdrawalEnc()),
        decrypt(userId, INVESTMENT_TABLE, "taxes", id, investment.getTaxesEnc()),
        decrypt(userId, INVESTMENT_TABLE, "taxes2", id, investment.getTaxes2Enc()),
        decrypt(userId, INVESTMENT_TABLE, "rate", id, investment.getRateEnc()),
        investment.getRateType(),
        decrypt(
            userId, INVESTMENT_TABLE, "fixed_annual_rate", id, investment.getFixedAnnualRateEnc()),
        decrypt(userId, INVESTMENT_TABLE, "last_month_rate", id, investment.getLastMonthRateEnc()),
        decrypt(userId, INVESTMENT_TABLE, "annual_rate", id, investment.getAnnualRateEnc()),
        decrypt(
            userId,
            INVESTMENT_TABLE,
            "last_twelve_months_rate",
            id,
            investment.getLastTwelveMonthsRateEnc()),
        referenceDate,
        investment.getPurchaseDate(),
        investment.getDueDate(),
        investment.getGracePeriodDate(),
        investment.getProviderUpdatedAt(),
        investment.getSyncedAt(),
        investment.getUnavailableAt(),
        manualCostBasis,
        costBasis == null ? null : format(costBasis),
        costBasisSource,
        format(incomeTotal),
        estimatedProfit == null ? null : format(estimatedProfit),
        estimatedReturn,
        investment.getUserAssetClass(),
        profit == null ? "UNAVAILABLE" : "PROVIDER",
        stale);
  }

  private static final class Flow {
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal proceeds = BigDecimal.ZERO;
  }

  private InvestmentSourceView toSource(ProviderInvestmentSync sync) {
    return new InvestmentSourceView(
        sync.getConnectionId(),
        sync.getScope().name(),
        sync.getResourceCount(),
        sync.getAvailableResourceCount(),
        sync.getPendingResourceCount(),
        sync.getUnavailableResourceCount(),
        sync.getResourcesCollectedAt(),
        sync.getLastSyncedAt(),
        sync.getErrorCode());
  }

  private List<InvestmentOverviewView.Allocation> allocation(
      List<InvestmentHoldingView> active, BigDecimal total) {
    Map<String, BigDecimal> values = new LinkedHashMap<>();
    Map<String, Integer> counts = new HashMap<>();
    for (InvestmentHoldingView holding : active) {
      BigDecimal value = balance(holding);
      values.merge(holding.assetClass(), value, BigDecimal::add);
      counts.merge(holding.assetClass(), 1, Integer::sum);
    }
    return values.entrySet().stream()
        .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
        .map(
            entry ->
                new InvestmentOverviewView.Allocation(
                    entry.getKey(),
                    format(entry.getValue()),
                    counts.getOrDefault(entry.getKey(), 0),
                    percentage(entry.getValue(), total)))
        .toList();
  }

  private List<InvestmentOverviewView.Duplicate> duplicates(
      List<InvestmentHoldingView> provider, List<InvestmentPositionView> manual) {
    Map<String, UUID> providerByCode = new HashMap<>();
    for (InvestmentHoldingView holding : provider) {
      if (holding.code() != null && !holding.code().isBlank()) {
        providerByCode.putIfAbsent(normalizeCode(holding.code()), holding.id());
      }
    }
    List<InvestmentOverviewView.Duplicate> duplicates = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (InvestmentPositionView position : manual) {
      String ticker = normalizeCode(position.ticker());
      if (ticker == null || seen.contains(ticker)) {
        continue;
      }
      UUID providerId = providerByCode.get(ticker);
      if (providerId != null) {
        duplicates.add(
            new InvestmentOverviewView.Duplicate(position.id(), position.ticker(), providerId));
        seen.add(ticker);
      }
    }
    return duplicates;
  }

  private Instant lastSyncedAt(
      List<InvestmentSourceView> coverage, List<InvestmentHoldingView> holdings) {
    Instant latest = null;
    for (InvestmentSourceView source : coverage) {
      latest = later(latest, source.lastSyncedAt());
    }
    for (InvestmentHoldingView holding : holdings) {
      latest = later(latest, holding.syncedAt());
    }
    return latest;
  }

  private static Instant later(Instant current, Instant candidate) {
    if (candidate == null) {
      return current;
    }
    return current == null || candidate.isAfter(current) ? candidate : current;
  }

  private static boolean isCurrent(InvestmentHoldingView holding) {
    return holding.unavailableAt() == null
        && !"CLOSED".equals(holding.status())
        && "BRL".equals(holding.currency());
  }

  private static boolean isClosed(InvestmentHoldingView holding) {
    return "CLOSED".equals(holding.status());
  }

  private static boolean matchesStatus(InvestmentHoldingView holding, String status) {
    if (status == null || "ALL".equals(status)) {
      return true;
    }
    return switch (status) {
      case "ACTIVE" -> isCurrent(holding);
      case "CLOSED" -> isClosed(holding);
      case "UNAVAILABLE" -> holding.unavailableAt() != null;
      default -> status.equals(holding.status());
    };
  }

  private static int statusRank(InvestmentHoldingView holding) {
    if (holding.unavailableAt() != null) {
      return 2;
    }
    return isClosed(holding) ? 1 : 0;
  }

  private String decrypt(
      UUID userId, String table, String column, UUID recordId, byte[] ciphertext) {
    if (ciphertext == null) {
      return null;
    }
    try {
      return cryptoService.decryptString(
          ciphertext, new CryptoContext(userId, table, column, recordId));
    } catch (CryptoException exception) {
      return null;
    }
  }

  private static BigDecimal balance(InvestmentHoldingView holding) {
    BigDecimal value = decimal(holding.balance());
    return value == null ? BigDecimal.ZERO : value;
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

  private static String format(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
  }

  private static String percentage(BigDecimal value, BigDecimal total) {
    if (total == null || total.signum() == 0) {
      return "0.00";
    }
    return value
        .multiply(BigDecimal.valueOf(100))
        .divide(total, 2, RoundingMode.HALF_EVEN)
        .toPlainString();
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeCode(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
