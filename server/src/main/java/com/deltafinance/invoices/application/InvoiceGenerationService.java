package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemKind;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceItemSource;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.ReportingEffect;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração/associação de faturas por ciclo local.
 *
 * <p>Somente transações efetivadas geram itens. Cada transação origina um espelho; compras
 * parceladas geram um item por parcela. O {@code billForecastDate} do provedor define a fatura de
 * compras e pagamentos; sem ele, transações que chegam depois do fechamento são alocadas ao ciclo
 * seguinte. Faturas pagas não são reescritas. Sem ciclo configurado, nada é gerado.
 */
@Service
public class InvoiceGenerationService {

  private static final String ITEM_TABLE = "invoice_item";

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final TransactionRepository transactionRepository;
  private final CardCycleService cardCycleService;
  private final InvoiceBalanceService balanceService;
  private final InstallmentService installmentService;
  private final CryptoService cryptoService;
  private final EncryptedValueReader encryptedValueReader;
  private final CardPaymentClassifier paymentClassifier;
  private final InvoiceCycleLookup invoiceLookup;
  private final CardPaymentReconciler paymentReconciler;

  public InvoiceGenerationService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      TransactionRepository transactionRepository,
      CardCycleService cardCycleService,
      InvoiceBalanceService balanceService,
      InstallmentService installmentService,
      CryptoService cryptoService,
      EncryptedValueReader encryptedValueReader,
      CardPaymentClassifier paymentClassifier,
      InvoiceCycleLookup invoiceLookup,
      CardPaymentReconciler paymentReconciler) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.transactionRepository = transactionRepository;
    this.cardCycleService = cardCycleService;
    this.balanceService = balanceService;
    this.installmentService = installmentService;
    this.cryptoService = cryptoService;
    this.encryptedValueReader = encryptedValueReader;
    this.paymentClassifier = paymentClassifier;
    this.invoiceLookup = invoiceLookup;
    this.paymentReconciler = paymentReconciler;
  }

  public int ensureInvoices(UUID userId, UUID cardAccountId) {
    cardCycleService.requireCard(userId, cardAccountId);
    CardProfile profile = cardCycleService.profileFor(userId, cardAccountId).orElse(null);
    if (profile == null) {
      return 0;
    }

    paymentReconciler.backfillPaymentDedupKeys(userId, cardAccountId);
    List<Transaction> transactions =
        transactionRepository.findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(
            userId, cardAccountId);
    Set<UUID> transferCategoryIds = paymentClassifier.transferCategoryIds(userId);
    int created = 0;
    for (Transaction transaction : transactions) {
      if (!isEffective(transaction) || !mirrorsInvoice(transaction.getType())) {
        continue;
      }
      if (paymentClassifier.isCardPayment(transaction, transferCategoryIds)) {
        if (paymentReconciler.ensureCardPayment(
            userId, cardAccountId, transaction, profile, transferCategoryIds)) {
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
          invoiceLookup.invoiceForTransaction(
              userId, cardAccountId, transaction, profile, transferCategoryIds);
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

  public void ensureCurrentInvoiceForCard(UUID userId, UUID accountId) {
    if (cardCycleService.profileFor(userId, accountId).isEmpty()) {
      return;
    }
    ensureCurrentInvoice(userId, accountId);
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

  public InvoiceItem ensureInvoiceForTransaction(UUID userId, Transaction transaction) {
    if (!isEffective(transaction) || !mirrorsInvoice(transaction.getType())) {
      return null;
    }
    CardProfile profile =
        cardCycleService.profileFor(userId, transaction.getAccountId()).orElse(null);
    if (profile == null) {
      return null;
    }
    Set<UUID> transferCategoryIds = paymentClassifier.transferCategoryIds(userId);
    if (paymentClassifier.isCardPayment(transaction, transferCategoryIds)) {
      paymentReconciler.ensureCardPayment(
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
        invoiceLookup.invoiceForTransaction(
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
    return paymentClassifier.isCardPayment(
        transaction, paymentClassifier.transferCategoryIds(userId));
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

  /** Primeira fatura aberta correspondente à data informada. */
  @Transactional
  public Invoice invoiceForDate(UUID userId, UUID cardAccountId, LocalDate postedOn) {
    return invoiceLookup.invoiceForDate(userId, cardAccountId, postedOn);
  }

  /** Fatura do ciclo para uso do fluxo manual de parcelamento. */
  public Invoice invoiceForCycle(
      UUID userId, UUID cardAccountId, CardCycleService.CardCycle cycle) {
    return invoiceLookup.invoiceForCycle(userId, cardAccountId, cycle);
  }

  private InvoiceItem createMirrorItem(UUID userId, Invoice invoice, Transaction transaction) {
    BigDecimal amount =
        encryptedValueReader
            .readMoney(
                userId,
                "transaction",
                "amount_effective",
                transaction.getId(),
                transaction.getAmountEffectiveEnc())
            .abs();
    String description =
        encryptedValueReader.read(
            userId,
            "transaction",
            "description_effective",
            transaction.getId(),
            transaction.getDescriptionEffectiveEnc());
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
            ? installmentService.planKey(
                userId, transaction.getId(), description, transaction.getTotalInstallments())
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

  private byte[] encryptItem(UUID userId, UUID itemId, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, ITEM_TABLE, column, itemId));
  }
}
