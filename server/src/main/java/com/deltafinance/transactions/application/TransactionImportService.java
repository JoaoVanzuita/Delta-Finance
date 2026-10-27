package com.deltafinance.transactions.application;

import com.deltafinance.accounts.domain.AccountStatus;
import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.categories.domain.Category;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyDataClient;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.sync.SyncCancellationRegistry;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.ExternalIdentityHistory;
import com.deltafinance.transactions.domain.ExternalIdentityHistoryRepository;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Importa transações com idempotência.
 *
 * <p>Dedup por HMAC(provider, connection, account, externalId); quando o ID externo muda, a
 * reconciliação por fingerprint evita duplicar a despesa e registra o novo ID em {@code
 * external_identity_history}. {@code PENDING→POSTED} atualiza o mesmo registro.
 *
 * <p>A reconciliação de ausências só ocorre em uma leitura conclusiva e não vazia: transações que
 * deixaram de aparecer na janela ficam com {@code status=SOURCE_REMOVED} e {@code
 * reconciliation_state=SOURCE_REMOVED}, saindo imediatamente dos totais correntes. Nenhuma linha é
 * apagada; se o mesmo identificador voltar em uma leitura posterior, a marca é limpa e o status
 * efetivo é restaurado automaticamente.
 */
@Service
public class TransactionImportService {

  private static final String TABLE = "transaction";
  private static final String SOURCE = "PLUGGY";
  private static final int MONTHS_INITIAL = 12;

  private final TransactionRepository transactionRepository;
  private final FinancialAccountRepository accountRepository;
  private final CategoryRepository categoryRepository;
  private final ExternalIdentityHistoryRepository identityRepository;
  private final RefundMatcher refundMatcher;
  private final PluggyAuthClient authClient;
  private final PluggyDataClient dataClient;
  private final CryptoService cryptoService;
  private final IndexHmacService hmacService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final SyncCancellationRegistry cancellationRegistry;
  private final Clock clock;

  public TransactionImportService(
      TransactionRepository transactionRepository,
      FinancialAccountRepository accountRepository,
      CategoryRepository categoryRepository,
      ExternalIdentityHistoryRepository identityRepository,
      RefundMatcher refundMatcher,
      PluggyAuthClient authClient,
      PluggyDataClient dataClient,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      SyncCancellationRegistry cancellationRegistry,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.categoryRepository = categoryRepository;
    this.identityRepository = identityRepository;
    this.refundMatcher = refundMatcher;
    this.authClient = authClient;
    this.dataClient = dataClient;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.cancellationRegistry = cancellationRegistry;
    this.clock = clock;
  }

  /** Importação inicial de 12 meses por conta da conexão. */
  public ImportStats importInitial(PluggyIntegration integration, UUID connectionId) {
    return importInitial(integration, connectionId, false);
  }

  /**
   * Importação inicial com controle explícito de reconciliação de identidade.
   *
   * <p>{@code reconcileIdentity=true} só é usado na troca de itemId, quando o ID externo pode ter
   * mudado e o fingerprint é o único vínculo com o registro local.
   */
  public ImportStats importInitial(
      PluggyIntegration integration, UUID connectionId, boolean reconcileIdentity) {
    return importRange(
        integration, connectionId, clock.today().minusMonths(MONTHS_INITIAL), reconcileIdentity);
  }

  /**
   * Importa a janela informada para todas as contas da conexão.
   *
   * <p>Chamadas repetidas atualizam o mesmo registro local. O retorno distingue leitura, criação,
   * atualização e descarte, permitindo registrar {@code sync_run} fielmente.
   */
  public ImportStats importRange(PluggyIntegration integration, UUID connectionId, LocalDate from) {
    return importRange(integration, connectionId, from, false);
  }

  public ImportStats importRange(
      PluggyIntegration integration, UUID connectionId, LocalDate from, boolean reconcileIdentity) {
    List<FinancialAccount> accounts =
        accountRepository.findByConnectionIdOrderByCreatedAtAsc(connectionId);
    ImportStats total = ImportStats.empty();
    for (FinancialAccount account : accounts) {
      if (account.getStatus() == AccountStatus.UNAVAILABLE) {
        // Conta não retornada pela origem não deve voltar a ser consultada até reaparecer.
        continue;
      }
      cancellationRegistry.checkpoint();
      total =
          total.plus(importAccount(integration, connectionId, account, from, reconcileIdentity));
    }
    return total;
  }

  private ImportStats importAccount(
      PluggyIntegration integration,
      UUID connectionId,
      FinancialAccount account,
      LocalDate from,
      boolean reconcileIdentity) {
    cancellationRegistry.checkpoint();
    String externalAccountId = decryptAccountId(account);
    String apiKey = authClient.getApiKey(integration);
    PluggyModels.PluggyTransactionPage page;
    try {
      page = dataClient.listTransactions(apiKey, externalAccountId, from, null);
    } catch (PluggyUnauthorizedException exception) {
      apiKey = authClient.refreshApiKey(integration);
      page = dataClient.listTransactions(apiKey, externalAccountId, from, null);
    }

    int read = 0;
    int created = 0;
    int updated = 0;
    int skipped = 0;
    int failed = 0;
    Set<byte[]> seenHashes = new HashSet<>();
    for (PluggyModels.PluggyTransaction imported : page.items()) {
      cancellationRegistry.checkpoint();
      read++;
      if (imported.externalId() == null || imported.externalId().isBlank()) {
        skipped++;
        continue;
      }
      seenHashes.add(hmacService.externalIdHash(SOURCE, imported.externalId()));
      Outcome outcome =
          upsert(
              integration.getUserId(),
              connectionId,
              account,
              externalAccountId,
              imported,
              reconcileIdentity);
      switch (outcome) {
        case CREATED -> created++;
        case UPDATED -> updated++;
        case SKIPPED -> skipped++;
        case FAILED -> failed++;
        default -> {
          // Enum fechado: nenhum outcome adicional é esperado.
        }
      }
    }

    // Lista vazia é inconclusiva: não marque ausências a partir dela (FR-040/FR-046).
    // Leitura truncada/cursor inválido também é inconclusiva.
    if (read > 0 && page.complete()) {
      cancellationRegistry.checkpoint();
      updated += markMissingAsSourceRemoved(integration.getUserId(), account, from, seenHashes);
    }
    return new ImportStats(read, created, updated, skipped, failed);
  }

  private int markMissingAsSourceRemoved(
      UUID userId, FinancialAccount account, LocalDate from, Set<byte[]> seenHashes) {
    int removed = 0;
    for (Transaction candidate :
        transactionRepository.findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(
            userId, account.getId())) {
      if (candidate.getSource() != TransactionSource.IMPORTED
          || candidate.getPostedOn().isBefore(from)
          || candidate.getExternalTransactionIdHash() == null
          || candidate.getStatus() == TransactionStatus.SOURCE_REMOVED
          || candidate.getReconciliationState() == ReconciliationState.SOURCE_REMOVED) {
        continue;
      }
      boolean seen =
          seenHashes.stream()
              .anyMatch(
                  hash -> hmacService.matches(hash, candidate.getExternalTransactionIdHash()));
      if (!seen) {
        candidate.markSourceRemoved();
        transactionRepository.save(candidate);
        auditService.record(
            userId,
            AuditEventType.TRANSACTION_SOURCE_REMOVED,
            "transaction",
            candidate.getId(),
            java.util.Map.of("automatic", true));
        eventPublisher.publishEvent(
            new TransactionFinancialChangedEvent(userId, candidate.getId()));
        removed++;
      }
    }
    return removed;
  }

  private Outcome upsert(
      UUID userId,
      UUID connectionId,
      FinancialAccount account,
      String externalAccountId,
      PluggyModels.PluggyTransaction imported,
      boolean reconcileIdentity) {
    if (imported.amount() == null || imported.date() == null) {
      // Sem valor/data não invente 0,00 nem "hoje": registre a falha e siga.
      return Outcome.FAILED;
    }
    BigDecimal amount = imported.amount();
    String description = imported.description() == null ? "" : imported.description();
    LocalDate postedOn = imported.date();
    TransactionStatus status = mapStatus(imported.status());
    TransactionType type = mapType(account, amount, description);
    byte[] dedupKey =
        hmacService.dedupKey(SOURCE, connectionId, externalAccountId, imported.externalId());
    byte[] fingerprint =
        hmacService.fingerprint(
            account.getId().toString(),
            amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
            postedOn.toString(),
            normalize(description),
            installmentMetadata(imported.creditCardMetadata()));

    Transaction transaction =
        transactionRepository.findByUserIdAndDedupKey(userId, dedupKey).orElse(null);
    if (transaction == null && reconcileIdentity) {
      // O fingerprint só entra na reconciliação explícita de itemId. No fluxo normal, duas
      // compras legítimas idênticas com IDs externos diferentes continuam sendo duas transações.
      transaction =
          transactionRepository
              .findFirstByUserIdAndAccountIdAndFingerprint(userId, account.getId(), fingerprint)
              .orElse(null);
    }

    UUID categoryId =
        transaction != null && transaction.hasEdited("category")
            ? transaction.getCategoryId()
            : resolveCategory(userId, account, type, amount, imported.category(), transaction);
    UUID relatedId =
        transaction == null
            ? refundMatcher
                .findOriginal(userId, account.getId(), type, amount, postedOn, description)
                .map(Transaction::getId)
                .orElse(null)
            : null;
    if (relatedId != null && (transaction == null || !transaction.hasEdited("category"))) {
      UUID originalCategory =
          transactionRepository
              .findByIdAndUserId(relatedId, userId)
              .map(Transaction::getCategoryId)
              .orElse(null);
      if (originalCategory != null) {
        categoryId = originalCategory;
      }
    }

    byte[] amountSourceEnc =
        encrypt(userId, "amount_source", transaction == null ? null : transaction.getId(), amount);
    byte[] amountEffectiveEnc =
        encrypt(
            userId, "amount_effective", transaction == null ? null : transaction.getId(), amount);
    byte[] descriptionSourceEnc =
        encrypt(
            userId,
            "description_source",
            transaction == null ? null : transaction.getId(),
            description);
    byte[] descriptionEffectiveEnc =
        encrypt(
            userId,
            "description_effective",
            transaction == null ? null : transaction.getId(),
            description);
    byte[] externalIdEnc =
        encrypt(
            userId,
            "external_transaction_id",
            transaction == null ? null : transaction.getId(),
            imported.externalId());

    if (transaction == null) {
      transaction =
          new Transaction(
              userId,
              account.getId(),
              TransactionSource.IMPORTED,
              type,
              status,
              postedOn,
              postedOn,
              imported.currency(),
              amountSourceEnc,
              amountEffectiveEnc,
              descriptionSourceEnc,
              descriptionEffectiveEnc);
      transaction.setCategoryId(categoryId);
      if (relatedId != null) {
        transaction.linkRelatedTransaction(relatedId);
      }
      transaction.setReconciliationState(ReconciliationState.NONE);
      transaction.applyInstallments(
          installmentValue(imported.creditCardMetadata(), "installmentNumber"),
          installmentValue(imported.creditCardMetadata(), "totalInstallments"));
      transaction = transactionRepository.save(transaction);
      // Cifragens usam o ID recém-gerado; refaz para vincular o AAD correto.
      transaction.applyImported(
          type,
          status,
          postedOn,
          postedOn,
          encrypt(userId, "amount_source", transaction.getId(), amount),
          encrypt(userId, "amount_effective", transaction.getId(), amount),
          encrypt(userId, "description_source", transaction.getId(), description),
          encrypt(userId, "description_effective", transaction.getId(), description),
          encrypt(userId, "external_transaction_id", transaction.getId(), imported.externalId()),
          hmacService.externalIdHash(SOURCE, imported.externalId()),
          dedupKey,
          fingerprint);
      transactionRepository.save(transaction);
      upsertIdentityHistory(
          userId,
          account.getId(),
          transaction.getId(),
          imported.externalId(),
          encrypt(userId, "external_transaction_id", transaction.getId(), imported.externalId()));
      eventPublisher.publishEvent(
          new TransactionFinancialChangedEvent(userId, transaction.getId()));
      return Outcome.CREATED;
    }

    boolean amountEdited = transaction.hasEdited("amount");
    boolean descriptionEdited = transaction.hasEdited("description");
    boolean postedOnEdited = transaction.hasEdited("postedOn");
    boolean categoryEdited = transaction.hasEdited("category");
    boolean wasSourceRemoved =
        transaction.getReconciliationState() == ReconciliationState.SOURCE_REMOVED;
    byte[] preservedAmount = transaction.getAmountEffectiveEnc();
    byte[] preservedDescription = transaction.getDescriptionEffectiveEnc();
    LocalDate preservedPostedOn = transaction.getPostedOn();
    TransactionType preservedType = transaction.getType();

    transaction.applyImported(
        type,
        status,
        postedOn,
        postedOn,
        amountSourceEnc,
        amountEffectiveEnc,
        descriptionSourceEnc,
        descriptionEffectiveEnc,
        externalIdEnc,
        hmacService.externalIdHash(SOURCE, imported.externalId()),
        dedupKey,
        fingerprint);
    if (amountEdited) {
      transaction.setAmountEffectiveEnc(preservedAmount);
      transaction.setType(preservedType);
    }
    if (descriptionEdited) {
      transaction.setDescriptionEffectiveEnc(preservedDescription);
    }
    if (postedOnEdited) {
      transaction.setPostedOn(preservedPostedOn);
    }
    if (amountEdited || descriptionEdited || postedOnEdited || categoryEdited) {
      java.util.List<String> preservedFields = new java.util.ArrayList<>();
      if (amountEdited) {
        preservedFields.add("amount");
      }
      if (descriptionEdited) {
        preservedFields.add("description");
      }
      if (postedOnEdited) {
        preservedFields.add("postedOn");
      }
      if (categoryEdited) {
        preservedFields.add("category");
      }
      auditService.record(
          userId,
          AuditEventType.TRANSACTION_SOURCE_PRESERVED,
          "transaction",
          transaction.getId(),
          java.util.Map.of("fields", String.join(",", preservedFields)));
    }
    if (wasSourceRemoved) {
      // A transação reapareceu na origem: limpa a marca e volta aos totais com o status atual.
      transaction.restoreFromSource(status);
      auditService.record(
          userId,
          AuditEventType.TRANSACTION_SOURCE_RESTORED,
          "transaction",
          transaction.getId(),
          java.util.Map.of("automatic", true));
    }
    if (categoryId != null) {
      transaction.setCategoryId(categoryId);
    }
    if (relatedId != null && transaction.getRelatedTransactionId() == null) {
      transaction.linkRelatedTransaction(relatedId);
    }
    transaction.applyInstallments(
        installmentValue(imported.creditCardMetadata(), "installmentNumber"),
        installmentValue(imported.creditCardMetadata(), "totalInstallments"));
    transactionRepository.save(transaction);
    upsertIdentityHistory(
        userId, account.getId(), transaction.getId(), imported.externalId(), externalIdEnc);
    eventPublisher.publishEvent(new TransactionFinancialChangedEvent(userId, transaction.getId()));
    return Outcome.UPDATED;
  }

  private enum Outcome {
    CREATED,
    UPDATED,
    SKIPPED,
    FAILED
  }

  private void upsertIdentityHistory(
      UUID userId, UUID accountId, UUID transactionId, String externalId, byte[] externalIdEnc) {
    byte[] externalHash = hmacService.externalIdHash(SOURCE, externalId);
    Instant now = clock.now();
    ExternalIdentityHistory history =
        identityRepository
            .findByUserIdAndSourceAndAccountIdAndExternalIdHash(
                userId, SOURCE, accountId, externalHash)
            .orElseGet(
                () ->
                    new ExternalIdentityHistory(
                        userId,
                        transactionId,
                        accountId,
                        SOURCE,
                        externalIdEnc,
                        externalHash,
                        now));
    history.touch(now);
    identityRepository.save(history);
  }

  private UUID resolveCategory(
      UUID userId,
      FinancialAccount account,
      TransactionType type,
      BigDecimal amount,
      String providerCategory,
      Transaction existing) {
    if (existing != null
        && existing.getCategoryId() != null
        && (providerCategory == null || providerCategory.isBlank())) {
      return existing.getCategoryId();
    }
    if (providerCategory == null || providerCategory.isBlank()) {
      return null;
    }
    String normalized = normalize(providerCategory);
    byte[] hash = hmacService.hmac("category", normalized);
    var found = categoryRepository.findByUserIdAndNormalizedNameHash(userId, hash);
    if (found.isPresent()) {
      return found.get().getId();
    }
    UUID categoryId = UUID.randomUUID();
    CategoryKind kind =
        type == TransactionType.CREDIT || type == TransactionType.REFUND
            ? CategoryKind.INCOME
            : CategoryKind.EXPENSE;
    Category category =
        new Category(
            categoryId,
            userId,
            cryptoService.encrypt(
                providerCategory, new CryptoContext(userId, "category", "name", categoryId)),
            hash,
            kind,
            null);
    return categoryRepository.save(category).getId();
  }

  private byte[] encrypt(UUID userId, String column, UUID recordId, BigDecimal amount) {
    return cryptoService.encrypt(
        amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
        new CryptoContext(userId, TABLE, column, recordId == null ? UUID.randomUUID() : recordId));
  }

  private byte[] encrypt(UUID userId, String column, UUID recordId, String value) {
    return cryptoService.encrypt(
        value,
        new CryptoContext(userId, TABLE, column, recordId == null ? UUID.randomUUID() : recordId));
  }

  private String decryptAccountId(FinancialAccount account) {
    return cryptoService.decryptString(
        account.getExternalAccountIdEnc(),
        new CryptoContext(
            account.getUserId(), "financial_account", "external_account_id", account.getId()));
  }

  private static TransactionStatus mapStatus(String status) {
    return status != null && "PENDING".equalsIgnoreCase(status)
        ? TransactionStatus.PENDING
        : TransactionStatus.POSTED;
  }

  private static TransactionType mapType(
      FinancialAccount account, BigDecimal amount, String description) {
    if (amount.signum() > 0) {
      String normalized = normalize(description);
      if (normalized.contains("chargeback")) {
        return TransactionType.CHARGEBACK;
      }
      if (normalized.contains("estorno")
          || normalized.contains("reembolso")
          || normalized.contains("refund")) {
        return TransactionType.REFUND;
      }
      // Entrada em cartão é crédito/estorno que reduz a despesa, nunca receita.
      return account.getType() == AccountType.CREDIT
          ? TransactionType.REFUND
          : TransactionType.CREDIT;
    }
    return TransactionType.DEBIT;
  }

  private static Integer installmentValue(JsonNode metadata, String field) {
    if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
      return null;
    }
    JsonNode value = metadata.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    int parsed = value.asInt(0);
    return parsed > 0 ? parsed : null;
  }

  private static String installmentMetadata(JsonNode metadata) {
    if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
      return "";
    }
    return metadata.path("totalInstallments").asString("")
        + "/"
        + metadata.path("installmentNumber").asString("");
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .strip()
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }
}
