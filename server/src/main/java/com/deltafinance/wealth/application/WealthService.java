package com.deltafinance.wealth.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.investments.application.InvestmentHoldingView;
import com.deltafinance.investments.application.InvestmentQueryService;
import com.deltafinance.invoices.application.InvoiceQueryService;
import com.deltafinance.invoices.application.InvoiceReportingService;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.wealth.api.ManualItemRequest;
import com.deltafinance.wealth.domain.ManualAssetLiability;
import com.deltafinance.wealth.domain.ManualAssetLiabilityRepository;
import com.deltafinance.wealth.domain.ManualItemKind;
import com.deltafinance.wealth.domain.WealthCompleteness;
import com.deltafinance.wealth.domain.WealthSourceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Patrimônio consolidado e ativos/passivos manuais.
 *
 * <p>Regras centrais:
 *
 * <ul>
 *   <li>contas {@code BANK}/{@code CASH}/{@code WALLET} ativas em BRL são ativos;
 *   <li>cartões são passivos pelo saldo devedor das faturas, nunca pela compra ou fatura
 *       individual, evitando dupla contagem;
 *   <li>posições de investimento importadas e itens manuais entram uma única vez;
 *   <li>valores ausentes e moeda não suportada viram lacunas explícitas; referências com mais de 30
 *       dias tornam a visão estimada.
 * </ul>
 */
@Service
public class WealthService {

  private static final String MANUAL_TABLE = "manual_asset_liability";
  private static final int STALE_DAYS = 30;

  private final ManualAssetLiabilityRepository manualRepository;
  private final AccountQueryService accountQueryService;
  private final InvoiceQueryService invoiceQueryService;
  private final InvoiceReportingService invoiceReportingService;
  private final InvestmentQueryService investmentQueryService;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final Clock clock;

  public WealthService(
      ManualAssetLiabilityRepository manualRepository,
      AccountQueryService accountQueryService,
      InvoiceQueryService invoiceQueryService,
      InvoiceReportingService invoiceReportingService,
      InvestmentQueryService investmentQueryService,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.manualRepository = manualRepository;
    this.accountQueryService = accountQueryService;
    this.invoiceQueryService = invoiceQueryService;
    this.invoiceReportingService = invoiceReportingService;
    this.investmentQueryService = investmentQueryService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  /** Composição atual, sem inferir valores ausentes. */
  public WealthOverviewView overview(UUID userId) {
    LocalDate today = clock.today();
    Accumulator accumulator = new Accumulator();

    List<AccountView> accounts = accountQueryService.list(userId);
    Set<UUID> cardIds = new LinkedHashSet<>();
    for (AccountView account : accounts) {
      if ("CREDIT".equals(account.type())) {
        cardIds.add(account.id());
      }
    }
    for (UUID cardId : cardIds) {
      try {
        invoiceQueryService.ensureInvoices(userId, cardId);
      } catch (ValidationException ignored) {
        // Sem ciclo configurado: o cartão aparece como lacuna, não como erro fatal.
      }
    }
    Map<UUID, BigDecimal> cardOutstanding = invoiceReportingService.outstandingByCard(userId);

    for (AccountView account : accounts) {
      if ("CREDIT".equals(account.type())) {
        addCard(accumulator, account, cardOutstanding, today);
      } else {
        addAccount(accumulator, account, today);
      }
    }

    for (InvestmentHoldingView holding : investmentQueryService.holdings(userId, "ACTIVE", null)) {
      addProviderInvestment(accumulator, holding, today);
    }

    for (ManualAssetLiability item :
        manualRepository.findByUserIdOrderByReferenceDateDescIdDesc(userId)) {
      addManualItem(accumulator, item, today);
    }

    boolean hasData = accumulator.hasIncluded;
    WealthCompleteness completeness = completeness(accumulator);
    BigDecimal netWorth = accumulator.gross.subtract(accumulator.liabilities);
    return new WealthOverviewView(
        today,
        format(accumulator.gross),
        format(accumulator.liabilities),
        format(netWorth),
        completeness.name(),
        hasData,
        List.copyOf(accumulator.items));
  }

  public List<ManualItemView> listManual(UUID userId) {
    return manualRepository.findByUserIdOrderByReferenceDateDescIdDesc(userId).stream()
        .map(this::toView)
        .toList();
  }

  public ManualItemView getManual(UUID userId, UUID itemId) {
    return toView(requireManual(userId, itemId));
  }

  public ManualItemView createManual(UUID userId, ManualItemRequest request) {
    ManualItemKind kind = parseKind(request.kind());
    ManualAssetLiability item = new ManualAssetLiability(userId, kind);
    applyManual(item, request, kind);
    manualRepository.save(item);
    auditService.record(
        userId,
        AuditEventType.WEALTH_MANUAL_ITEM_CREATED,
        MANUAL_TABLE,
        item.getId(),
        Map.of("kind", kind.name()));
    return toView(item);
  }

  public ManualItemView updateManual(UUID userId, UUID itemId, ManualItemRequest request) {
    ManualAssetLiability item = requireManual(userId, itemId);
    ManualItemKind kind = parseKind(request.kind());
    applyManual(item, request, kind);
    manualRepository.save(item);
    auditService.record(
        userId,
        AuditEventType.WEALTH_MANUAL_ITEM_UPDATED,
        MANUAL_TABLE,
        item.getId(),
        Map.of("kind", kind.name()));
    return toView(item);
  }

  public void deleteManual(UUID userId, UUID itemId) {
    ManualAssetLiability item = requireManual(userId, itemId);
    manualRepository.delete(item);
    auditService.record(userId, AuditEventType.WEALTH_MANUAL_ITEM_DELETED, MANUAL_TABLE, itemId);
  }

  private void addAccount(Accumulator accumulator, AccountView account, LocalDate today) {
    String type = account.type();
    if (!"BANK".equals(type) && !"CASH".equals(type) && !"WALLET".equals(type)) {
      return;
    }
    String description = description(account, type);
    String displayedValue =
        account.currentBalance() == null || account.currentBalance().isBlank()
            ? "0.00"
            : format(new BigDecimal(account.currentBalance()));
    if (!"ACTIVE".equals(account.status())) {
      accumulator.exclude(
          item(
              WealthSourceType.ACCOUNT,
              account.id(),
              description,
              displayedValue,
              false,
              "Conta inativa, encerrada ou indisponível não compõe o patrimônio atual.",
              account.origin(),
              null,
              false));
      return;
    }
    if (!Money.BRL.equals(account.currency())) {
      accumulator.exclude(
          item(
              WealthSourceType.ACCOUNT,
              account.id(),
              description,
              displayedValue,
              false,
              "Moeda diferente de BRL não compõe o patrimônio consolidado.",
              account.origin(),
              null,
              false));
      return;
    }
    if (account.currentBalance() == null || account.currentBalance().isBlank()) {
      accumulator.gap(
          item(
              WealthSourceType.ACCOUNT,
              account.id(),
              description,
              "0.00",
              false,
              "Saldo não informado.",
              account.origin(),
              null,
              false));
      return;
    }
    LocalDate referenceDate =
        account.balanceReferenceAt() == null
            ? null
            : LocalDate.ofInstant(account.balanceReferenceAt(), clock.zone());
    boolean stale = isStale(referenceDate, today);
    accumulator.include(
        item(
            WealthSourceType.ACCOUNT,
            account.id(),
            description,
            displayedValue,
            true,
            null,
            account.origin(),
            referenceDate,
            stale),
        false);
  }

  private void addCard(
      Accumulator accumulator,
      AccountView account,
      Map<UUID, BigDecimal> cardOutstanding,
      LocalDate today) {
    String description = description(account, "CREDIT");
    if (!Money.BRL.equals(account.currency())) {
      accumulator.exclude(
          item(
              WealthSourceType.CARD,
              account.id(),
              description,
              "0.00",
              false,
              "Moeda diferente de BRL não compõe o passivo consolidado.",
              account.origin(),
              today,
              false));
      return;
    }
    if (!cardOutstanding.containsKey(account.id())) {
      accumulator.gap(
          item(
              WealthSourceType.CARD,
              account.id(),
              description,
              "0.00",
              false,
              "Sem fatura ou saldo devedor conhecido.",
              account.origin(),
              today,
              false));
      return;
    }
    BigDecimal value = cardOutstanding.get(account.id()).setScale(2, RoundingMode.HALF_EVEN);
    if (!"ACTIVE".equals(account.status()) && value.signum() == 0) {
      accumulator.exclude(
          item(
              WealthSourceType.CARD,
              account.id(),
              description,
              "0.00",
              false,
              "Cartão inativo sem saldo devedor.",
              account.origin(),
              today,
              false));
      return;
    }
    accumulator.include(
        item(
            WealthSourceType.CARD,
            account.id(),
            description,
            format(value),
            true,
            null,
            account.origin(),
            today,
            false),
        true);
  }

  private void addProviderInvestment(
      Accumulator accumulator, InvestmentHoldingView holding, LocalDate today) {
    String name =
        holding.name() == null || holding.name().isBlank()
            ? "Posição de investimento importada"
            : holding.name();
    String description =
        holding.code() == null || holding.code().isBlank()
            ? name
            : name + " (" + holding.code() + ")";
    boolean stale = holding.stale();
    if (holding.balance() == null || holding.balance().isBlank()) {
      accumulator.gap(
          item(
              WealthSourceType.INVESTMENT,
              holding.id(),
              description,
              "0.00",
              false,
              "Valor atual não informado pelo provedor.",
              "PROVIDER",
              holding.referenceDate(),
              stale));
      return;
    }
    accumulator.include(
        item(
            WealthSourceType.INVESTMENT,
            holding.id(),
            description,
            format(new BigDecimal(holding.balance())),
            true,
            null,
            "PROVIDER",
            holding.referenceDate(),
            stale),
        false);
  }

  private void addManualItem(
      Accumulator accumulator, ManualAssetLiability entity, LocalDate today) {
    String description =
        decrypt(
            entity.getUserId(),
            MANUAL_TABLE,
            "description",
            entity.getId(),
            entity.getDescriptionEnc());
    String value =
        decrypt(
            entity.getUserId(),
            MANUAL_TABLE,
            "reference_value",
            entity.getId(),
            entity.getReferenceValueEnc());
    if (description == null || description.isBlank()) {
      description = "Item manual";
    }
    boolean liability = entity.getKind() == ManualItemKind.LIABILITY;
    WealthSourceType sourceType =
        liability ? WealthSourceType.MANUAL_LIABILITY : WealthSourceType.MANUAL_ASSET;
    if (value == null) {
      accumulator.gap(
          item(
              sourceType,
              entity.getId(),
              description,
              "0.00",
              false,
              "Valor não disponível.",
              entity.getReferenceDate(),
              false));
      return;
    }
    boolean stale = isStale(entity.getReferenceDate(), today);
    accumulator.include(
        item(
            sourceType,
            entity.getId(),
            description,
            format(new BigDecimal(value)),
            true,
            null,
            entity.getReferenceDate(),
            stale),
        liability);
  }

  private ManualItemView toView(ManualAssetLiability entity) {
    return new ManualItemView(
        entity.getId(),
        entity.getKind().name(),
        entity.getType(),
        decrypt(
            entity.getUserId(),
            MANUAL_TABLE,
            "description",
            entity.getId(),
            entity.getDescriptionEnc()),
        decrypt(
            entity.getUserId(),
            MANUAL_TABLE,
            "reference_value",
            entity.getId(),
            entity.getReferenceValueEnc()),
        entity.getReferenceDate(),
        decrypt(entity.getUserId(), MANUAL_TABLE, "notes", entity.getId(), entity.getNotesEnc()));
  }

  private void applyManual(
      ManualAssetLiability item, ManualItemRequest request, ManualItemKind kind) {
    UUID userId = item.getUserId();
    UUID id = item.getId();
    item.apply(
        kind,
        requireText(request.type(), "type", 60, "Informe o tipo do item."),
        encrypt(
            userId,
            id,
            MANUAL_TABLE,
            "description",
            requireText(request.description(), "description", 200, "Informe a descrição.")),
        encrypt(userId, id, MANUAL_TABLE, "reference_value", parseValue(request.referenceValue())),
        requireDate(request.referenceDate()),
        optionalEncrypt(
            userId, id, MANUAL_TABLE, "notes", optionalText(request.notes(), "notes", 2000)));
  }

  private ManualAssetLiability requireManual(UUID userId, UUID itemId) {
    return manualRepository.findByIdAndUserId(itemId, userId).orElseThrow(NotFoundException::new);
  }

  private static String description(AccountView account, String type) {
    if (account.alias() != null && !account.alias().isBlank()) {
      return account.alias();
    }
    return switch (type) {
      case "CREDIT" -> "Cartão de crédito";
      case "CASH" -> "Dinheiro";
      case "WALLET" -> "Carteira digital";
      default -> "Conta bancária";
    };
  }

  private static WealthItemView item(
      WealthSourceType sourceType,
      UUID sourceId,
      String description,
      String value,
      boolean included,
      String exclusionReason,
      LocalDate referenceDate,
      boolean stale) {
    return item(
        sourceType,
        sourceId,
        description,
        value,
        included,
        exclusionReason,
        originFor(sourceType),
        referenceDate,
        stale);
  }

  private static WealthItemView item(
      WealthSourceType sourceType,
      UUID sourceId,
      String description,
      String value,
      boolean included,
      String exclusionReason,
      String origin,
      LocalDate referenceDate,
      boolean stale) {
    return new WealthItemView(
        sourceType.name(),
        sourceId,
        description,
        value,
        included,
        exclusionReason,
        origin,
        referenceDate,
        stale);
  }

  private static String originFor(WealthSourceType sourceType) {
    return switch (sourceType) {
      case MANUAL_ASSET, MANUAL_LIABILITY -> "MANUAL";
      default -> "PROVIDER";
    };
  }

  private boolean isStale(LocalDate referenceDate, LocalDate today) {
    return referenceDate != null && referenceDate.isBefore(today.minusDays(STALE_DAYS));
  }

  private static WealthCompleteness completeness(Accumulator accumulator) {
    if (!accumulator.hasIncluded) {
      return WealthCompleteness.PARTIAL;
    }
    if (accumulator.hasGap) {
      return WealthCompleteness.PARTIAL;
    }
    if (accumulator.hasStale) {
      return WealthCompleteness.ESTIMATED;
    }
    return WealthCompleteness.COMPLETE;
  }

  private static String format(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
  }

  private static ManualItemKind parseKind(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("kind", "Informe se é ativo ou passivo.");
    }
    try {
      return ManualItemKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("kind", "Use ASSET ou LIABILITY.");
    }
  }

  private static String parseValue(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("referenceValue", "Informe o valor de referência.");
    }
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(value.trim());
    } catch (NumberFormatException exception) {
      throw new ValidationException("referenceValue", "Valor de referência inválido.");
    }
    if (parsed.signum() < 0) {
      throw new ValidationException(
          "referenceValue", "O valor de referência não pode ser negativo.");
    }
    return new Money(parsed, Money.BRL).toPlainString();
  }

  private static LocalDate requireDate(LocalDate referenceDate) {
    if (referenceDate == null) {
      throw new ValidationException("referenceDate", "Informe a data de referência.");
    }
    return referenceDate;
  }

  private static String requireText(String value, String field, int maxLength, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field, message);
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLength) {
      throw new ValidationException(
          field, "O valor deve ter no máximo " + maxLength + " caracteres.");
    }
    return trimmed;
  }

  private static String optionalText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLength) {
      throw new ValidationException(
          field, "O valor deve ter no máximo " + maxLength + " caracteres.");
    }
    return trimmed;
  }

  private byte[] encrypt(UUID userId, UUID recordId, String table, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, table, column, recordId));
  }

  private byte[] optionalEncrypt(
      UUID userId, UUID recordId, String table, String column, String value) {
    return value == null ? null : encrypt(userId, recordId, table, column, value);
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

  private static final class Accumulator {
    private final List<WealthItemView> items = new ArrayList<>();
    private BigDecimal gross = BigDecimal.ZERO;
    private BigDecimal liabilities = BigDecimal.ZERO;
    private boolean hasGap;
    private boolean hasStale;
    private boolean hasIncluded;

    void include(WealthItemView item, boolean liability) {
      items.add(item);
      BigDecimal value = new BigDecimal(item.value());
      if (liability) {
        liabilities = liabilities.add(value);
      } else {
        gross = gross.add(value);
      }
      hasIncluded = true;
      hasStale = hasStale || item.stale();
    }

    void gap(WealthItemView item) {
      items.add(item);
      hasGap = true;
    }

    void exclude(WealthItemView item) {
      items.add(item);
    }
  }
}
