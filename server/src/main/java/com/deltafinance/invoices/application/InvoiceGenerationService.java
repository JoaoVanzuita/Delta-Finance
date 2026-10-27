package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.categories.domain.Category;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemKind;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceItemSource;
import com.deltafinance.invoices.domain.InvoicePayment;
import com.deltafinance.invoices.domain.InvoicePaymentRepository;
import com.deltafinance.invoices.domain.InvoicePaymentSource;
import com.deltafinance.invoices.domain.InvoicePaymentStatus;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceSource;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.invoices.domain.ReportingEffect;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração/associação de faturas por ciclo local.
 *
 * <p>Somente transações efetivadas geram itens. Cada transação origina no máximo um item ativo. O
 * {@code billForecastDate} do provedor define a fatura de compras e pagamentos; sem ele, transações
 * que chegam depois do fechamento são alocadas ao ciclo seguinte. Faturas pagas não são reescritas.
 * Sem ciclo configurado, nada é gerado.
 */
@Service
public class InvoiceGenerationService {

  private static final String INVOICE_TABLE = "invoice";
  private static final String ITEM_TABLE = "invoice_item";
  private static final int MAX_CYCLE_ADVANCE = 240;

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final InvoicePaymentRepository paymentRepository;
  private final TransactionRepository transactionRepository;
  private final CardCycleService cardCycleService;
  private final InvoiceBalanceService balanceService;
  private final InstallmentService installmentService;
  private final CategoryRepository categoryRepository;
  private final CryptoService cryptoService;
  private final IndexHmacService hmacService;
  private final Clock clock;

  public InvoiceGenerationService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      InvoicePaymentRepository paymentRepository,
      TransactionRepository transactionRepository,
      CardCycleService cardCycleService,
      InvoiceBalanceService balanceService,
      InstallmentService installmentService,
      CategoryRepository categoryRepository,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      Clock clock) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.paymentRepository = paymentRepository;
    this.transactionRepository = transactionRepository;
    this.cardCycleService = cardCycleService;
    this.balanceService = balanceService;
    this.installmentService = installmentService;
    this.categoryRepository = categoryRepository;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.clock = clock;
  }

  @Transactional
  public int ensureInvoices(UUID userId, UUID cardAccountId) {
    cardCycleService.requireCard(userId, cardAccountId);
    CardProfile profile = cardCycleService.profileFor(userId, cardAccountId).orElse(null);
    if (profile == null) {
      return 0;
    }

    backfillPaymentDedupKeys(userId, cardAccountId);
    List<Transaction> transactions =
        transactionRepository.findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(
            userId, cardAccountId);
    Set<UUID> transferCategoryIds = transferCategoryIds(userId);
    int created = 0;
    for (Transaction transaction : transactions) {
      if (!isEffective(transaction) || !mirrorsInvoice(transaction.getType())) {
        continue;
      }
      if (isCardPayment(transaction, transferCategoryIds)) {
        if (ensureCardPayment(userId, cardAccountId, transaction, profile, transferCategoryIds)) {
          created++;
        }
        continue;
      }
      if (!itemRepository
          .findByUserIdAndTransactionIdAndRemovedAtIsNull(userId, transaction.getId())
          .isEmpty()) {
        continue;
      }
      if (hasSuspendedMirror(userId, transaction.getId())) {
        // A reativação pertence ao fluxo de eventos; não replique o plano suspenso.
        continue;
      }

      Invoice invoice =
          invoiceForTransaction(userId, cardAccountId, transaction, profile, transferCategoryIds);
      if (invoice == null) {
        continue;
      }
      createMirrorItem(userId, invoice, transaction);
      transaction.setInvoiceId(invoice.getId());
      transactionRepository.save(transaction);
      created++;
    }
    balanceService.recalculateCard(userId, cardAccountId);
    return created;
  }

  /**
   * Garante a fatura do ciclo atual quando já existe alguma transação no período.
   *
   * <p>Diferente das faturas históricas, a fatura aberta precisa existir mesmo quando as compras
   * ainda estão {@code PENDING}. Ela é criada com total zero e passa a receber os itens quando as
   * transações forem efetivadas. Sem transações no ciclo, não há fatura a acompanhar.
   */
  @Transactional
  public Invoice ensureCurrentInvoice(UUID userId, UUID cardAccountId) {
    CardProfile profile = cardCycleService.profileFor(userId, cardAccountId).orElse(null);
    if (profile == null) {
      return null;
    }
    CardCycleService.CardCycle cycle = cardCycleService.cycleFor(profile, cardCycleService.today());
    boolean hasTransactions =
        transactionRepository
            .findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(userId, cardAccountId)
            .stream()
            .anyMatch(
                transaction ->
                    transaction.getReconciliationState() != ReconciliationState.SOURCE_REMOVED
                        && !transaction.getPostedOn().isBefore(cycle.cycleStart())
                        && !transaction.getPostedOn().isAfter(cycle.cycleEnd()));
    Invoice existing =
        invoiceRepository
            .findByUserIdAndCardAccountIdAndCycleStart(userId, cardAccountId, cycle.cycleStart())
            .orElse(null);
    if (!hasTransactions) {
      return existing;
    }
    Invoice invoice = existing == null ? invoiceForCycle(userId, cardAccountId, cycle) : existing;
    balanceService.recalculateCard(userId, cardAccountId);
    return invoice;
  }

  /**
   * Garante o espelho de uma única transação efetivada, sem varrer o cartão inteiro.
   *
   * <p>Usado pelo listener de eventos para que o dashboard reflita a compra logo após a importação,
   * o lançamento manual ou a correção. Faturas pagas nunca são reescritas: a busca sempre parte do
   * primeiro ciclo aberto.
   */
  @Transactional
  public InvoiceItem ensureInvoiceForTransaction(UUID userId, Transaction transaction) {
    if (!isEffective(transaction) || !mirrorsInvoice(transaction.getType())) {
      return null;
    }
    CardProfile profile =
        cardCycleService.profileFor(userId, transaction.getAccountId()).orElse(null);
    if (profile == null) {
      return null;
    }
    Set<UUID> transferCategoryIds = transferCategoryIds(userId);
    if (isCardPayment(transaction, transferCategoryIds)) {
      ensureCardPayment(
          userId, transaction.getAccountId(), transaction, profile, transferCategoryIds);
      balanceService.recalculateCard(userId, transaction.getAccountId());
      return null;
    }
    if (!itemRepository
        .findByUserIdAndTransactionIdAndRemovedAtIsNull(userId, transaction.getId())
        .isEmpty()) {
      return null;
    }
    if (hasSuspendedMirror(userId, transaction.getId())) {
      return null;
    }
    Invoice invoice =
        invoiceForTransaction(
            userId, transaction.getAccountId(), transaction, profile, transferCategoryIds);
    if (invoice == null) {
      return null;
    }
    InvoiceItem item = createMirrorItem(userId, invoice, transaction);
    transaction.setInvoiceId(invoice.getId());
    transactionRepository.save(transaction);
    balanceService.recalculateCard(userId, transaction.getAccountId());
    return item;
  }

  /** Indica se a transação é um pagamento de fatura, não uma compra/estorno de loja. */
  public boolean isCardPaymentTransaction(UUID userId, Transaction transaction) {
    return isCardPayment(transaction, transferCategoryIds(userId));
  }

  /**
   * Converte o pagamento importado em {@link InvoicePayment}.
   *
   * <p>Itens espelho antigos de pagamento são removidos logicamente. O valor continua reduzindo o
   * saldo devedor, mas o total da fatura permanece bruto para o histórico.
   */
  private boolean ensureCardPayment(
      UUID userId,
      UUID cardAccountId,
      Transaction transaction,
      CardProfile profile,
      Set<UUID> transferCategoryIds) {
    boolean changed = false;
    for (InvoiceItem legacy :
        itemRepository.findByUserIdAndTransactionId(userId, transaction.getId())) {
      if (legacy.isActive()) {
        legacy.remove(clock.now());
        itemRepository.save(legacy);
        changed = true;
      }
    }

    byte[] dedupKey = paymentDedupKey(userId, transaction);
    InvoicePayment existing =
        paymentRepository
            .findByUserIdAndSourceTransactionId(userId, transaction.getId())
            .orElse(null);
    if (existing != null) {
      if (existing.getDedupKey() == null) {
        existing.applyDedupKey(dedupKey);
        paymentRepository.save(existing);
        changed = true;
      }
      if (!existing.getInvoiceId().equals(transaction.getInvoiceId())) {
        transaction.setInvoiceId(existing.getInvoiceId());
        transactionRepository.save(transaction);
        changed = true;
      }
      return changed;
    }

    InvoicePayment duplicate = findDuplicatePayment(userId, cardAccountId, dedupKey);
    if (duplicate != null) {
      changed |= adoptDuplicate(userId, transaction, duplicate);
      return changed;
    }

    Invoice invoice =
        invoiceForTransaction(userId, cardAccountId, transaction, profile, transferCategoryIds);
    if (invoice == null) {
      return changed;
    }
    createImportedPayment(userId, invoice, transaction, dedupKey);
    transaction.setInvoiceId(invoice.getId());
    transactionRepository.save(transaction);
    return true;
  }

  /**
   * Preenche `dedup_key` em pagamentos importados de versões anteriores.
   *
   * <p>Idempotente: pagamentos já indexados ou cuja chave colida com outro pagamento do cartão
   * permanecem como estão; a colisão só ocorre em ambientes com duplicidade histórica.
   */
  private void backfillPaymentDedupKeys(UUID userId, UUID cardAccountId) {
    Set<String> assigned = new HashSet<>();
    for (Invoice invoice :
        invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(
            userId, cardAccountId)) {
      for (InvoicePayment payment :
          paymentRepository.findByUserIdAndInvoiceIdOrderByPaidOnAscCreatedAtAsc(
              userId, invoice.getId())) {
        if (payment.getDedupKey() != null || payment.getSourceTransactionId() == null) {
          continue;
        }
        Transaction source =
            transactionRepository
                .findByIdAndUserId(payment.getSourceTransactionId(), userId)
                .orElse(null);
        if (source == null) {
          continue;
        }
        byte[] key = paymentDedupKey(userId, source);
        String hex = HexFormat.of().formatHex(key);
        if (!assigned.add(hex)
            || paymentRepository.findByUserIdAndDedupKey(userId, key).isPresent()) {
          continue;
        }
        payment.applyDedupKey(key);
        paymentRepository.save(payment);
      }
    }
  }

  /**
   * Chave HMAC de pagamento importado.
   *
   * <p>Inclui cartão, valor, data e descrição normalizada. É a mesma para a transação `PENDING` e
   * para a `POSTED` equivalente, evitando pagamento em dobro quando a origem muda de status.
   */
  public byte[] paymentDedupKey(UUID userId, Transaction transaction) {
    BigDecimal amount = decryptAmount(userId, transaction);
    String description = decryptDescription(userId, transaction);
    String normalized =
        Normalizer.normalize(description, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("\\s+", " ");
    String value =
        transaction.getAccountId()
            + "|"
            + amount.toPlainString()
            + "|"
            + transaction.getPostedOn()
            + "|"
            + normalized;
    return hmacService.externalIdHash("invoice-payment-dedup", value);
  }

  /**
   * Procura pagamento equivalente, inclusive entre registros antigos sem `dedup_key`.
   *
   * <p>O backfill é limitado aos pagamentos do cartão e ignora chaves repetidas, preservando os
   * registros existentes caso um ambiente já tenha duplicidades históricas.
   */
  private InvoicePayment findDuplicatePayment(UUID userId, UUID cardAccountId, byte[] dedupKey) {
    Optional<InvoicePayment> direct = paymentRepository.findByUserIdAndDedupKey(userId, dedupKey);
    if (direct.isPresent()) {
      return direct.get();
    }
    Set<String> assigned = new HashSet<>();
    for (Invoice invoice :
        invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(
            userId, cardAccountId)) {
      for (InvoicePayment candidate :
          paymentRepository.findByUserIdAndInvoiceIdOrderByPaidOnAscCreatedAtAsc(
              userId, invoice.getId())) {
        if (candidate.getDedupKey() != null || candidate.getSourceTransactionId() == null) {
          continue;
        }
        Transaction source =
            transactionRepository
                .findByIdAndUserId(candidate.getSourceTransactionId(), userId)
                .orElse(null);
        if (source == null) {
          continue;
        }
        byte[] key = paymentDedupKey(userId, source);
        String hex = HexFormat.of().formatHex(key);
        if (!assigned.add(hex)) {
          continue;
        }
        if (!Arrays.equals(key, dedupKey)) {
          continue;
        }
        candidate.applyDedupKey(key);
        paymentRepository.save(candidate);
        return candidate;
      }
    }
    return null;
  }

  private boolean adoptDuplicate(UUID userId, Transaction transaction, InvoicePayment duplicate) {
    boolean changed = false;
    if (duplicate.getStatus() == InvoicePaymentStatus.REVERSED) {
      duplicate.confirm();
      duplicate.repointSource(transaction.getId());
      paymentRepository.save(duplicate);
      changed = true;
    }
    if (!duplicate.getInvoiceId().equals(transaction.getInvoiceId())) {
      transaction.setInvoiceId(duplicate.getInvoiceId());
      transactionRepository.save(transaction);
      changed = true;
    }
    return changed;
  }

  /**
   * Sincroniza valor, data e chave de deduplicação de um pagamento importado após correção da
   * transação de origem.
   *
   * <p>Se a mudança de ciclo apontar para outra fatura e a fatura atual não estiver paga, o
   * pagamento acompanha a transação. Faturas pagas permanecem históricas.
   */
  @Transactional
  public boolean synchronizePaymentData(
      UUID userId, Transaction transaction, InvoicePayment payment) {
    BigDecimal amount = decryptAmount(userId, transaction);
    byte[] dedupKey = paymentDedupKey(userId, transaction);
    InvoicePayment collision =
        paymentRepository.findByUserIdAndDedupKey(userId, dedupKey).orElse(null);
    if (collision != null && !collision.getId().equals(payment.getId())) {
      // Mantém a chave atual para não violar a unicidade; o valor/data ainda são corrigidos.
      dedupKey = payment.getDedupKey();
    }
    BigDecimal currentAmount =
        new BigDecimal(
            cryptoService.decryptString(
                payment.getAmountEnc(),
                new CryptoContext(userId, "invoice_payment", "amount", payment.getId())));
    boolean changed =
        currentAmount.compareTo(amount) != 0
            || !payment.getPaidOn().equals(transaction.getPostedOn())
            || !Arrays.equals(payment.getDedupKey(), dedupKey);

    CardProfile profile =
        cardCycleService.profileFor(userId, transaction.getAccountId()).orElse(null);
    if (profile != null) {
      Invoice target =
          invoiceForTransaction(
              userId,
              transaction.getAccountId(),
              transaction,
              profile,
              transferCategoryIds(userId));
      if (target != null && !target.getId().equals(payment.getInvoiceId())) {
        Invoice current =
            invoiceRepository.findByIdAndUserId(payment.getInvoiceId(), userId).orElse(null);
        if (current != null && current.getStatus() != InvoiceStatus.PAID) {
          payment.reassignInvoice(target.getId());
          transaction.setInvoiceId(target.getId());
          transactionRepository.save(transaction);
          changed = true;
        }
      }
    }

    if (!changed) {
      return false;
    }
    payment.applySourceData(
        cryptoService.encrypt(
            amount.toPlainString(),
            new CryptoContext(userId, "invoice_payment", "amount", payment.getId())),
        transaction.getPostedOn(),
        dedupKey);
    paymentRepository.save(payment);
    balanceService.recalculateCard(userId, transaction.getAccountId());
    return true;
  }

  /**
   * Encontra uma transação efetiva equivalente para assumir o pagamento quando a origem atual deixa
   * de valer (duplicata `PENDING`/`POSTED` ou remoção de uma das vias).
   */
  public Optional<UUID> paymentSourceReplacement(UUID userId, Transaction removed) {
    byte[] key = paymentDedupKey(userId, removed);
    for (Transaction candidate :
        transactionRepository.findByUserIdAndAccountIdAndPostedOn(
            userId, removed.getAccountId(), removed.getPostedOn())) {
      if (candidate.getId().equals(removed.getId()) || !isEffective(candidate)) {
        continue;
      }
      if (Arrays.equals(paymentDedupKey(userId, candidate), key)) {
        return Optional.of(candidate.getId());
      }
    }
    return Optional.empty();
  }

  /**
   * Transfere o vínculo do pagamento para a duplicata efetiva.
   *
   * <p>Devolve {@code false} quando não há substituta, caso em que o chamador deve reverter o
   * pagamento.
   */
  public boolean reassignPaymentSource(UUID userId, Transaction removed, InvoicePayment payment) {
    if (payment.getSourceTransactionId() == null
        || !payment.getSourceTransactionId().equals(removed.getId())) {
      return false;
    }
    Optional<UUID> replacement = paymentSourceReplacement(userId, removed);
    if (replacement.isEmpty()) {
      return false;
    }
    Transaction candidate =
        transactionRepository.findByIdAndUserId(replacement.get(), userId).orElse(null);
    if (candidate == null) {
      return false;
    }
    payment.repointSource(candidate.getId());
    paymentRepository.save(payment);
    candidate.setInvoiceId(payment.getInvoiceId());
    transactionRepository.save(candidate);
    balanceService.recalculateCard(userId, removed.getAccountId());
    return true;
  }

  private void createImportedPayment(
      UUID userId, Invoice invoice, Transaction transaction, byte[] dedupKey) {
    BigDecimal amount = decryptAmount(userId, transaction);
    UUID paymentId = UUID.randomUUID();
    InvoicePayment payment =
        new InvoicePayment(
            paymentId,
            userId,
            invoice.getId(),
            transaction.getAccountId(),
            cryptoService.encrypt(
                amount.toPlainString(),
                new CryptoContext(userId, "invoice_payment", "amount", paymentId)),
            transaction.getPostedOn(),
            InvoicePaymentStatus.CONFIRMED,
            InvoicePaymentSource.IMPORTED,
            transaction.getId());
    payment.applyDedupKey(dedupKey);
    paymentRepository.save(payment);
  }

  private BigDecimal decryptAmount(UUID userId, Transaction transaction) {
    return new BigDecimal(
            cryptoService.decryptString(
                transaction.getAmountEffectiveEnc(),
                new CryptoContext(userId, "transaction", "amount_effective", transaction.getId())))
        .abs()
        .setScale(Money.SCALE, RoundingMode.HALF_EVEN);
  }

  private String decryptDescription(UUID userId, Transaction transaction) {
    try {
      return cryptoService.decryptString(
          transaction.getDescriptionEffectiveEnc(),
          new CryptoContext(userId, "transaction", "description_effective", transaction.getId()));
    } catch (RuntimeException exception) {
      // A chave ainda diferencia cartão, valor e data; descrição ilegível não deve quebrar o fluxo.
      return "";
    }
  }

  /**
   * Fatura correta para a transação.
   *
   * <p>O {@code billForecastDate} do Pluggy indica o mês da fatura das compras. Para pagamentos, o
   * provedor informa o ciclo em que o pagamento foi lançado; a fatura quitada é a do mês anterior.
   * Sem esse metadado, pagamentos usam a fatura imediatamente anterior e as demais transações usam
   * a data da transação.
   */
  private Invoice invoiceForTransaction(
      UUID userId,
      UUID cardAccountId,
      Transaction transaction,
      CardProfile profile,
      Set<UUID> transferCategoryIds) {
    boolean cardPayment = isCardPayment(transaction, transferCategoryIds);
    LocalDate billMonth = transaction.getProviderBillMonth();
    if (billMonth != null) {
      YearMonth targetMonth = YearMonth.from(billMonth);
      if (cardPayment) {
        targetMonth = targetMonth.minusMonths(1);
      }
      Invoice target =
          invoiceForCycle(
              userId, cardAccountId, cardCycleService.cycleForBillMonth(profile, targetMonth));
      // Fatura paga é histórica; uma transação atrasada não pode reescrevê-la.
      if (target.getStatus() != InvoiceStatus.PAID) {
        return target;
      }
    }
    if (cardPayment) {
      // Pagamentos sem billForecastDate (comum no lançamento canônico do Pluggy) quitam a fatura
      // imediatamente anterior ao ciclo da data do pagamento.
      CardCycleService.CardCycle nominal =
          cardCycleService.cycleFor(profile, transaction.getPostedOn());
      CardCycleService.CardCycle previous =
          cardCycleService.cycleFor(profile, nominal.cycleStart().minusDays(1));
      Invoice target = invoiceForCycle(userId, cardAccountId, previous);
      if (target.getStatus() != InvoiceStatus.PAID) {
        return target;
      }
    }
    return invoiceForDate(userId, cardAccountId, transaction.getPostedOn());
  }

  private boolean isCardPayment(Transaction transaction, Set<UUID> transferCategoryIds) {
    if (transaction.getType() != TransactionType.REFUND) {
      return false;
    }
    if (transaction.getCategoryId() != null
        && transferCategoryIds.contains(transaction.getCategoryId())) {
      return true;
    }
    return isPaymentDescription(transaction);
  }

  private boolean isPaymentDescription(Transaction transaction) {
    try {
      String description =
          cryptoService.decryptString(
              transaction.getDescriptionEffectiveEnc(),
              new CryptoContext(
                  transaction.getUserId(),
                  "transaction",
                  "description_effective",
                  transaction.getId()));
      String normalized =
          Normalizer.normalize(description, Normalizer.Form.NFD)
              .replaceAll("\\p{M}", "")
              .toLowerCase(Locale.ROOT);
      return normalized.contains("pagamento") || normalized.contains("payment");
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private Set<UUID> transferCategoryIds(UUID userId) {
    Set<UUID> ids = new HashSet<>();
    for (Category category : categoryRepository.findByUserIdOrderByNameEncAsc(userId)) {
      if (category.getKind() == CategoryKind.TRANSFER) {
        ids.add(category.getId());
      }
    }
    return ids;
  }

  /** Primeira fatura aberta correspondente à data informada. */
  @Transactional
  public Invoice invoiceForDate(UUID userId, UUID cardAccountId, LocalDate postedOn) {
    CardProfile profile = cardCycleService.profileFor(userId, cardAccountId).orElse(null);
    if (profile == null) {
      return null;
    }
    CardCycleService.CardCycle cycle = cardCycleService.cycleFor(profile, postedOn);
    Invoice invoice = findOrCreateInvoice(userId, cardAccountId, cycle);
    int guard = 0;
    while (invoice.getStatus() != InvoiceStatus.OPEN && guard++ < MAX_CYCLE_ADVANCE) {
      cycle = cardCycleService.nextCycle(profile, cycle);
      invoice = findOrCreateInvoice(userId, cardAccountId, cycle);
    }
    if (invoice.getStatus() != InvoiceStatus.OPEN) {
      throw new IllegalStateException("Não foi possível encontrar uma fatura aberta para o ciclo.");
    }
    return invoice;
  }

  private static boolean isEffective(Transaction transaction) {
    return (transaction.getStatus() == TransactionStatus.POSTED
            || transaction.getStatus() == TransactionStatus.RECONCILED)
        && transaction.getReconciliationState() != ReconciliationState.SOURCE_REMOVED;
  }

  private static boolean mirrorsInvoice(TransactionType type) {
    return type == TransactionType.DEBIT
        || type == TransactionType.REFUND
        || type == TransactionType.CHARGEBACK
        || type == TransactionType.CREDIT;
  }

  private boolean hasSuspendedMirror(UUID userId, UUID transactionId) {
    return itemRepository.findByUserIdAndTransactionId(userId, transactionId).stream()
        .anyMatch(InvoiceItem::isMirrorSuspended);
  }

  /** Fatura do ciclo para uso do fluxo manual de parcelamento. */
  public Invoice invoiceForCycle(
      UUID userId, UUID cardAccountId, CardCycleService.CardCycle cycle) {
    return findOrCreateInvoice(userId, cardAccountId, cycle);
  }

  private Invoice findOrCreateInvoice(
      UUID userId, UUID cardAccountId, CardCycleService.CardCycle cycle) {
    return invoiceRepository
        .findByUserIdAndCardAccountIdAndCycleStart(userId, cardAccountId, cycle.cycleStart())
        .orElseGet(
            () -> {
              UUID id = UUID.randomUUID();
              Invoice invoice =
                  new Invoice(
                      id,
                      userId,
                      cardAccountId,
                      cycle.cycleStart(),
                      cycle.cycleEnd(),
                      cycle.closingDate(),
                      cycle.dueDate(),
                      InvoiceSource.LOCAL,
                      encryptInvoice(userId, id, "total", "0.00"));
              invoice.applyTotals(
                  InvoiceStatus.OPEN,
                  encryptInvoice(userId, id, "total", "0.00"),
                  encryptInvoice(userId, id, "payments_total", "0.00"),
                  encryptInvoice(userId, id, "outstanding", "0.00"),
                  encryptInvoice(userId, id, "applied_credit", "0.00"));
              return invoiceRepository.save(invoice);
            });
  }

  private InvoiceItem createMirrorItem(UUID userId, Invoice invoice, Transaction transaction) {
    BigDecimal amount =
        new BigDecimal(
                cryptoService.decryptString(
                    transaction.getAmountEffectiveEnc(),
                    new CryptoContext(
                        userId, "transaction", "amount_effective", transaction.getId())))
            .abs()
            .setScale(2, RoundingMode.HALF_EVEN);
    String description =
        cryptoService.decryptString(
            transaction.getDescriptionEffectiveEnc(),
            new CryptoContext(userId, "transaction", "description_effective", transaction.getId()));
    boolean debit = transaction.getType() == TransactionType.DEBIT;
    boolean installment =
        transaction.getTotalInstallments() != null
            && transaction.getTotalInstallments() > 1
            && transaction.getInstallmentNumber() != null;
    InvoiceItemKind kind =
        debit
            ? (installment ? InvoiceItemKind.INSTALLMENT : InvoiceItemKind.PURCHASE)
            : InvoiceItemKind.REFUND;
    // Itens espelho nunca entram na projeção de relatórios; a direção vem do tipo e do total da
    // fatura. O efeito explícito é reservado a itens manuais.
    ReportingEffect effect = ReportingEffect.NONE;
    InvoiceItemSource source =
        transaction.getSource() == TransactionSource.MANUAL
            ? InvoiceItemSource.MANUAL
            : InvoiceItemSource.IMPORTED;
    byte[] planKey =
        installment
            ? installmentService.planKey(userId, description, transaction.getTotalInstallments())
            : null;
    UUID itemId = UUID.randomUUID();
    InvoiceItem item =
        new InvoiceItem(
            itemId,
            userId,
            invoice.getId(),
            transaction.getId(),
            kind,
            effect,
            source,
            encryptItem(userId, itemId, "description", description),
            encryptItem(userId, itemId, "amount", amount.toPlainString()),
            transaction.getPostedOn(),
            installment ? transaction.getInstallmentNumber() : null,
            installment ? transaction.getTotalInstallments() : null,
            planKey);
    itemRepository.save(item);
    return item;
  }

  private byte[] encryptInvoice(UUID userId, UUID invoiceId, String column, String value) {
    return cryptoService.encrypt(
        value, new CryptoContext(userId, INVOICE_TABLE, column, invoiceId));
  }

  private byte[] encryptItem(UUID userId, UUID itemId, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, ITEM_TABLE, column, itemId));
  }
}
