package com.deltafinance.reporting.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.categories.application.CategoryView;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Valida período/filtros e monta o contexto compartilhado pelas projeções de relatório. */
@Component
class ReportQueryResolver {

  public static final UUID UNCATEGORIZED_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");
  public static final String UNCATEGORIZED_NAME = "Sem categoria";

  private static final int MAX_RANGE_DAYS = 366;
  private static final DateTimeFormatter DAY_LABEL =
      DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"));
  private static final DateTimeFormatter MONTH_LABEL =
      DateTimeFormatter.ofPattern("MMM/yyyy", Locale.forLanguageTag("pt-BR"));

  private final AccountQueryService accountQueryService;
  private final CategoryService categoryService;
  private final Clock clock;

  ReportQueryResolver(
      AccountQueryService accountQueryService, CategoryService categoryService, Clock clock) {
    this.accountQueryService = accountQueryService;
    this.categoryService = categoryService;
    this.clock = clock;
  }

  ResolvedReportQuery resolve(UUID userId, ReportQuery query) {
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
    Map<UUID, CategoryKind> categoryKinds = new HashMap<>();
    Set<UUID> transferCategoryIds = new HashSet<>();
    UUID outrosCategoryId = null;
    for (CategoryView category : categoryService.listExisting(userId)) {
      categoryNames.put(category.id(), category.name());
      CategoryKind kind = CategoryKind.valueOf(category.kind());
      categoryKinds.put(category.id(), kind);
      if (kind == CategoryKind.TRANSFER) {
        // Categorias do tipo transferência (inclusive as criadas a partir de "Same person
        // transfer" e "Transfer - Internal" do provedor) não compõem receitas nem despesas.
        transferCategoryIds.add(category.id());
      }
      if ("Outros".equals(category.name())) {
        outrosCategoryId = category.id();
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

    return new ResolvedReportQuery(
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
        query.direction(),
        accounts,
        categoryNames,
        categoryKinds,
        transferCategoryIds,
        outrosCategoryId,
        accountQueryService.connectionIdsByAccount(userId));
  }

  List<Bucket> buckets(ResolvedReportQuery resolved) {
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

  LocalDate bucketStartFor(LocalDate date, ReportPeriod period) {
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
}
