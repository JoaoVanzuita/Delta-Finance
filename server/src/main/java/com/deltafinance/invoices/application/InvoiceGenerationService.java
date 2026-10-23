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
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final TransactionRepository transactionRepository;
  private final CardCycleService cardCycleService;
  private final InvoiceBalanceService balanceService;
  private final CryptoService cryptoService;

  public InvoiceGenerationService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      TransactionRepository transactionRepository,
      CardCycleService cardCycleService,
      InvoiceBalanceService balanceService,
      CryptoService cryptoService) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.transactionRepository = transactionRepository;
    this.cardCycleService = cardCycleService;
    this.balanceService = balanceService;
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
      if (transaction.getStatus() != TransactionStatus.POSTED
          && transaction.getStatus() != TransactionStatus.RECONCILED) {
        continue;
      }
      if (transaction.getType() != TransactionType.DEBIT
          && transaction.getType() != TransactionType.REFUND
          && transaction.getType() != TransactionType.CHARGEBACK
          && transaction.getType() != TransactionType.CREDIT) {
        continue;
      }
      if (itemRepository
          .findByUserIdAndTransactionIdAndRemovedAtIsNull(userId, transaction.getId())
          .isPresent()) {
        continue;
      }

      CardCycleService.CardCycle cycle =
          cardCycleService.cycleFor(profile, transaction.getPostedOn());
      Invoice invoice = findOrCreateInvoice(userId, cardAccountId, cycle);
      if (invoice.getStatus() != InvoiceStatus.OPEN) {
        CardCycleService.CardCycle next = cardCycleService.nextCycle(profile, cycle);
        invoice = findOrCreateInvoice(userId, cardAccountId, next);
      }
      createMirrorItem(userId, invoice, transaction);
      transaction.setInvoiceId(invoice.getId());
      transactionRepository.save(transaction);
      created++;
    }
    balanceService.recalculateCard(userId, cardAccountId);
    return created;
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

  private void createMirrorItem(UUID userId, Invoice invoice, Transaction transaction) {
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
    InvoiceItemKind kind = debit ? InvoiceItemKind.PURCHASE : InvoiceItemKind.REFUND;
    ReportingEffect effect = debit ? ReportingEffect.NONE : ReportingEffect.EXPENSE_DECREASE;
    InvoiceItemSource source =
        transaction.getSource() == TransactionSource.MANUAL
            ? InvoiceItemSource.MANUAL
            : InvoiceItemSource.IMPORTED;
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
            null,
            null,
            null);
    itemRepository.save(item);
  }

  private byte[] encryptInvoice(UUID userId, UUID invoiceId, String column, String value) {
    return cryptoService.encrypt(
        value, new CryptoContext(userId, INVOICE_TABLE, column, invoiceId));
  }

  private byte[] encryptItem(UUID userId, UUID itemId, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, ITEM_TABLE, column, itemId));
  }
}
