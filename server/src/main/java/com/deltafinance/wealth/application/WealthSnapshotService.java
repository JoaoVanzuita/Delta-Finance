package com.deltafinance.wealth.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.investments.application.InvestmentHoldingView;
import com.deltafinance.investments.application.InvestmentQueryService;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.wealth.domain.SnapshotTrigger;
import com.deltafinance.wealth.domain.WealthCompleteness;
import com.deltafinance.wealth.domain.WealthSnapshot;
import com.deltafinance.wealth.domain.WealthSnapshotItem;
import com.deltafinance.wealth.domain.WealthSnapshotItemRepository;
import com.deltafinance.wealth.domain.WealthSnapshotRepository;
import com.deltafinance.wealth.domain.WealthSourceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captura e leitura de snapshots patrimoniais mensais.
 *
 * <p>O snapshot usa exatamente a visão patrimonial vigente, mas grava uma posição datada e
 * imutável. Valores e descrições são cifrados; nenhum valor entra em logs ou auditoria. Não há
 * reconstrução retroativa: a série começa no primeiro snapshot realmente capturado.
 */
@Service
public class WealthSnapshotService {

  public static final String SNAPSHOT_TABLE = "wealth_snapshot";
  public static final String ITEM_TABLE = "wealth_snapshot_item";

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final WealthSnapshotRepository snapshotRepository;
  private final WealthSnapshotItemRepository itemRepository;
  private final WealthService wealthService;
  private final AccountQueryService accountQueryService;
  private final InvestmentQueryService investmentQueryService;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final int retentionMonths;

  public WealthSnapshotService(
      WealthSnapshotRepository snapshotRepository,
      WealthSnapshotItemRepository itemRepository,
      WealthService wealthService,
      AccountQueryService accountQueryService,
      InvestmentQueryService investmentQueryService,
      CryptoService cryptoService,
      AuditService auditService,
      JdbcTemplate jdbcTemplate,
      Clock clock,
      @Value("${delta.wealth.snapshot.retention-months:60}") int retentionMonths) {
    this.snapshotRepository = snapshotRepository;
    this.itemRepository = itemRepository;
    this.wealthService = wealthService;
    this.accountQueryService = accountQueryService;
    this.investmentQueryService = investmentQueryService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
    this.retentionMonths = Math.max(1, retentionMonths);
  }

  public List<WealthSnapshotSummaryView> history(UUID userId, LocalDate from, LocalDate to) {
    Range range = range(from, to);
    List<WealthSnapshot> snapshots =
        snapshotRepository.findByUserIdAndReferenceDateBetweenOrderByReferenceDateDesc(
            userId, range.from(), range.to());
    if (snapshots.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<WealthSnapshotItem>> items = itemsBySnapshot(userId, snapshots);
    return snapshots.stream()
        .map(snapshot -> summary(snapshot, items.getOrDefault(snapshot.getId(), List.of())))
        .toList();
  }

  public WealthSnapshotView detail(UUID userId, UUID snapshotId) {
    WealthSnapshot snapshot =
        snapshotRepository
            .findByIdAndUserId(snapshotId, userId)
            .orElseThrow(NotFoundException::new);
    List<WealthSnapshotItem> items =
        itemRepository.findBySnapshotIdAndUserIdOrderBySourceTypeAscIdAsc(snapshotId, userId);
    return view(snapshot, items);
  }

  /** Evolução mensal de uma reserva, derivada dos snapshots que a incluíram. */
  public List<ReservedBalanceHistoryPointView> reservedBalanceHistory(
      UUID userId, UUID reservedBalanceId) {
    List<WealthSnapshotItem> items =
        itemRepository.findByUserIdAndSourceTypeAndSourceIdOrderBySnapshotIdAsc(
            userId, WealthSourceType.RESERVED_BALANCE, reservedBalanceId);
    if (items.isEmpty()) {
      return List.of();
    }
    Map<UUID, LocalDate> dates = new HashMap<>();
    for (WealthSnapshot snapshot :
        snapshotRepository.findByUserIdAndIdIn(
            userId, items.stream().map(WealthSnapshotItem::getSnapshotId).distinct().toList())) {
      dates.put(snapshot.getId(), snapshot.getReferenceDate());
    }
    return items.stream()
        .filter(item -> dates.containsKey(item.getSnapshotId()))
        .map(
            item ->
                new ReservedBalanceHistoryPointView(
                    dates.get(item.getSnapshotId()),
                    decrypt(userId, item, "value", item.getValueEnc()),
                    item.getCurrency(),
                    item.isIncluded()))
        .sorted(Comparator.comparing(ReservedBalanceHistoryPointView::referenceDate).reversed())
        .toList();
  }

  @Transactional
  public CaptureResult captureScheduled(UUID userId, LocalDate referenceDate) {
    return capture(userId, referenceDate, SnapshotTrigger.SCHEDULED);
  }

  @Transactional
  public WealthSnapshotView captureManual(UUID userId, LocalDate referenceDate) {
    CaptureResult result = capture(userId, referenceDate, SnapshotTrigger.MANUAL);
    return result.snapshot();
  }

  private CaptureResult capture(UUID userId, LocalDate referenceDate, SnapshotTrigger trigger) {
    LocalDate today = clock.today();
    LocalDate effectiveDate = referenceDate == null ? today : referenceDate;
    if (effectiveDate.isAfter(today)) {
      throw new ValidationException("referenceDate", "A data de referência não pode ser futura.");
    }
    if (trigger == SnapshotTrigger.MANUAL && effectiveDate.isBefore(today)) {
      throw new ValidationException(
          "referenceDate", "A captura manual registra a posição atual e não reconstrói o passado.");
    }

    Optional<WealthSnapshot> existing =
        snapshotRepository.findByUserIdAndReferenceDate(userId, effectiveDate);
    if (existing.isPresent()) {
      if (trigger == SnapshotTrigger.MANUAL) {
        throw new ConflictException(
            "WEALTH_SNAPSHOT_ALREADY_EXISTS", "Já existe um snapshot para esta data.");
      }
      return new CaptureResult(
          view(existing.get(), itemsOf(userId, existing.get().getId())), false);
    }

    WealthOverviewView overview = wealthService.overview(userId);
    if (!overview.hasData()) {
      throw new ValidationException(
          "snapshot", "Não há dados patrimoniais suficientes para capturar um snapshot.");
    }

    Map<UUID, AccountView> accounts = new HashMap<>();
    for (AccountView account : accountQueryService.list(userId)) {
      accounts.put(account.id(), account);
    }
    Map<UUID, InvestmentHoldingView> holdings = new HashMap<>();
    for (InvestmentHoldingView holding : investmentQueryService.holdings(userId, "ACTIVE", null)) {
      holdings.put(holding.id(), holding);
    }

    UUID snapshotId = UUID.randomUUID();
    Instant capturedAt = clock.now();
    WealthCompleteness completeness = WealthCompleteness.valueOf(overview.completeness());
    String coverage = coverage(overview.items());

    WealthSnapshot snapshot =
        new WealthSnapshot(
            snapshotId,
            userId,
            effectiveDate,
            capturedAt,
            trigger,
            completeness,
            encrypt(userId, SNAPSHOT_TABLE, "gross_assets", snapshotId, overview.grossAssets()),
            encrypt(userId, SNAPSHOT_TABLE, "liabilities", snapshotId, overview.liabilities()),
            encrypt(userId, SNAPSHOT_TABLE, "net_worth", snapshotId, overview.netWorth()),
            coverage);
    snapshotRepository.save(snapshot);

    List<WealthSnapshotItem> items = new ArrayList<>();
    for (WealthItemView item : overview.items()) {
      WealthSourceType sourceType = WealthSourceType.valueOf(item.sourceType());
      UUID itemId = UUID.randomUUID();
      String currency = currency(sourceType, item.sourceId(), accounts, holdings);
      String value = item.included() ? item.value() : originalValue(item, accounts, holdings);
      String assetClass =
          sourceType == WealthSourceType.INVESTMENT ? assetClass(item.sourceId(), holdings) : null;
      String description =
          item.description() == null || item.description().isBlank()
              ? "Sem descrição"
              : item.description();
      items.add(
          new WealthSnapshotItem(
              itemId,
              snapshotId,
              userId,
              sourceType,
              item.sourceId(),
              assetClass,
              encrypt(userId, ITEM_TABLE, "description", itemId, description),
              encrypt(userId, ITEM_TABLE, "value", itemId, value),
              currency,
              item.included(),
              truncate(item.exclusionReason(), 240),
              item.referenceDate(),
              item.stale()));
    }
    itemRepository.saveAll(items);

    auditService.record(
        userId,
        AuditEventType.WEALTH_SNAPSHOT_CREATED,
        SNAPSHOT_TABLE,
        snapshotId,
        Map.of(
            "referenceDate", effectiveDate.toString(),
            "trigger", trigger.name(),
            "completeness", completeness.name(),
            "items", items.size()));

    return new CaptureResult(view(snapshot, items), true);
  }

  public List<InvestmentHistoryPointView> investmentHistory(
      UUID userId, LocalDate from, LocalDate to) {
    Range range = range(from, to);
    List<WealthSnapshot> snapshots =
        snapshotRepository.findByUserIdAndReferenceDateBetweenOrderByReferenceDateDesc(
            userId, range.from(), range.to());
    if (snapshots.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<WealthSnapshotItem>> items = itemsBySnapshot(userId, snapshots);
    List<InvestmentHistoryPointView> points = new ArrayList<>();
    for (WealthSnapshot snapshot : snapshots) {
      List<WealthSnapshotItem> investmentItems =
          items.getOrDefault(snapshot.getId(), List.of()).stream()
              .filter(item -> item.getSourceType() == WealthSourceType.INVESTMENT)
              .filter(WealthSnapshotItem::isIncluded)
              .filter(item -> "BRL".equalsIgnoreCase(item.getCurrency()))
              .toList();
      if (investmentItems.isEmpty()) {
        continue;
      }
      Map<String, BigDecimal> byClass = new LinkedHashMap<>();
      for (WealthSnapshotItem item : investmentItems) {
        String assetClass = item.getAssetClass() == null ? "OUTRO" : item.getAssetClass();
        BigDecimal value = new BigDecimal(decrypt(userId, item, "value", item.getValueEnc()));
        byClass.merge(assetClass, value, BigDecimal::add);
      }
      BigDecimal total =
          byClass.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
      if (total.signum() <= 0) {
        continue;
      }
      List<InvestmentClassSliceView> allocation =
          byClass.entrySet().stream()
              .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
              .map(
                  entry ->
                      new InvestmentClassSliceView(
                          entry.getKey(),
                          format(entry.getValue()),
                          percentage(entry.getValue(), total),
                          (int)
                              investmentItems.stream()
                                  .filter(
                                      item ->
                                          (item.getAssetClass() == null
                                                  ? "OUTRO"
                                                  : item.getAssetClass())
                                              .equals(entry.getKey()))
                                  .count()))
              .toList();
      points.add(
          new InvestmentHistoryPointView(
              snapshot.getId(),
              snapshot.getReferenceDate(),
              format(total),
              snapshot.getCompleteness().name(),
              allocation));
    }
    points.sort(Comparator.comparing(InvestmentHistoryPointView::referenceDate));
    return points;
  }

  /** Remove snapshots fora da retenção e devolve a quantidade removida. */
  @Transactional
  public int purgeExpired() {
    LocalDate cutoff = clock.today().minusMonths(retentionMonths);
    List<Map<String, Object>> users =
        jdbcTemplate.queryForList(
            "select user_id, count(*) as total from wealth_snapshot"
                + " where reference_date < ? group by user_id",
            cutoff);
    int deleted =
        jdbcTemplate.update("delete from wealth_snapshot where reference_date < ?", cutoff);
    for (Map<String, Object> row : users) {
      UUID userId = (UUID) row.get("user_id");
      Number total = (Number) row.get("total");
      auditService.record(
          userId,
          AuditEventType.WEALTH_SNAPSHOT_PURGED,
          SNAPSHOT_TABLE,
          null,
          Map.of("count", total == null ? 0 : total.intValue(), "cutoff", cutoff.toString()));
    }
    return deleted;
  }

  private Map<UUID, List<WealthSnapshotItem>> itemsBySnapshot(
      UUID userId, List<WealthSnapshot> snapshots) {
    List<UUID> ids = snapshots.stream().map(WealthSnapshot::getId).toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<WealthSnapshotItem>> grouped = new HashMap<>();
    for (WealthSnapshotItem item : itemRepository.findByUserIdAndSnapshotIdIn(userId, ids)) {
      grouped.computeIfAbsent(item.getSnapshotId(), ignored -> new ArrayList<>()).add(item);
    }
    return grouped;
  }

  private List<WealthSnapshotItem> itemsOf(UUID userId, UUID snapshotId) {
    return itemRepository.findBySnapshotIdAndUserIdOrderBySourceTypeAscIdAsc(snapshotId, userId);
  }

  private WealthSnapshotSummaryView summary(
      WealthSnapshot snapshot, List<WealthSnapshotItem> items) {
    int included = (int) items.stream().filter(WealthSnapshotItem::isIncluded).count();
    return new WealthSnapshotSummaryView(
        snapshot.getId(),
        snapshot.getReferenceDate(),
        snapshot.getCapturedAt(),
        snapshot.getTrigger().name(),
        snapshot.getCompleteness().name(),
        decrypt(snapshot.getUserId(), snapshot, "gross_assets", snapshot.getGrossAssetsEnc()),
        decrypt(snapshot.getUserId(), snapshot, "liabilities", snapshot.getLiabilitiesEnc()),
        decrypt(snapshot.getUserId(), snapshot, "net_worth", snapshot.getNetWorthEnc()),
        items.size(),
        included,
        snapshot.getCompleteness() != WealthCompleteness.COMPLETE);
  }

  private WealthSnapshotView view(WealthSnapshot snapshot, List<WealthSnapshotItem> items) {
    int included = (int) items.stream().filter(WealthSnapshotItem::isIncluded).count();
    List<WealthSnapshotItemView> itemViews =
        items.stream()
            .map(
                item ->
                    new WealthSnapshotItemView(
                        item.getSourceType().name(),
                        item.getSourceId(),
                        decrypt(
                            snapshot.getUserId(), item, "description", item.getDescriptionEnc()),
                        decrypt(snapshot.getUserId(), item, "value", item.getValueEnc()),
                        item.getCurrency(),
                        item.getAssetClass(),
                        item.isIncluded(),
                        item.getExclusionReason(),
                        item.getSourceReferenceDate(),
                        item.isStale()))
            .toList();
    return new WealthSnapshotView(
        snapshot.getId(),
        snapshot.getReferenceDate(),
        snapshot.getCapturedAt(),
        snapshot.getTrigger().name(),
        snapshot.getCompleteness().name(),
        decrypt(snapshot.getUserId(), snapshot, "gross_assets", snapshot.getGrossAssetsEnc()),
        decrypt(snapshot.getUserId(), snapshot, "liabilities", snapshot.getLiabilitiesEnc()),
        decrypt(snapshot.getUserId(), snapshot, "net_worth", snapshot.getNetWorthEnc()),
        items.size(),
        included,
        snapshot.getCompleteness() != WealthCompleteness.COMPLETE,
        itemViews);
  }

  private static String coverage(List<WealthItemView> items) {
    int included = (int) items.stream().filter(WealthItemView::included).count();
    return "{\"itemCount\":"
        + items.size()
        + ",\"includedItemCount\":"
        + included
        + ",\"excludedItemCount\":"
        + (items.size() - included)
        + ",\"partial\":"
        + (included < items.size())
        + "}";
  }

  private static String currency(
      WealthSourceType type,
      UUID sourceId,
      Map<UUID, AccountView> accounts,
      Map<UUID, InvestmentHoldingView> holdings) {
    if (type == WealthSourceType.INVESTMENT) {
      InvestmentHoldingView holding = holdings.get(sourceId);
      return holding == null || holding.currency() == null || holding.currency().isBlank()
          ? "BRL"
          : holding.currency();
    }
    if (type == WealthSourceType.MANUAL_ASSET || type == WealthSourceType.MANUAL_LIABILITY) {
      return "BRL";
    }
    if (type == WealthSourceType.RESERVED_BALANCE) {
      return "BRL";
    }
    AccountView account = accounts.get(sourceId);
    return account == null || account.currency() == null || account.currency().isBlank()
        ? "BRL"
        : account.currency();
  }

  private static String originalValue(
      WealthItemView item,
      Map<UUID, AccountView> accounts,
      Map<UUID, InvestmentHoldingView> holdings) {
    WealthSourceType type = WealthSourceType.valueOf(item.sourceType());
    if (type == WealthSourceType.INVESTMENT) {
      InvestmentHoldingView holding = holdings.get(item.sourceId());
      return holding == null || holding.balance() == null || holding.balance().isBlank()
          ? item.value()
          : holding.balance();
    }
    if (type == WealthSourceType.ACCOUNT || type == WealthSourceType.CARD) {
      AccountView account = accounts.get(item.sourceId());
      return account == null
              || account.currentBalance() == null
              || account.currentBalance().isBlank()
          ? item.value()
          : account.currentBalance();
    }
    return item.value();
  }

  private static String assetClass(UUID sourceId, Map<UUID, InvestmentHoldingView> holdings) {
    InvestmentHoldingView holding = holdings.get(sourceId);
    return holding == null ? null : holding.assetClass();
  }

  private static String percentage(BigDecimal value, BigDecimal total) {
    return value.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_EVEN).toPlainString();
  }

  private static String format(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
  }

  private static String truncate(String value, int max) {
    if (value == null || value.length() <= max) {
      return value;
    }
    return value.substring(0, max);
  }

  private byte[] encrypt(
      UUID userId, String table, String column, UUID recordId, String plaintext) {
    return cryptoService.encrypt(plaintext, new CryptoContext(userId, table, column, recordId));
  }

  private String decrypt(UUID userId, WealthSnapshot snapshot, String column, byte[] payload) {
    return cryptoService.decryptString(
        payload, new CryptoContext(userId, SNAPSHOT_TABLE, column, snapshot.getId()));
  }

  private String decrypt(UUID userId, WealthSnapshotItem item, String column, byte[] payload) {
    return cryptoService.decryptString(
        payload, new CryptoContext(userId, ITEM_TABLE, column, item.getId()));
  }

  private Range range(LocalDate from, LocalDate to) {
    LocalDate end = to == null ? clock.today() : to;
    LocalDate start = from == null ? end.minusMonths(11) : from;
    if (start.isAfter(end)) {
      throw new ValidationException("from", "A data inicial deve ser anterior à final.");
    }
    return new Range(start, end);
  }

  private record Range(LocalDate from, LocalDate to) {}

  /** Resultado da captura, distinguindo criação de reexecução idempotente. */
  public record CaptureResult(WealthSnapshotView snapshot, boolean created) {}
}
