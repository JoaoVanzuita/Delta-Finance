package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.AccountStatus;
import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.accounts.domain.CardProfileRepository;
import com.deltafinance.accounts.domain.CardProfileSource;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyDataClient;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Importa contas bancárias e cartões de um item.
 *
 * <p>O upsert é por {@code (connection_id, external_account_id_hash)}; IDs locais nunca mudam.
 * Dados ausentes ficam nulos (a UI sinaliza), sem estimativas.
 */
@Service
public class AccountImportService {

  private static final String ACCOUNT_TABLE = "financial_account";
  private static final String CARD_TABLE = "card_profile";

  private final FinancialAccountRepository accountRepository;
  private final CardProfileRepository cardProfileRepository;
  private final PluggyAuthClient authClient;
  private final PluggyDataClient dataClient;
  private final CryptoService cryptoService;
  private final IndexHmacService hmacService;
  private final Clock clock;

  public AccountImportService(
      FinancialAccountRepository accountRepository,
      CardProfileRepository cardProfileRepository,
      PluggyAuthClient authClient,
      PluggyDataClient dataClient,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.cardProfileRepository = cardProfileRepository;
    this.authClient = authClient;
    this.dataClient = dataClient;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.clock = clock;
  }

  public int importAccounts(PluggyIntegration integration, UUID connectionId, String itemId) {
    String apiKey = authClient.getApiKey(integration);
    PluggyModels.PluggyItem item;
    List<PluggyModels.PluggyAccount> accounts;
    try {
      item = dataClient.getItem(apiKey, itemId);
      accounts = dataClient.listAccounts(apiKey, itemId);
    } catch (PluggyUnauthorizedException exception) {
      apiKey = authClient.refreshApiKey(integration);
      item = dataClient.getItem(apiKey, itemId);
      accounts = dataClient.listAccounts(apiKey, itemId);
    }

    Instant now = clock.now();
    for (PluggyModels.PluggyAccount imported : accounts) {
      upsert(integration.getUserId(), connectionId, imported, item.institutionName(), now);
    }
    return accounts.size();
  }

  private void upsert(
      UUID userId,
      UUID connectionId,
      PluggyModels.PluggyAccount imported,
      String institutionName,
      Instant now) {
    byte[] externalHash = hmacService.externalIdHash("pluggy", imported.externalId());
    FinancialAccount account =
        accountRepository
            .findByConnectionIdAndExternalAccountIdHash(connectionId, externalHash)
            .orElseGet(
                () ->
                    FinancialAccount.imported(
                        userId,
                        connectionId,
                        mapType(imported.type()),
                        imported.subtype(),
                        imported.currency()));

    account.updateSubtype(imported.subtype());
    byte[] externalEnc =
        cryptoService.encrypt(
            imported.externalId(),
            new CryptoContext(userId, ACCOUNT_TABLE, "external_account_id", account.getId()));
    byte[] institutionEnc =
        institutionName == null || institutionName.isBlank()
            ? null
            : cryptoService.encrypt(
                institutionName,
                new CryptoContext(userId, ACCOUNT_TABLE, "institution_name", account.getId()));
    byte[] aliasEnc =
        imported.name() == null || imported.name().isBlank()
            ? null
            : cryptoService.encrypt(
                imported.name(),
                new CryptoContext(userId, ACCOUNT_TABLE, "alias", account.getId()));
    byte[] balanceEnc =
        imported.balance() == null
            ? null
            : cryptoService.encrypt(
                imported.balance().toPlainString(),
                new CryptoContext(userId, ACCOUNT_TABLE, "balance", account.getId()));

    account.applyImported(
        externalEnc, externalHash, institutionEnc, aliasEnc, balanceEnc, now, AccountStatus.ACTIVE);
    accountRepository.save(account);

    if (account.getType() == AccountType.CREDIT) {
      upsertCardProfile(userId, account.getId(), imported.creditData());
    }
  }

  private void upsertCardProfile(UUID userId, UUID accountId, JsonNode creditData) {
    CardProfile profile =
        cardProfileRepository
            .findByAccountIdAndUserId(accountId, userId)
            .orElseGet(
                () ->
                    new CardProfile(
                        userId,
                        accountId,
                        dayOrDefault(creditData, "balanceCloseDate", 1),
                        dayOrDefault(creditData, "balanceDueDate", 1),
                        CardProfileSource.PROVIDER));

    int closingDay = dayOrDefault(creditData, "balanceCloseDate", profile.getClosingDay());
    int dueDay = dayOrDefault(creditData, "balanceDueDate", profile.getDueDay());
    if (profile.getSource() != CardProfileSource.USER) {
      profile.applyProviderCycle(closingDay, dueDay);
    }

    BigDecimal limit = decimal(creditData, "creditLimit");
    if (limit == null) {
      limit = decimal(creditData, "availableCreditLimit");
    }
    if (limit != null) {
      profile.updateCreditLimit(
          cryptoService.encrypt(
              limit.toPlainString(),
              new CryptoContext(userId, CARD_TABLE, "credit_limit", profile.getId())));
    }
    if (profile.getCreditBalanceEnc() == null) {
      profile.updateCreditBalance(
          cryptoService.encrypt(
              "0.00", new CryptoContext(userId, CARD_TABLE, "credit_balance", profile.getId())));
    }
    cardProfileRepository.save(profile);
  }

  private static AccountType mapType(String type) {
    return "CREDIT".equalsIgnoreCase(type) ? AccountType.CREDIT : AccountType.BANK;
  }

  private static int dayOrDefault(JsonNode creditData, String field, int fallback) {
    if (creditData == null || creditData.isMissingNode() || creditData.isNull()) {
      return fallback;
    }
    String text = creditData.path(field).asString(null);
    if (text == null || text.isBlank()) {
      return fallback;
    }
    try {
      return LocalDate.parse(text).getDayOfMonth();
    } catch (RuntimeException exception) {
      try {
        return Instant.parse(text).atZone(ZoneId.of("America/Sao_Paulo")).getDayOfMonth();
      } catch (RuntimeException ignored) {
        return fallback;
      }
    }
  }

  private static BigDecimal decimal(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (value.isNumber()) {
      return value.decimalValue();
    }
    String text = value.asString(null);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
