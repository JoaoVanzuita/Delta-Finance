package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceSource;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.transactions.domain.Transaction;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolve e cria faturas por ciclo/data sem conhecer pagamentos ou espelhos. */
@Component
class InvoiceCycleLookup {

  private static final String INVOICE_TABLE = "invoice";
  private static final int MAX_CYCLE_ADVANCE = 240;

  private final InvoiceRepository invoiceRepository;
  private final CardCycleService cardCycleService;
  private final CardPaymentClassifier paymentClassifier;
  private final CryptoService cryptoService;

  InvoiceCycleLookup(
      InvoiceRepository invoiceRepository,
      CardCycleService cardCycleService,
      CardPaymentClassifier paymentClassifier,
      CryptoService cryptoService) {
    this.invoiceRepository = invoiceRepository;
    this.cardCycleService = cardCycleService;
    this.paymentClassifier = paymentClassifier;
    this.cryptoService = cryptoService;
  }

  Invoice invoiceForTransaction(
      UUID userId,
      UUID cardAccountId,
      Transaction transaction,
      CardProfile profile,
      Set<UUID> transferCategoryIds) {
    boolean cardPayment = paymentClassifier.isCardPayment(transaction, transferCategoryIds);
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

  private byte[] encryptInvoice(UUID userId, UUID invoiceId, String column, String value) {
    return cryptoService.encrypt(
        value, new CryptoContext(userId, INVOICE_TABLE, column, invoiceId));
  }
}
