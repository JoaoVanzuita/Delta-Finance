package com.deltafinance.investments.application;

import com.deltafinance.investments.api.InvestmentIncomeRequest;
import com.deltafinance.investments.domain.InvestmentIncome;
import com.deltafinance.investments.domain.InvestmentIncomeRepository;
import com.deltafinance.investments.domain.ProviderInvestment;
import com.deltafinance.investments.domain.ProviderInvestmentRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Proventos manuais de investimento.
 *
 * <p>O provento é sempre complementar ao provedor e precisa apontar para uma posição importada ou
 * manual do próprio usuário. Valores e descrições são cifrados; a origem permanece {@code MANUAL}.
 */
@Service
public class InvestmentIncomeService {

  private static final String TABLE = "investment_income";
  private static final Set<String> TYPES =
      Set.of("DIVIDEND", "INTEREST", "JCP", "RENT", "COUPON", "OTHER");

  private final InvestmentIncomeRepository incomeRepository;
  private final ProviderInvestmentRepository investmentRepository;
  private final InvestmentPositionService positionService;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final Clock clock;

  public InvestmentIncomeService(
      InvestmentIncomeRepository incomeRepository,
      ProviderInvestmentRepository investmentRepository,
      InvestmentPositionService positionService,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.incomeRepository = incomeRepository;
    this.investmentRepository = investmentRepository;
    this.positionService = positionService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  /** Proventos manuais no período; datas nulas representam todo o histórico. */
  public List<InvestmentIncomeEntryView> list(UUID userId, LocalDate from, LocalDate to) {
    LocalDate start = from == null ? LocalDate.of(1900, 1, 1) : from;
    LocalDate end = to == null ? clock.today() : to;
    if (start.isAfter(end)) {
      throw new ValidationException("from", "A data inicial deve ser anterior à final.");
    }
    return incomeRepository
        .findByUserIdAndIncomeDateBetweenOrderByIncomeDateDescCreatedAtDesc(userId, start, end)
        .stream()
        .map(entry -> toView(userId, entry))
        .toList();
  }

  /** Soma dos proventos manuais por posição importada, para o resultado estimado. */
  public Map<UUID, BigDecimal> byProviderInvestment(UUID userId) {
    Map<UUID, BigDecimal> totals = new HashMap<>();
    for (InvestmentIncome entry :
        incomeRepository.findByUserIdOrderByIncomeDateDescCreatedAtDesc(userId)) {
      if (entry.getProviderInvestmentId() == null) {
        continue;
      }
      BigDecimal amount =
          decimal(decrypt(userId, TABLE, entry.getId(), "amount", entry.getAmountEnc()));
      if (amount != null) {
        totals.merge(entry.getProviderInvestmentId(), amount, BigDecimal::add);
      }
    }
    return totals;
  }

  public InvestmentIncomeEntryView create(UUID userId, InvestmentIncomeRequest request) {
    Target target = validateTarget(userId, request);
    InvestmentIncome entry = new InvestmentIncome(userId);
    apply(userId, entry, target, request);
    incomeRepository.save(entry);
    auditService.record(
        userId,
        AuditEventType.INVESTMENT_INCOME_CREATED,
        TABLE,
        entry.getId(),
        Map.of("targetType", target.type(), "incomeType", entry.getIncomeType()));
    return toView(userId, entry);
  }

  public InvestmentIncomeEntryView update(
      UUID userId, UUID incomeId, InvestmentIncomeRequest request) {
    InvestmentIncome entry =
        incomeRepository.findByIdAndUserId(incomeId, userId).orElseThrow(NotFoundException::new);
    Target target = validateTarget(userId, request);
    apply(userId, entry, target, request);
    incomeRepository.save(entry);
    auditService.record(
        userId,
        AuditEventType.INVESTMENT_INCOME_UPDATED,
        TABLE,
        entry.getId(),
        Map.of("targetType", target.type(), "incomeType", entry.getIncomeType()));
    return toView(userId, entry);
  }

  public void delete(UUID userId, UUID incomeId) {
    InvestmentIncome entry =
        incomeRepository.findByIdAndUserId(incomeId, userId).orElseThrow(NotFoundException::new);
    incomeRepository.delete(entry);
    auditService.record(userId, AuditEventType.INVESTMENT_INCOME_DELETED, TABLE, incomeId);
  }

  private void apply(
      UUID userId, InvestmentIncome entry, Target target, InvestmentIncomeRequest request) {
    String type = parseType(request.incomeType());
    LocalDate date = request.incomeDate();
    if (date == null) {
      throw new ValidationException("incomeDate", "Informe a data do provento.");
    }
    if (date.isAfter(clock.today())) {
      throw new ValidationException("incomeDate", "O provento não pode estar no futuro.");
    }
    BigDecimal amount = parseAmount(request.amount());
    String description = optionalText(request.description(), "description", 500);
    entry.apply(
        target.providerInvestmentId(),
        target.investmentPositionId(),
        type,
        date,
        cryptoService.encrypt(
            amount.toPlainString(), new CryptoContext(userId, TABLE, "amount", entry.getId())),
        description == null
            ? null
            : cryptoService.encrypt(
                description, new CryptoContext(userId, TABLE, "description", entry.getId())));
  }

  private Target validateTarget(UUID userId, InvestmentIncomeRequest request) {
    boolean provider = request.providerInvestmentId() != null;
    boolean manual = request.investmentPositionId() != null;
    if (provider == manual) {
      throw new ValidationException(
          "providerInvestmentId",
          "Informe exatamente uma posição importada ou manual para o provento.");
    }
    if (provider) {
      ProviderInvestment investment =
          investmentRepository
              .findByIdAndUserId(request.providerInvestmentId(), userId)
              .orElseThrow(NotFoundException::new);
      return new Target("PROVIDER", investment.getId(), null);
    }
    InvestmentPositionView position = positionService.get(userId, request.investmentPositionId());
    return new Target("MANUAL", null, position.id());
  }

  private InvestmentIncomeEntryView toView(UUID userId, InvestmentIncome entry) {
    String targetType;
    UUID targetId;
    String targetName;
    String targetCode;
    if (entry.getProviderInvestmentId() != null) {
      targetType = "PROVIDER";
      targetId = entry.getProviderInvestmentId();
      ProviderInvestment investment =
          investmentRepository.findByIdAndUserId(targetId, userId).orElse(null);
      targetName =
          investment == null
              ? "Posição importada"
              : decrypt(userId, investment, "name", "Posição importada");
      targetCode = investment == null ? null : decrypt(userId, investment, "code", null);
    } else {
      targetType = "MANUAL";
      targetId = entry.getInvestmentPositionId();
      InvestmentPositionView position = positionService.get(userId, targetId);
      targetName = position.assetName();
      targetCode = position.ticker();
    }
    return new InvestmentIncomeEntryView(
        entry.getId(),
        targetType,
        targetId,
        targetName,
        targetCode,
        entry.getIncomeType(),
        entry.getIncomeDate(),
        decrypt(userId, entry, "amount", entry.getAmountEnc()),
        decrypt(userId, entry, "description", entry.getDescriptionEnc()),
        entry.getSource(),
        entry.getCreatedAt());
  }

  private String decrypt(
      UUID userId, ProviderInvestment investment, String column, String fallback) {
    String value =
        decrypt(
            userId,
            "provider_investment",
            investment.getId(),
            column,
            "name".equals(column) ? investment.getNameEnc() : investment.getCodeEnc());
    return value == null || value.isBlank() ? fallback : value;
  }

  private String decrypt(UUID userId, InvestmentIncome entry, String column, byte[] value) {
    return decrypt(userId, TABLE, entry.getId(), column, value);
  }

  private String decrypt(UUID userId, String table, UUID recordId, String column, byte[] value) {
    if (value == null) {
      return null;
    }
    try {
      return cryptoService.decryptString(value, new CryptoContext(userId, table, column, recordId));
    } catch (CryptoException exception) {
      return null;
    }
  }

  private static String parseType(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("incomeType", "Informe o tipo do provento.");
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!TYPES.contains(normalized)) {
      throw new ValidationException("incomeType", "Tipo de provento inválido.");
    }
    return normalized;
  }

  private static BigDecimal parseAmount(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("amount", "Informe o valor do provento.");
    }
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(value.trim());
    } catch (NumberFormatException exception) {
      throw new ValidationException("amount", "Valor do provento inválido.");
    }
    BigDecimal scaled = parsed.setScale(2, RoundingMode.HALF_EVEN);
    if (scaled.signum() <= 0) {
      throw new ValidationException("amount", "O valor do provento deve ser positivo.");
    }
    return scaled;
  }

  private static String optionalText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLength) {
      throw new ValidationException(
          field, "A descrição deve ter no máximo " + maxLength + " caracteres.");
    }
    return trimmed;
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

  private record Target(String type, UUID providerInvestmentId, UUID investmentPositionId) {}
}
