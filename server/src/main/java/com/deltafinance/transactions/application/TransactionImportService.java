package com.deltafinance.transactions.application;

import com.deltafinance.accounts.domain.AccountStatus;
import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.categories.application.CategoryTaxonomy;
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
import java.time.YearMonth;
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
 *
 * <p>O sinal das transações de cartão de crédito é normalizado na importação: o Pluggy usa valores
 * positivos para compras (aumentam a dívida) e negativos para pagamentos/créditos. Internamente a
 * convenção é sempre negativa para saída e positiva para entrada, como nos lançamentos manuais. O
 * valor bruto da origem permanece em {@code amount_source_enc} para auditoria.
 */
@Service
public class TransactionImportService {

  private static final String TABLE = "transaction";
  private static final String SOURCE = "PLUGGY";
  private static final int MONTHS_INITIAL = 12;

  /**
   * Categorias do provedor que representam movimentação entre contas do próprio titular.
   *
   * <p>Elas são registradas como {@link CategoryKind#TRANSFER} para não compor receitas nem
   * despesas, mesmo quando não há uma perna correspondente importada (ex.: cofrinho ou conta de
   * outra instituição não conectada).
   */
  private final TransactionRepository transactionRepository;

  private final FinancialAccountRepository accountRepository;
  private final CategoryService categoryService;
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
      CategoryService categoryService,
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
    this.categoryService = categoryService;
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
    BigDecimal sourceAmount = imported.amount();
    // Em transações internacionais o provedor envia o valor original em `amount` e a conversão
    // para a moeda da conta em `amountInAccountCurrency`. O valor efetivo usa a conversão; o
    // valor original permanece em `amount_source` e a moeda de origem em `source_currency`.
    BigDecimal accountAmount =
        imported.amountInAccountCurrency() != null
            ? imported.amountInAccountCurrency()
            : sourceAmount;
    // Pluggy inverte o sinal em cartões: positivo é compra (dívida) e negativo é pagamento. A
    // convenção interna é única (negativo = saída), então normalizamos já na importação.
    BigDecimal amount = normalizeAmount(account, accountAmount);
    String effectiveCurrency = effectiveCurrency(account, imported);
    String description = imported.description() == null ? "" : imported.description();
    LocalDate postedOn = imported.date();
    TransactionStatus status = mapStatus(imported.status());
    TransactionType type = mapType(account, amount, description);
    byte[] dedupKey =
        hmacService.dedupKey(SOURCE, connectionId, externalAccountId, imported.externalId());
    byte[] fingerprint =
        hmacService.fingerprint(
            account.getId().toString(),
            sourceAmount.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
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
    if (transaction == null
        && status == TransactionStatus.PENDING
        && account.getType() == AccountType.CREDIT
        && type == TransactionType.REFUND
        && looksLikeCardPayment(description)
        && hasEffectivePaymentDuplicate(userId, account.getId(), postedOn, type, amount)) {
      // O provedor pode manter a via PENDING da instituição e a via POSTED sintética do mesmo
      // pagamento. A via efetiva já representa o evento; não crie uma segunda linha.
      return Outcome.SKIPPED;
    }

    UUID categoryId =
        transaction != null && transaction.hasEdited("category")
            ? transaction.getCategoryId()
            : resolveCategory(
                userId, account, type, amount, imported.category(), description, transaction);
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
        encrypt(
            userId,
            "amount_source",
            transaction == null ? null : transaction.getId(),
            sourceAmount);
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
              effectiveCurrency,
              amountSourceEnc,
              amountEffectiveEnc,
              descriptionSourceEnc,
              descriptionEffectiveEnc);
      transaction.applySourceCurrency(sourceCurrency(account, imported));
      transaction.setCategoryId(categoryId);
      if (relatedId != null) {
        transaction.linkRelatedTransaction(relatedId);
      }
      transaction.setReconciliationState(ReconciliationState.NONE);
      transaction.applyInstallments(
          installmentValue(imported.creditCardMetadata(), "installmentNumber"),
          installmentValue(imported.creditCardMetadata(), "totalInstallments"));
      transaction.applyProviderBillMonth(billForecastMonth(imported.creditCardMetadata()));
      transaction.applyProviderMetadata(
          imported.operationType(),
          imported.providerId(),
          imported.providerBillId(),
          imported.providerBillClosingDate());
      transaction = transactionRepository.save(transaction);
      // Cifragens usam o ID recém-gerado; refaz para vincular o AAD correto.
      transaction.applyImported(
          type,
          status,
          postedOn,
          postedOn,
          encrypt(userId, "amount_source", transaction.getId(), sourceAmount),
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
    boolean statusEdited =
        transaction.hasEdited("status")
            && (transaction.getStatus() == TransactionStatus.POSTED
                || transaction.getStatus() == TransactionStatus.RECONCILED);
    TransactionStatus preservedStatus = transaction.getStatus();
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
    transaction.applyAccountCurrency(effectiveCurrency);
    transaction.applySourceCurrency(sourceCurrency(account, imported));
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
    if (amountEdited || descriptionEdited || postedOnEdited || categoryEdited || statusEdited) {
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
      if (statusEdited) {
        preservedFields.add("status");
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
    if (statusEdited) {
      // Backfill documental: o provedor continua em PENDING, mas a fatura oficial já quitou a
      // compra/pagamento. A marca sobrevive aos próximos syncs.
      transaction.markStatus(preservedStatus);
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
    transaction.applyProviderBillMonth(billForecastMonth(imported.creditCardMetadata()));
    transaction.applyProviderMetadata(
        imported.operationType(),
        imported.providerId(),
        imported.providerBillId(),
        imported.providerBillClosingDate());
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
      String description,
      Transaction existing) {
    if (existing != null
        && existing.getCategoryId() != null
        && (providerCategory == null || providerCategory.isBlank())) {
      return existing.getCategoryId();
    }
    String canonicalKey =
        providerCategory == null || providerCategory.isBlank()
            ? CategoryTaxonomy.OUTROS
            : CategoryTaxonomy.canonicalKey(providerCategory);
    // Salário costuma chegar como "Same person transfer" porque a conta pagadora não está
    // conectada. A descrição explícita prevalece sobre esse rótulo genérico e garante que a
    // entrada componha receitas.
    if (type == TransactionType.CREDIT
        && account.getType() != AccountType.CREDIT
        && CategoryTaxonomy.isSalaryDescription(description)) {
      canonicalKey = CategoryTaxonomy.SALARIO;
    }
    // Débito bancário que paga a fatura do cartão é transferência, não despesa.
    if (type == TransactionType.DEBIT
        && account.getType() != AccountType.CREDIT
        && CategoryTaxonomy.isCardInvoicePaymentDescription(description)) {
      canonicalKey = CategoryTaxonomy.TRANSFERENCIAS;
    }
    // Aporte e resgate são movimentos patrimoniais, não receita/despesa. O Pluggy usa a mesma
    // família de categorias ("Investments" e filhos) para os dois sentidos; a direção separa.
    if (CategoryTaxonomy.isInvestmentKey(canonicalKey)) {
      canonicalKey =
          type == TransactionType.CREDIT ? CategoryTaxonomy.RESGATES : CategoryTaxonomy.APORTES;
    }
    // Em conta bancária, um crédito numa categoria de despesa é uma entrada atípica (reembolso,
    // devolução ou categorização incorreta do provedor). Ele não pode aparecer como “despesa” no
    // gráfico de receitas; vai para Outros e o usuário pode recategorizar depois. No cartão, o
    // crédito é estorno e deve permanecer na categoria original para reduzir a despesa correta.
    if (type == TransactionType.CREDIT
        && account.getType() != AccountType.CREDIT
        && CategoryTaxonomy.isExpenseKey(canonicalKey)) {
      canonicalKey = CategoryTaxonomy.OUTROS;
    }
    return categoryService.canonicalCategoryId(userId, canonicalKey);
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

  /**
   * Converte o valor da origem para o sinal interno.
   *
   * <p>Para contas {@code CREDIT}, o Pluggy retorna compras como positivas (aumentam a dívida) e
   * pagamentos/créditos como negativos. No restante do sistema a saída é negativa, então o valor é
   * invertido. Contas {@code BANK}, {@code CASH} e {@code WALLET} já seguem a convenção interna.
   */
  private static BigDecimal normalizeAmount(FinancialAccount account, BigDecimal amount) {
    return account.getType() == AccountType.CREDIT ? amount.negate() : amount;
  }

  /** Moeda de origem só é preservada quando difere da moeda da conta. */
  private static String sourceCurrency(
      FinancialAccount account, PluggyModels.PluggyTransaction imported) {
    String source = imported.currency();
    if (source == null || source.isBlank() || source.equalsIgnoreCase(account.getCurrency())) {
      return null;
    }
    return source;
  }

  /**
   * Moeda do valor efetivo.
   *
   * <p>Com {@code amountInAccountCurrency}, o valor já está na moeda da conta. Sem conversão
   * disponível, mantém a moeda original para que o relatório continue excluindo o lançamento em vez
   * de tratá-lo como BRL.
   */
  private static String effectiveCurrency(
      FinancialAccount account, PluggyModels.PluggyTransaction imported) {
    if (imported.amountInAccountCurrency() != null) {
      return account.getCurrency();
    }
    return imported.currency() == null || imported.currency().isBlank()
        ? account.getCurrency()
        : imported.currency();
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

  private static LocalDate billForecastMonth(JsonNode metadata) {
    if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
      return null;
    }
    String text = metadata.path("billForecastDate").asString(null);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return YearMonth.parse(text.trim()).atDay(1);
    } catch (RuntimeException exception) {
      return null;
    }
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

  private static boolean looksLikeCardPayment(String description) {
    String normalized =
        Normalizer.normalize(description == null ? "" : description, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return normalized.contains("pagamento")
        || normalized.contains("payment")
        || normalized.contains("boleto");
  }

  /**
   * Procura a via efetiva do mesmo pagamento de cartão (mesma conta, data, tipo e valor).
   *
   * <p>Restrito a descrições de pagamento para não confundir duas compras legítimas idênticas no
   * mesmo dia.
   */
  private boolean hasEffectivePaymentDuplicate(
      UUID userId, UUID accountId, LocalDate postedOn, TransactionType type, BigDecimal amount) {
    for (Transaction candidate :
        transactionRepository.findByUserIdAndAccountIdAndPostedOn(userId, accountId, postedOn)) {
      if ((candidate.getStatus() != TransactionStatus.POSTED
              && candidate.getStatus() != TransactionStatus.RECONCILED)
          || candidate.getType() != type) {
        continue;
      }
      BigDecimal candidateAmount =
          new BigDecimal(
              cryptoService.decryptString(
                  candidate.getAmountEffectiveEnc(),
                  new CryptoContext(userId, TABLE, "amount_effective", candidate.getId())));
      if (candidateAmount.compareTo(amount) != 0) {
        continue;
      }
      try {
        String candidateDescription =
            cryptoService.decryptString(
                candidate.getDescriptionEffectiveEnc(),
                new CryptoContext(userId, TABLE, "description_effective", candidate.getId()));
        if (looksLikeCardPayment(candidateDescription)) {
          return true;
        }
      } catch (RuntimeException exception) {
        // Descrição ilegível não confirma a duplicata.
      }
    }
    return false;
  }
}
