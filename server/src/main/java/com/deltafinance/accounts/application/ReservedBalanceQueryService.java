package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.accounts.domain.ReservedBalance;
import com.deltafinance.accounts.domain.ReservedBalanceRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Leitura dos saldos reservados de uma conta. */
@Service
public class ReservedBalanceQueryService {

  private static final String TABLE = "reserved_balance";
  private static final int STALE_DAYS = 30;

  private final ReservedBalanceRepository reservedBalanceRepository;
  private final FinancialAccountRepository accountRepository;
  private final CryptoService cryptoService;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public ReservedBalanceQueryService(
      ReservedBalanceRepository reservedBalanceRepository,
      FinancialAccountRepository accountRepository,
      CryptoService cryptoService,
      JsonMapper jsonMapper,
      Clock clock) {
    this.reservedBalanceRepository = reservedBalanceRepository;
    this.accountRepository = accountRepository;
    this.cryptoService = cryptoService;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  public List<ReservedBalanceView> list(UUID userId, UUID accountId) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    return reservedBalanceRepository
        .findByUserIdAndAccountIdAndRemovedAtIsNullOrderByCreatedAtAsc(userId, accountId)
        .stream()
        .map(reserved -> toView(reserved, account.getCurrency()))
        .toList();
  }

  public ReservedBalanceView get(UUID userId, UUID reservedBalanceId) {
    ReservedBalance reserved =
        reservedBalanceRepository
            .findByIdAndUserId(reservedBalanceId, userId)
            .orElseThrow(NotFoundException::new);
    FinancialAccount account =
        accountRepository
            .findByIdAndUserId(reserved.getAccountId(), userId)
            .orElseThrow(NotFoundException::new);
    return toView(reserved, account.getCurrency());
  }

  private ReservedBalanceView toView(ReservedBalance reserved, String accountCurrency) {
    String providerName = decrypt(reserved, reserved.getProviderNameEnc(), "provider_name");
    String displayName = decrypt(reserved, reserved.getDisplayNameEnc(), "display_name");
    boolean nameCustomized = displayName != null && !displayName.isBlank();
    String name =
        nameCustomized
            ? displayName
            : providerName == null || providerName.isBlank() ? "Reserva sem nome" : providerName;
    String amount = decrypt(reserved, reserved.getAmountEnc(), "amount");
    String currency =
        reserved.getCurrency() == null || reserved.getCurrency().isBlank()
            ? accountCurrency
            : reserved.getCurrency();
    LocalDate referenceDate = reserved.getReferenceDate();
    boolean stale =
        referenceDate != null && referenceDate.isBefore(clock.today().minusDays(STALE_DAYS));
    String details = decrypt(reserved, reserved.getDetailsEnc(), "details");
    return new ReservedBalanceView(
        reserved.getId(),
        reserved.getAccountId(),
        name,
        providerName,
        nameCustomized,
        amount == null
            ? "0.00"
            : new BigDecimal(amount).setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
        currency,
        referenceDate,
        stale,
        amounts(details, currency));
  }

  private List<ReservedBalanceAmountView> amounts(String details, String fallbackCurrency) {
    if (details == null || details.isBlank()) {
      return List.of();
    }
    JsonNode root;
    try {
      root = jsonMapper.readTree(details);
    } catch (RuntimeException exception) {
      return List.of();
    }
    if (!root.isArray()) {
      return List.of();
    }
    List<ReservedBalanceAmountView> amounts = new ArrayList<>();
    for (JsonNode node : root) {
      JsonNode remuneration = node.path("remuneration");
      amounts.add(
          new ReservedBalanceAmountView(
              decimalText(node, "amount"),
              text(node, "currencyCode") == null ? fallbackCurrency : text(node, "currencyCode"),
              text(remuneration, "indexer"),
              text(remuneration, "rateType"),
              text(remuneration, "calculation"),
              text(remuneration, "ratePeriodicity"),
              decimalText(remuneration, "preFixedRate"),
              decimalText(remuneration, "postFixedIndexerPercentage"),
              text(remuneration, "indexerAdditionalInfo")));
    }
    return amounts;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString(null);
  }

  private static String decimalText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (value.isNumber()) {
      return value.decimalValue().toPlainString();
    }
    String text = value.asString(null);
    return text == null || text.isBlank() ? null : text;
  }

  private String decrypt(ReservedBalance reserved, byte[] ciphertext, String column) {
    if (ciphertext == null) {
      return null;
    }
    try {
      return cryptoService.decryptString(
          ciphertext, new CryptoContext(reserved.getUserId(), TABLE, column, reserved.getId()));
    } catch (CryptoException exception) {
      return null;
    }
  }
}
