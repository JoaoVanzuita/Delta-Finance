package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemKind;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoicePayment;
import com.deltafinance.invoices.domain.InvoicePaymentRepository;
import com.deltafinance.invoices.domain.InvoicePaymentStatus;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.invoices.domain.ReportingEffect;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.TransactionDeletedEvent;
import com.deltafinance.transactions.application.TransactionFinancialChangedEvent;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém o item espelho de fatura sincronizado com a transação de origem.
 *
 * <p>Transação não efetiva ({@code PENDING} ou remoção na origem ainda não confirmada) tem o
 * espelho suspenso e sai dos totais. Quando volta a ser efetiva, o item é reativado na mesma fatura
 * ou recriado na primeira fatura aberta. Faturas pagas não são reescritas. Correções de valor,
 * descrição e data chegam a todas as parcelas do plano; apenas a âncora muda de ciclo.
 */
@Component
public class InvoiceMirrorListener {

  private static final String TRANSACTION_TABLE = "transaction";
  private static final String ITEM_TABLE = "invoice_item";

  private final InvoiceItemRepository itemRepository;
  private final InvoicePaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final TransactionRepository transactionRepository;
  private final InvoiceGenerationService generationService;
  private final InvoiceBalanceService balanceService;
  private final CryptoService cryptoService;
  private final Clock clock;

  public InvoiceMirrorListener(
      InvoiceItemRepository itemRepository,
      InvoicePaymentRepository paymentRepository,
      InvoiceRepository invoiceRepository,
      TransactionRepository transactionRepository,
      InvoiceGenerationService generationService,
      InvoiceBalanceService balanceService,
      CryptoService cryptoService,
      Clock clock) {
    this.itemRepository = itemRepository;
    this.paymentRepository = paymentRepository;
    this.invoiceRepository = invoiceRepository;
    this.transactionRepository = transactionRepository;
    this.generationService = generationService;
    this.balanceService = balanceService;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  @EventListener
  @Transactional
  public void onChanged(TransactionFinancialChangedEvent event) {
    Transaction transaction =
        transactionRepository.findByIdAndUserId(event.transactionId(), event.userId()).orElse(null);
    if (transaction == null) {
      return;
    }
    InvoicePayment payment =
        paymentRepository
            .findByUserIdAndSourceTransactionId(event.userId(), transaction.getId())
            .orElse(null);
    if (payment != null) {
      syncPaymentStatus(event.userId(), transaction, payment);
      return;
    }
    if (generationService.isCardPaymentTransaction(event.userId(), transaction)) {
      if (isEffective(transaction)) {
        generationService.ensureInvoiceForTransaction(event.userId(), transaction);
      }
      return;
    }
    List<InvoiceItem> related = relatedItems(event.userId(), event.transactionId());
    List<InvoiceItem> active = related.stream().filter(InvoiceItem::isActive).toList();

    if (!isEffective(transaction)) {
      suspendMirrors(event.userId(), active);
      return;
    }
    if (active.isEmpty()) {
      if (!restoreSuspended(event.userId(), related)) {
        generationService.ensureInvoiceForTransaction(event.userId(), transaction);
        return;
      }
      active = related.stream().filter(InvoiceItem::isActive).toList();
      if (active.isEmpty()) {
        return;
      }
    }
    syncItems(event.userId(), active, transaction);
  }

  private void syncPaymentStatus(UUID userId, Transaction transaction, InvoicePayment payment) {
    if (isEffective(transaction)) {
      // Uma duplicata PENDING/POSTED pode ter revertido o pagamento antes; ao voltar a valer,
      // confirma novamente e mantém o vínculo canônico.
      boolean statusChanged = payment.getStatus() != InvoicePaymentStatus.CONFIRMED;
      if (statusChanged) {
        payment.confirm();
        paymentRepository.save(payment);
      }
      // Correções de valor/data/chave também chegam pelo evento da transação.
      boolean dataChanged = generationService.synchronizePaymentData(userId, transaction, payment);
      if (!dataChanged && statusChanged) {
        recalculatePaymentCard(userId, payment);
      }
      return;
    }
    if (generationService.reassignPaymentSource(userId, transaction, payment)) {
      // Outra transação equivalente continua efetiva e assume o pagamento.
      return;
    }
    if (payment.getStatus() != InvoicePaymentStatus.REVERSED) {
      payment.reverse();
      paymentRepository.save(payment);
      recalculatePaymentCard(userId, payment);
    }
  }

  private void recalculatePaymentCard(UUID userId, InvoicePayment payment) {
    invoiceRepository
        .findByIdAndUserId(payment.getInvoiceId(), userId)
        .ifPresent(invoice -> balanceService.recalculateCard(userId, invoice.getCardAccountId()));
  }

  private void syncItems(UUID userId, List<InvoiceItem> active, Transaction transaction) {
    boolean plan =
        active.stream().anyMatch(item -> item.getInstallmentPlanKey() != null)
            || active.stream().anyMatch(item -> item.getTotalInstallments() != null);
    if (plan) {
      syncPlan(userId, active, transaction);
    } else if (active.size() == 1) {
      sync(userId, active.getFirst(), transaction, true);
    }
  }

  @EventListener
  @Transactional
  public void onDeleted(TransactionDeletedEvent event) {
    InvoicePayment payment =
        paymentRepository
            .findByUserIdAndSourceTransactionId(event.userId(), event.transactionId())
            .orElse(null);
    if (payment != null) {
      // A transação ainda existe neste ponto; se houver duplicata efetiva, ela assume o vínculo
      // antes da exclusão e o pagamento não é perdido.
      Transaction transaction =
          transactionRepository
              .findByIdAndUserId(event.transactionId(), event.userId())
              .orElse(null);
      if (transaction == null
          || !generationService.reassignPaymentSource(event.userId(), transaction, payment)) {
        if (payment.getStatus() != InvoicePaymentStatus.REVERSED) {
          payment.reverse();
          paymentRepository.save(payment);
          recalculatePaymentCard(event.userId(), payment);
        }
      }
      return;
    }
    List<InvoiceItem> active =
        relatedItems(event.userId(), event.transactionId()).stream()
            .filter(InvoiceItem::isActive)
            .toList();
    if (active.isEmpty()) {
      return;
    }
    for (InvoiceItem item : active) {
      Invoice invoice =
          invoiceRepository.findByIdAndUserId(item.getInvoiceId(), event.userId()).orElse(null);
      if (invoice != null && invoice.getStatus() == InvoiceStatus.PAID) {
        throw new ConflictException(
            "TRANSACTION_PAID_INVOICE",
            "A compra está em uma fatura paga; registre um ajuste explícito antes de excluir.");
      }
    }
    Set<UUID> cards = new LinkedHashSet<>();
    for (InvoiceItem item : active) {
      Invoice invoice =
          invoiceRepository.findByIdAndUserId(item.getInvoiceId(), event.userId()).orElse(null);
      if (invoice == null) {
        continue;
      }
      // O vínculo com a transação precisa sair antes da exclusão da linha de origem.
      item.attachTransaction(null);
      item.remove(clock.now());
      itemRepository.save(item);
      cards.add(invoice.getCardAccountId());
      markAdjustmentIfNeeded(invoice);
    }
    // Garante que a FK seja liberada antes da exclusão da transação de origem.
    itemRepository.flush();
    for (UUID cardAccountId : cards) {
      balanceService.recalculateCard(event.userId(), cardAccountId);
    }
  }

  private List<InvoiceItem> relatedItems(UUID userId, UUID transactionId) {
    List<InvoiceItem> related =
        new ArrayList<>(itemRepository.findByUserIdAndTransactionId(userId, transactionId));
    for (InvoiceItem item : related) {
      byte[] planKey = item.getInstallmentPlanKey();
      if (planKey != null) {
        for (InvoiceItem planItem :
            itemRepository.findByUserIdAndInstallmentPlanKey(userId, planKey)) {
          if (related.stream().noneMatch(existing -> existing.getId().equals(planItem.getId()))) {
            related.add(planItem);
          }
        }
        break;
      }
    }
    return related;
  }

  private void suspendMirrors(UUID userId, List<InvoiceItem> active) {
    Set<UUID> cards = new LinkedHashSet<>();
    for (InvoiceItem item : active) {
      Invoice invoice =
          invoiceRepository.findByIdAndUserId(item.getInvoiceId(), userId).orElse(null);
      if (invoice == null || invoice.getStatus() == InvoiceStatus.PAID) {
        // Fatura paga permanece histórica; não reescreva o total.
        continue;
      }
      item.suspendByMirror(clock.now());
      itemRepository.save(item);
      cards.add(invoice.getCardAccountId());
      markAdjustmentIfNeeded(invoice);
    }
    for (UUID cardAccountId : cards) {
      balanceService.recalculateCard(userId, cardAccountId);
    }
  }

  /** Reativa itens suspensos pelo espelho; devolve {@code true} quando reativou algo. */
  private boolean restoreSuspended(UUID userId, List<InvoiceItem> related) {
    List<InvoiceItem> suspended = related.stream().filter(InvoiceItem::isMirrorSuspended).toList();
    if (suspended.isEmpty()) {
      return false;
    }
    Set<UUID> cards = new LinkedHashSet<>();
    for (InvoiceItem item : suspended) {
      Invoice invoice =
          invoiceRepository.findByIdAndUserId(item.getInvoiceId(), userId).orElse(null);
      if (invoice == null || invoice.getStatus() == InvoiceStatus.PAID) {
        continue;
      }
      item.restoreFromMirrorSuspension();
      itemRepository.save(item);
      cards.add(invoice.getCardAccountId());
      markAdjustmentIfNeeded(invoice);
    }
    for (UUID cardAccountId : cards) {
      balanceService.recalculateCard(userId, cardAccountId);
    }
    return true;
  }

  /**
   * Propaga valor, descrição e data para todas as parcelas ativas.
   *
   * <p>A âncora (menor número de parcela) acompanha o ciclo da data efetiva; as demais mantêm a
   * data do ciclo a que pertencem.
   */
  private void syncPlan(UUID userId, List<InvoiceItem> active, Transaction transaction) {
    InvoiceItem anchor =
        active.stream()
            .min(
                Comparator.comparing(
                    item -> item.getInstallmentNumber() == null ? 0 : item.getInstallmentNumber()))
            .orElse(null);
    for (InvoiceItem item : active) {
      sync(userId, item, transaction, item == anchor);
    }
  }

  private void sync(UUID userId, InvoiceItem item, Transaction transaction, boolean anchor) {
    Invoice current = invoiceRepository.findByIdAndUserId(item.getInvoiceId(), userId).orElse(null);
    if (current == null || current.getStatus() == InvoiceStatus.PAID) {
      return;
    }
    BigDecimal amount =
        new BigDecimal(
                cryptoService.decryptString(
                    transaction.getAmountEffectiveEnc(),
                    new CryptoContext(
                        userId, TRANSACTION_TABLE, "amount_effective", transaction.getId())))
            .abs()
            .setScale(2, RoundingMode.HALF_EVEN);
    String description =
        cryptoService.decryptString(
            transaction.getDescriptionEffectiveEnc(),
            new CryptoContext(
                userId, TRANSACTION_TABLE, "description_effective", transaction.getId()));
    if (anchor) {
      boolean sameCycle =
          !transaction.getPostedOn().isBefore(current.getCycleStart())
              && !transaction.getPostedOn().isAfter(current.getCycleEnd());
      if (!sameCycle) {
        Invoice target =
            generationService.invoiceForDate(
                userId, current.getCardAccountId(), transaction.getPostedOn());
        if (target != null && !target.getId().equals(item.getInvoiceId())) {
          markAdjustmentIfNeeded(current);
          item.moveTo(target.getId());
          transaction.setInvoiceId(target.getId());
          transactionRepository.save(transaction);
          current = target;
        }
      }
    }
    boolean debit = transaction.getType() == TransactionType.DEBIT;
    boolean installment = item.getTotalInstallments() != null && item.getTotalInstallments() > 1;
    InvoiceItemKind kind =
        debit
            ? (installment ? InvoiceItemKind.INSTALLMENT : InvoiceItemKind.PURCHASE)
            : InvoiceItemKind.REFUND;
    item.applyMirror(
        kind,
        ReportingEffect.NONE,
        cryptoService.encrypt(
            description, new CryptoContext(userId, ITEM_TABLE, "description", item.getId())),
        cryptoService.encrypt(
            amount.toPlainString(), new CryptoContext(userId, ITEM_TABLE, "amount", item.getId())),
        anchor ? transaction.getPostedOn() : item.getOccurredOn());
    itemRepository.save(item);
    markAdjustmentIfNeeded(current);
    balanceService.recalculateCard(userId, current.getCardAccountId());
  }

  private void markAdjustmentIfNeeded(Invoice invoice) {
    if (invoice.getStatus() != InvoiceStatus.OPEN) {
      invoice.markAdjustment();
      invoiceRepository.save(invoice);
    }
  }

  private static boolean isEffective(Transaction transaction) {
    return (transaction.getStatus() == TransactionStatus.POSTED
            || transaction.getStatus() == TransactionStatus.RECONCILED)
        && transaction.getReconciliationState() != ReconciliationState.SOURCE_REMOVED;
  }
}
