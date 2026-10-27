package com.deltafinance.investments.application;

import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyDataClient;
import com.deltafinance.integrations.infrastructure.PluggyException;
import com.deltafinance.integrations.infrastructure.PluggyItemInvalidException;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyRateLimitedException;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
import com.deltafinance.investments.domain.ProviderInvestment;
import com.deltafinance.investments.domain.ProviderInvestmentData;
import com.deltafinance.investments.domain.ProviderInvestmentRepository;
import com.deltafinance.investments.domain.ProviderInvestmentScope;
import com.deltafinance.investments.domain.ProviderInvestmentSync;
import com.deltafinance.investments.domain.ProviderInvestmentSyncRepository;
import com.deltafinance.investments.domain.ProviderInvestmentTransaction;
import com.deltafinance.investments.domain.ProviderInvestmentTransactionData;
import com.deltafinance.investments.domain.ProviderInvestmentTransactionRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.sync.SyncCancellationRegistry;
import com.deltafinance.shared.sync.SyncCancelledException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.ImportStats;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Importa posições e movimentações de investimento de um item.
 *
 * <p>Investimento não é conta: o vínculo é com a conexão/item e não há {@code accountId}. A
 * cobertura é derivada de {@code /items/{id}/resources}; {@code products} não é prova. Falhas de um
 * item não apagam dados anteriores e são isoladas pelo serviço de sincronização. Nenhum valor é
 * estimado quando o provedor não o envia.
 */
@Service
public class InvestmentImportService {

  private static final Logger LOG = LoggerFactory.getLogger(InvestmentImportService.class);
  private static final String INVESTMENT_TABLE = "provider_investment";
  private static final String TRANSACTION_TABLE = "provider_investment_transaction";
  private static final Set<String> INVESTMENT_RESOURCE_TYPES =
      Set.of(
          "BANK_FIXED_INCOME", "CREDIT_FIXED_INCOME", "VARIABLE_INCOME", "TREASURE_TITLE", "FUND");
  private static final Set<String> RESOURCE_STATUS_AVAILABLE = Set.of("AVAILABLE");
  private static final Set<String> RESOURCE_STATUS_PENDING =
      Set.of("PENDING_AUTHORISATION", "PENDING_AUTHORIZATION");
  private static final Set<String> RESOURCE_STATUS_UNAVAILABLE =
      Set.of("UNAVAILABLE", "TEMPORARILY_UNAVAILABLE");

  private final ProviderInvestmentRepository investmentRepository;
  private final ProviderInvestmentTransactionRepository transactionRepository;
  private final ProviderInvestmentSyncRepository syncRepository;
  private final PluggyAuthClient authClient;
  private final PluggyDataClient dataClient;
  private final CryptoService cryptoService;
  private final IndexHmacService hmacService;
  private final SyncCancellationRegistry cancellationRegistry;
  private final AuditService auditService;
  private final Clock clock;

  public InvestmentImportService(
      ProviderInvestmentRepository investmentRepository,
      ProviderInvestmentTransactionRepository transactionRepository,
      ProviderInvestmentSyncRepository syncRepository,
      PluggyAuthClient authClient,
      PluggyDataClient dataClient,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      SyncCancellationRegistry cancellationRegistry,
      AuditService auditService,
      Clock clock) {
    this.investmentRepository = investmentRepository;
    this.transactionRepository = transactionRepository;
    this.syncRepository = syncRepository;
    this.authClient = authClient;
    this.dataClient = dataClient;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.cancellationRegistry = cancellationRegistry;
    this.auditService = auditService;
    this.clock = clock;
  }

  /**
   * Importa investimentos de um item.
   *
   * <p>O chamador deve isolar falhas: uma exceção aqui não pode interromper contas e transações.
   * {@link SyncCancelledException} e {@link PluggyRateLimitedException} são propagadas para que o
   * cancelamento e o backoff do provedor sejam respeitados.
   */
  public ImportStats importInvestments(
      PluggyIntegration integration, UUID connectionId, PluggyModels.PluggyItem item) {
    UUID userId = integration.getUserId();
    Instant now = clock.now();
    String apiKey = apiKey(integration);
    ScopeResult scope = resolveScope(integration, apiKey, connectionId, item);
    ImportStats stats = ImportStats.empty();
    try {
      PluggyModels.PluggyInvestmentPage page =
          listInvestments(integration, apiKey, item.externalId());
      Set<String> seen = new HashSet<>();
      for (PluggyModels.PluggyInvestment imported : page.items()) {
        cancellationRegistry.checkpoint();
        seen.add(hex(hmacService.externalIdHash("pluggy", imported.externalId())));
        ImportStats investmentStats =
            upsertInvestment(userId, connectionId, imported, now, integration, apiKey);
        stats = stats.plus(investmentStats);
      }
      if (page.complete()) {
        markMissingInvestments(userId, connectionId, seen, now);
      }
      if (!page.items().isEmpty()) {
        scope = scope.asAvailable();
      }
      saveSync(userId, connectionId, scope, item.resourcesCollectedAt(), null);
      auditService.record(
          userId,
          AuditEventType.INVESTMENTS_SYNCED,
          "pluggy_connection",
          connectionId,
          Map.of(
              "scope", scope.scope().name(),
              "read", stats.read(),
              "created", stats.created(),
              "updated", stats.updated(),
              "failed", stats.failed(),
              "resources", scope.resourceCount(),
              "availableResources", scope.available(),
              "pendingResources", scope.pending()));
      return stats;
    } catch (SyncCancelledException | PluggyRateLimitedException exception) {
      throw exception;
    } catch (PluggyUnauthorizedException exception) {
      // O sync já renovou a chave uma vez; uma segunda falha é tratada pelo chamador.
      saveSync(
          userId,
          connectionId,
          scope.asError("UNAUTHORIZED"),
          item.resourcesCollectedAt(),
          "UNAUTHORIZED");
      throw exception;
    } catch (PluggyException exception) {
      saveSync(
          userId,
          connectionId,
          scope.asError("PROVIDER_UNAVAILABLE"),
          item.resourcesCollectedAt(),
          "PROVIDER_UNAVAILABLE");
      auditService.record(
          userId,
          AuditEventType.INVESTMENTS_SYNC_FAILED,
          "pluggy_connection",
          connectionId,
          Map.of("scope", scope.scope().name(), "errorCode", "PROVIDER_UNAVAILABLE"));
      LOG.warn(
          "Falha ao importar investimentos connectionId={} tipo={}",
          connectionId,
          exception.getClass().getSimpleName());
      throw exception;
    } catch (RuntimeException exception) {
      saveSync(
          userId,
          connectionId,
          scope.asError("IMPORT_FAILED"),
          item.resourcesCollectedAt(),
          "IMPORT_FAILED");
      auditService.record(
          userId,
          AuditEventType.INVESTMENTS_SYNC_FAILED,
          "pluggy_connection",
          connectionId,
          Map.of("scope", scope.scope().name(), "errorCode", "IMPORT_FAILED"));
      LOG.warn(
          "Falha inesperada ao importar investimentos connectionId={} tipo={}",
          connectionId,
          exception.getClass().getSimpleName());
      throw exception;
    }
  }

  private ImportStats upsertInvestment(
      UUID userId,
      UUID connectionId,
      PluggyModels.PluggyInvestment imported,
      Instant now,
      PluggyIntegration integration,
      String apiKey) {
    byte[] externalHash = hmacService.externalIdHash("pluggy", imported.externalId());
    ProviderInvestment existing =
        investmentRepository
            .findByUserIdAndConnectionIdAndExternalInvestmentIdHash(
                userId, connectionId, externalHash)
            .orElse(null);
    ProviderInvestment investment =
        existing == null ? new ProviderInvestment(userId, connectionId, externalHash) : existing;
    investment.apply(toData(userId, investment.getId(), imported), now);
    investmentRepository.save(investment);

    ImportStats transactionStats =
        importTransactions(userId, investment, imported, now, integration, apiKey);
    return new ImportStats(
        1 + transactionStats.read(),
        (existing == null ? 1 : 0) + transactionStats.created(),
        (existing == null ? 0 : 1) + transactionStats.updated(),
        transactionStats.skipped(),
        transactionStats.failed());
  }

  private ImportStats importTransactions(
      UUID userId,
      ProviderInvestment investment,
      PluggyModels.PluggyInvestment imported,
      Instant now,
      PluggyIntegration integration,
      String apiKey) {
    PluggyModels.PluggyInvestmentTransactionPage page;
    try {
      page = listTransactions(integration, apiKey, imported.externalId());
    } catch (SyncCancelledException
        | PluggyRateLimitedException
        | PluggyUnauthorizedException exception) {
      throw exception;
    } catch (PluggyException exception) {
      LOG.warn(
          "Falha ao ler transações de investimento connectionId={} tipo={}",
          investment.getConnectionId(),
          exception.getClass().getSimpleName());
      return new ImportStats(0, 0, 0, 0, 1);
    }

    ImportStats stats = ImportStats.empty();
    Set<String> seen = new HashSet<>();
    for (PluggyModels.PluggyInvestmentTransaction importedTransaction : page.items()) {
      cancellationRegistry.checkpoint();
      String type = normalizeTransactionType(importedTransaction.type());
      if (type == null) {
        stats = stats.plus(new ImportStats(0, 0, 0, 0, 1));
        continue;
      }
      byte[] dedupKey = transactionDedupKey(imported, importedTransaction);
      seen.add(hex(dedupKey));
      ProviderInvestmentTransaction existing =
          transactionRepository
              .findByUserIdAndProviderInvestmentIdAndDedupKey(userId, investment.getId(), dedupKey)
              .orElse(null);
      ProviderInvestmentTransaction transaction =
          existing == null
              ? new ProviderInvestmentTransaction(userId, investment.getId(), dedupKey)
              : existing;
      transaction.apply(toData(userId, transaction.getId(), importedTransaction, type), now);
      transactionRepository.save(transaction);
      stats =
          stats.plus(new ImportStats(1, existing == null ? 1 : 0, existing == null ? 0 : 1, 0, 0));
    }
    if (page.complete()) {
      markMissingTransactions(userId, investment.getId(), seen, now);
    }
    return stats;
  }

  private void markMissingInvestments(
      UUID userId, UUID connectionId, Set<String> seen, Instant now) {
    for (ProviderInvestment investment :
        investmentRepository.findByUserIdAndConnectionId(userId, connectionId)) {
      if (seen.contains(hex(investment.getExternalInvestmentIdHash()))) {
        continue;
      }
      if (investment.isAvailable()) {
        investment.markUnavailable(now);
        investmentRepository.save(investment);
      }
    }
  }

  private void markMissingTransactions(
      UUID userId, UUID providerInvestmentId, Set<String> seen, Instant now) {
    for (ProviderInvestmentTransaction transaction :
        transactionRepository.findByUserIdAndProviderInvestmentId(userId, providerInvestmentId)) {
      if (seen.contains(hex(transaction.getDedupKey())) || transaction.isRemoved()) {
        continue;
      }
      transaction.markRemoved(now);
      transactionRepository.save(transaction);
    }
  }

  private ProviderInvestmentData toData(
      UUID userId, UUID investmentId, PluggyModels.PluggyInvestment imported) {
    String type = normalizeType(imported.type());
    String status = normalizeStatus(imported.status());
    String currency = currency(imported.currency());
    return new ProviderInvestmentData(
        encryptInvestment(userId, investmentId, "external_investment_id", imported.externalId()),
        encryptInvestmentOptional(userId, investmentId, "name", imported.name()),
        encryptInvestmentOptional(userId, investmentId, "code", imported.code()),
        encryptInvestmentOptional(userId, investmentId, "isin", imported.isin()),
        encryptInvestmentOptional(userId, investmentId, "owner", imported.owner()),
        encryptInvestmentOptional(userId, investmentId, "institution", imported.institution()),
        encryptInvestmentOptional(userId, investmentId, "issuer", imported.issuer()),
        type,
        normalizeUpper(imported.subtype(), 48),
        status,
        currency,
        encryptInvestmentAmount(userId, investmentId, "quantity", imported.quantity()),
        encryptInvestmentAmount(userId, investmentId, "unit_value", imported.unitValue()),
        encryptInvestmentAmount(userId, investmentId, "amount", imported.amount()),
        encryptInvestmentAmount(userId, investmentId, "balance", imported.balance()),
        encryptInvestmentAmount(userId, investmentId, "amount_original", imported.amountOriginal()),
        encryptInvestmentAmount(userId, investmentId, "amount_profit", imported.amountProfit()),
        encryptInvestmentAmount(
            userId, investmentId, "amount_withdrawal", imported.amountWithdrawal()),
        encryptInvestmentAmount(userId, investmentId, "taxes", imported.taxes()),
        encryptInvestmentAmount(userId, investmentId, "taxes2", imported.taxes2()),
        encryptInvestmentAmount(userId, investmentId, "rate", imported.rate()),
        normalizeUpper(imported.rateType(), 32),
        encryptInvestmentAmount(
            userId, investmentId, "fixed_annual_rate", imported.fixedAnnualRate()),
        encryptInvestmentAmount(userId, investmentId, "last_month_rate", imported.lastMonthRate()),
        encryptInvestmentAmount(userId, investmentId, "annual_rate", imported.annualRate()),
        encryptInvestmentAmount(
            userId, investmentId, "last_twelve_months_rate", imported.lastTwelveMonthsRate()),
        imported.referenceDate(),
        imported.purchaseDate(),
        imported.dueDate(),
        imported.gracePeriodDate(),
        imported.providerUpdatedAt());
  }

  private ProviderInvestmentTransactionData toData(
      UUID userId,
      UUID transactionId,
      PluggyModels.PluggyInvestmentTransaction imported,
      String type) {
    return new ProviderInvestmentTransactionData(
        encryptTransactionOptional(
            userId, transactionId, "external_transaction_id", imported.externalId()),
        imported.externalId() == null || imported.externalId().isBlank()
            ? null
            : hmacService.externalIdHash("pluggy-investment-transaction", imported.externalId()),
        type,
        normalizeUpper(imported.movementType(), 16),
        imported.tradeDate(),
        imported.transactionDate(),
        encryptTransactionAmount(userId, transactionId, "quantity", imported.quantity()),
        encryptTransactionAmount(userId, transactionId, "unit_value", imported.unitValue()),
        encryptTransactionAmount(userId, transactionId, "amount", imported.amount()),
        encryptTransactionAmount(userId, transactionId, "net_amount", imported.netAmount()),
        encryptTransactionOptional(userId, transactionId, "description", imported.description()),
        encryptTransactionOptional(
            userId, transactionId, "brokerage_number", imported.brokerageNumber()),
        encryptTransactionOptional(
            userId, transactionId, "expenses", expenses(imported.expenses())));
  }

  private byte[] transactionDedupKey(
      PluggyModels.PluggyInvestment imported,
      PluggyModels.PluggyInvestmentTransaction transaction) {
    if (transaction.externalId() != null && !transaction.externalId().isBlank()) {
      return hmacService.externalIdHash("pluggy-investment-transaction", transaction.externalId());
    }
    return hmacService.fingerprint(
        imported.externalId(),
        nvl(transaction.type()),
        nvl(transaction.transactionDate()),
        nvl(transaction.amount()),
        nvl(transaction.quantity()),
        nvl(transaction.description()));
  }

  private ScopeResult resolveScope(
      PluggyIntegration integration,
      String apiKey,
      UUID connectionId,
      PluggyModels.PluggyItem item) {
    try {
      return doResolveScope(apiKey, connectionId, item);
    } catch (PluggyUnauthorizedException exception) {
      return doResolveScope(authClient.refreshApiKey(integration), connectionId, item);
    }
  }

  private ScopeResult doResolveScope(
      String apiKey, UUID connectionId, PluggyModels.PluggyItem item) {
    try {
      PluggyModels.PluggyItemResourcePage page =
          dataClient.listItemResources(apiKey, item.externalId());
      int total = 0;
      int available = 0;
      int pending = 0;
      int unavailable = 0;
      for (PluggyModels.PluggyItemResource resource : page.items()) {
        if (resource.type() == null || !INVESTMENT_RESOURCE_TYPES.contains(resource.type())) {
          continue;
        }
        total++;
        String status = resource.status() == null ? "" : resource.status();
        if (RESOURCE_STATUS_AVAILABLE.contains(status)) {
          available++;
        } else if (RESOURCE_STATUS_PENDING.contains(status)) {
          pending++;
        } else if (RESOURCE_STATUS_UNAVAILABLE.contains(status)) {
          unavailable++;
        }
      }
      ProviderInvestmentScope scope;
      if (available > 0) {
        scope = ProviderInvestmentScope.AVAILABLE;
      } else if (pending > 0) {
        scope = ProviderInvestmentScope.PENDING_AUTHORIZATION;
      } else if (unavailable > 0 || item.resourcesCollectedAt() != null) {
        scope = ProviderInvestmentScope.NOT_SHARED;
      } else {
        scope = ProviderInvestmentScope.UNKNOWN;
      }
      return new ScopeResult(scope, total, available, pending, unavailable, null);
    } catch (SyncCancelledException
        | PluggyRateLimitedException
        | PluggyUnauthorizedException exception) {
      throw exception;
    } catch (PluggyItemInvalidException exception) {
      // Conector direto pode não expor recursos; isso não invalida o item.
      return new ScopeResult(ProviderInvestmentScope.UNKNOWN, 0, 0, 0, 0, "RESOURCES_UNSUPPORTED");
    } catch (PluggyException exception) {
      LOG.warn(
          "Falha ao verificar cobertura de investimentos connectionId={} tipo={}",
          connectionId,
          exception.getClass().getSimpleName());
      return new ScopeResult(ProviderInvestmentScope.ERROR, 0, 0, 0, 0, "RESOURCES_UNAVAILABLE");
    }
  }

  private void saveSync(
      UUID userId,
      UUID connectionId,
      ScopeResult scope,
      Instant resourcesCollectedAt,
      String errorCode) {
    ProviderInvestmentSync sync =
        syncRepository
            .findByUserIdAndConnectionId(userId, connectionId)
            .orElseGet(() -> new ProviderInvestmentSync(userId, connectionId));
    sync.apply(
        scope.scope(),
        scope.resourceCount(),
        scope.available(),
        scope.pending(),
        scope.unavailable(),
        resourcesCollectedAt,
        clock.now(),
        errorCode);
    syncRepository.save(sync);
  }

  private String apiKey(PluggyIntegration integration) {
    try {
      return authClient.getApiKey(integration);
    } catch (PluggyUnauthorizedException exception) {
      return authClient.refreshApiKey(integration);
    }
  }

  private PluggyModels.PluggyInvestmentPage listInvestments(
      PluggyIntegration integration, String apiKey, String itemId) {
    try {
      return dataClient.listInvestments(apiKey, itemId);
    } catch (PluggyUnauthorizedException exception) {
      return dataClient.listInvestments(authClient.refreshApiKey(integration), itemId);
    }
  }

  private PluggyModels.PluggyInvestmentTransactionPage listTransactions(
      PluggyIntegration integration, String apiKey, String investmentId) {
    try {
      return dataClient.listInvestmentTransactions(apiKey, investmentId);
    } catch (PluggyUnauthorizedException exception) {
      return dataClient.listInvestmentTransactions(
          authClient.refreshApiKey(integration), investmentId);
    }
  }

  private byte[] encryptInvestment(UUID userId, UUID recordId, String column, String value) {
    return cryptoService.encrypt(
        value, new CryptoContext(userId, INVESTMENT_TABLE, column, recordId));
  }

  private byte[] encryptInvestmentOptional(
      UUID userId, UUID recordId, String column, String value) {
    return value == null || value.isBlank()
        ? null
        : encryptInvestment(userId, recordId, column, value);
  }

  private byte[] encryptInvestmentAmount(
      UUID userId, UUID recordId, String column, BigDecimal value) {
    return value == null
        ? null
        : cryptoService.encrypt(
            value.toPlainString(), new CryptoContext(userId, INVESTMENT_TABLE, column, recordId));
  }

  private byte[] encryptTransaction(UUID userId, UUID recordId, String column, String value) {
    return cryptoService.encrypt(
        value, new CryptoContext(userId, TRANSACTION_TABLE, column, recordId));
  }

  private byte[] encryptTransactionOptional(
      UUID userId, UUID recordId, String column, String value) {
    return value == null || value.isBlank()
        ? null
        : encryptTransaction(userId, recordId, column, value);
  }

  private byte[] encryptTransactionAmount(
      UUID userId, UUID recordId, String column, BigDecimal value) {
    return value == null
        ? null
        : cryptoService.encrypt(
            value.toPlainString(), new CryptoContext(userId, TRANSACTION_TABLE, column, recordId));
  }

  private String expenses(JsonNode node) {
    return node == null || node.isNull() || node.isMissingNode() ? null : node.toString();
  }

  private static String hex(byte[] value) {
    return value == null ? "" : HexFormat.of().formatHex(value);
  }

  private static String nvl(Object value) {
    return value == null ? "" : value.toString();
  }

  private static String normalizeType(String value) {
    String normalized = normalizeUpper(value, 32);
    if (normalized == null) {
      return "OTHER";
    }
    return switch (normalized) {
      case "RENDA_FIXA" -> "FIXED_INCOME";
      case "FUNDO_MUTUO", "FUNDO_MÚTUO" -> "MUTUAL_FUND";
      case "ACAO", "AÇÃO" -> "EQUITY";
      case "TITULO", "TÍTULO" -> "SECURITY";
      default -> normalized;
    };
  }

  private static String normalizeStatus(String value) {
    String normalized = normalizeUpper(value, 32);
    if (normalized == null) {
      return "UNKNOWN";
    }
    return switch (normalized) {
      case "ATIVO" -> "ACTIVE";
      case "PENDENTE" -> "PENDING";
      case "RETIRADA_TOTAL", "TOTAL_WITHDRAWAL" -> "CLOSED";
      default -> normalized;
    };
  }

  private static String normalizeTransactionType(String value) {
    String normalized = normalizeUpper(value, 32);
    if (normalized == null) {
      return null;
    }
    return switch (normalized) {
      case "APLICACAO", "APLICAÇÃO" -> "BUY";
      case "RETIRADA", "RESGATE" -> "SELL";
      case "PROVENTO", "RENDIMENTO" -> "INTEREST";
      default -> normalized;
    };
  }

  private static String normalizeUpper(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
  }

  private static String currency(String value) {
    String normalized = normalizeUpper(value, 3);
    return normalized == null || normalized.length() != 3 ? "BRL" : normalized;
  }

  private record ScopeResult(
      ProviderInvestmentScope scope,
      int resourceCount,
      int available,
      int pending,
      int unavailable,
      String errorCode) {

    ScopeResult asAvailable() {
      return new ScopeResult(
          ProviderInvestmentScope.AVAILABLE,
          resourceCount,
          available,
          pending,
          unavailable,
          errorCode);
    }

    ScopeResult asError(String code) {
      return new ScopeResult(
          ProviderInvestmentScope.ERROR, resourceCount, available, pending, unavailable, code);
    }
  }
}
