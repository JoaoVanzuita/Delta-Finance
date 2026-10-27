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
import com.deltafinance.shared.sync.SyncCancellationRegistry;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
  private final SyncCancellationRegistry cancellationRegistry;
  private final Clock clock;

  public AccountImportService(
      FinancialAccountRepository accountRepository,
      CardProfileRepository cardProfileRepository,
      PluggyAuthClient authClient,
      PluggyDataClient dataClient,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      SyncCancellationRegistry cancellationRegistry,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.cardProfileRepository = cardProfileRepository;
    this.authClient = authClient;
    this.dataClient = dataClient;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.cancellationRegistry = cancellationRegistry;
    this.clock = clock;
  }

  public int importAccounts(PluggyIntegration integration, UUID connectionId, String itemId) {
    cancellationRegistry.checkpoint();
    String apiKey = authClient.getApiKey(integration);
    PluggyModels.PluggyItem item;
    PluggyModels.PluggyAccountPage page;
    try {
      item = dataClient.getItem(apiKey, itemId);
      page = dataClient.listAccounts(apiKey, itemId);
    } catch (PluggyUnauthorizedException exception) {
      apiKey = authClient.refreshApiKey(integration);
      item = dataClient.getItem(apiKey, itemId);
      page = dataClient.listAccounts(apiKey, itemId);
    }

    Instant now = clock.now();
    List<byte[]> seenHashes = new ArrayList<>();
    for (PluggyModels.PluggyAccount imported : page.items()) {
      cancellationRegistry.checkpoint();
      seenHashes.add(hmacService.externalIdHash("pluggy", imported.externalId()));
      upsert(integration.getUserId(), connectionId, imported, item.institutionName(), now);
    }
    // Leitura completa: contas ausentes saem dos saldos, mas preservam histórico e transações.
    if (page.complete()) {
      markMissingUnavailable(connectionId, seenHashes);
    }
    return page.items().size();
  }

  private void markMissingUnavailable(UUID connectionId, List<byte[]> seenHashes) {
    for (FinancialAccount account :
        accountRepository.findByConnectionIdOrderByCreatedAtAsc(connectionId)) {
      if (account.getStatus() == AccountStatus.UNAVAILABLE
          || account.getStatus() == AccountStatus.INACTIVE
          || account.getExternalAccountIdHash() == null) {
        continue;
      }
      boolean seen =
          seenHashes.stream()
              .anyMatch(hash -> hmacService.matches(hash, account.getExternalAccountIdHash()));
      if (!seen) {
        account.markUnavailable();
        accountRepository.save(account);
      }
    }
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
    Integer providerClosing = dayOrNull(creditData, "balanceCloseDate");
    Integer providerDue = dayOrNull(creditData, "balanceDueDate");
    CardProfile existing =
        cardProfileRepository.findByAccountIdAndUserId(accountId, userId).orElse(null);

    // Sem ciclo utilizável do provedor, não invente 1/1: o cartão fica sem perfil até o usuário
    // configurar. Se já existe perfil (do usuário ou de um provedor anterior), preserve-o.
    if (existing == null && (providerClosing == null || providerDue == null)) {
      return;
    }

    CardProfile profile =
        existing == null
            ? new CardProfile(
                userId, accountId, providerClosing, providerDue, CardProfileSource.PROVIDER)
            : existing;
    if (profile.getSource() != CardProfileSource.USER
        && providerClosing != null
        && providerDue != null) {
      profile.applyProviderCycle(providerClosing, providerDue);
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
    if (type == null || type.isBlank()) {
      return AccountType.UNKNOWN;
    }
    return switch (type.trim().toUpperCase(java.util.Locale.ROOT)) {
      case "BANK" -> AccountType.BANK;
      case "CREDIT" -> AccountType.CREDIT;
      case "CASH" -> AccountType.CASH;
      case "WALLET" -> AccountType.WALLET;
      default -> AccountType.UNKNOWN;
    };
  }

  private static Integer dayOrNull(JsonNode creditData, String field) {
    if (creditData == null || creditData.isMissingNode() || creditData.isNull()) {
      return null;
    }
    String text = creditData.path(field).asString(null);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(text).getDayOfMonth();
    } catch (RuntimeException exception) {
      try {
        return Instant.parse(text).atZone(ZoneId.of("America/Sao_Paulo")).getDayOfMonth();
      } catch (RuntimeException ignored) {
        return null;
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
