package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemKind;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceItemSource;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceSource;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.invoices.domain.ReportingEffect;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração/associação de faturas por ciclo local.
 *
 * <p>Somente transações efetivadas geram itens. Cada transação origina no máximo um item ativo.
 * Transações que chegam depois do fechamento de uma fatura já existente são alocadas ao ciclo
 * seguinte, sem alterar faturas pagas. Sem ciclo configurado, nada é gerado.
 */
@Service
public class InvoiceGenerationService {

  private static final String INVOICE_TABLE = "invoice";
  private static final String ITEM_TABLE = "invoice_item";
  private static final int MAX_CYCLE_ADVANCE = 240;

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final TransactionRepository transactionRepository;
  private final CardCycleService cardCycleService;
  private final InvoiceBalanceService balanceService;
  private final InstallmentService installmentService;
  private final CryptoService cryptoService;

  public InvoiceGenerationService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      TransactionRepository transactionRepository,
      CardCycleService cardCycleService,
      InvoiceBalanceService balanceService,
      InstallmentService installmentService,
      CryptoService cryptoService) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.transactionRepository = transactionRepository;
    this.cardCycleService = cardCycleService;
    this.balanceService = balanceService;
    this.installmentService = installmentService;
    this.cryptoService = cryptoService;
  }

  @Transactional
  public int ensureInvoices(UUID userId, UUID cardAccountId) {
    cardCycleService.requireCard(userId, cardAccountId);
    CardProfile profile = cardCycleService.profileFor(userId, cardAccountId).orElse(null);
    if (profile == null) {
      return 0;
    }

    List<Transaction> transactions =
        transactionRepository.findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(
            userId, cardAccountId);
    int created = 0;
    for (Transaction transaction : transactions) {
      if (!isEffective(transaction) || !mirrorsInvoice(transaction.getType())) {
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

      Invoice invoice = invoiceForDate(userId, cardAccountId, transaction.getPostedOn());
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
    if (!itemRepository
        .findByUserIdAndTransactionIdAndRemovedAtIsNull(userId, transaction.getId())
        .isEmpty()) {
      return null;
    }
    if (hasSuspendedMirror(userId, transaction.getId())) {
      return null;
    }
    CardProfile profile =
        cardCycleService.profileFor(userId, transaction.getAccountId()).orElse(null);
    if (profile == null) {
      return null;
    }
    Invoice invoice = invoiceForDate(userId, transaction.getAccountId(), transaction.getPostedOn());
    if (invoice == null) {
      return null;
    }
    InvoiceItem item = createMirrorItem(userId, invoice, transaction);
    transaction.setInvoiceId(invoice.getId());
    transactionRepository.save(transaction);
    balanceService.recalculateCard(userId, transaction.getAccountId());
    return item;
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
