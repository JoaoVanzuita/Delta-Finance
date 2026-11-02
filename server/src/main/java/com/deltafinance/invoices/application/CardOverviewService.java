package com.deltafinance.invoices.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.shared.money.Money;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Leitura agregada dos cartões de crédito.
 *
 * <p>Reaproveita o ciclo, as faturas e os pagamentos já existentes. A visão não altera valores: se
 * o cartão ainda não tem ciclo, ele aparece como pendência de configuração e as compras permanecem
 * não alocadas. Totais consolidados consideram apenas cartões em BRL, como no dashboard.
 */
@Service
public class CardOverviewService {

  private static final BigDecimal ZERO = Money.scaled(BigDecimal.ZERO);
  private static final List<TransactionStatus> EFFECTIVE_STATUSES =
      List.of(TransactionStatus.POSTED, TransactionStatus.RECONCILED);

  private final AccountQueryService accountQueryService;
  private final InvoiceQueryService invoiceQueryService;
  private final InvoiceRepository invoiceRepository;
  private final TransactionRepository transactionRepository;
  private final CardDebtService cardDebtService;
  private final EncryptedValueReader encryptedValueReader;

  public CardOverviewService(
      AccountQueryService accountQueryService,
      InvoiceQueryService invoiceQueryService,
      InvoiceRepository invoiceRepository,
      TransactionRepository transactionRepository,
      CardDebtService cardDebtService,
      EncryptedValueReader encryptedValueReader) {
    this.accountQueryService = accountQueryService;
    this.invoiceQueryService = invoiceQueryService;
    this.invoiceRepository = invoiceRepository;
    this.transactionRepository = transactionRepository;
    this.cardDebtService = cardDebtService;
    this.encryptedValueReader = encryptedValueReader;
  }

  public CardOverviewView overview(UUID userId) {
    List<CardOverviewView.CardItem> items = new ArrayList<>();
    BigDecimal totalOutstanding = ZERO;
    BigDecimal totalLimit = ZERO;
    BigDecimal totalUsed = ZERO;
    LocalDate nextDueDate = null;

    for (AccountView account : accountQueryService.list(userId)) {
      if (!"CREDIT".equals(account.type())) {
        continue;
      }
      CardOverviewView.CardItem item = cardItem(userId, account);
      items.add(item);
      if (nextDueDate == null
          || (item.nextDueDate() != null && item.nextDueDate().isBefore(nextDueDate))) {
        nextDueDate = item.nextDueDate() == null ? nextDueDate : item.nextDueDate();
      }
      if (!"BRL".equalsIgnoreCase(account.currency())) {
        continue;
      }
      totalOutstanding = totalOutstanding.add(parse(item.outstanding(), ZERO));
      totalLimit = totalLimit.add(parse(item.creditLimit(), ZERO));
      totalUsed = totalUsed.add(parse(item.usedAmount(), ZERO));
    }

    return new CardOverviewView(
        format(totalOutstanding), format(totalLimit), format(totalUsed), nextDueDate, items);
  }

  private CardOverviewView.CardItem cardItem(UUID userId, AccountView account) {
    List<InvoiceView> invoices = invoicesFor(userId, account);
    InvoiceView currentInvoice =
        invoices.stream()
            .max(Comparator.comparing(InvoiceView::cycleStart).thenComparing(InvoiceView::id))
            .orElse(null);
    InvoiceView nextDueInvoice =
        invoices.stream()
            .filter(invoice -> !"PAID".equals(invoice.status()))
            .filter(invoice -> parse(invoice.outstanding(), ZERO).signum() > 0)
            .min(
                Comparator.comparing(InvoiceView::dueDate)
                    .thenComparing(InvoiceView::cycleStart)
                    .thenComparing(InvoiceView::id))
            .orElse(null);

    BigDecimal outstanding =
        invoices.stream()
            .filter(invoice -> !"PAID".equals(invoice.status()))
            .map(invoice -> parse(invoice.outstanding(), ZERO))
            .reduce(ZERO, BigDecimal::add)
            .max(ZERO);
    CardDebtService.CardDebt debt = cardDebtService.byAccount(account, outstanding);
    BigDecimal invoicedOutstanding = debt.invoicedOutstanding();
    BigDecimal unbilledAmount = debt.unbilledAmount();
    outstanding = debt.outstanding();
    BigDecimal used = parse(account.currentBalance(), null);
    if (used == null || used.signum() <= 0) {
      // O provedor usa valor positivo para dívida; saldo neutro/negativo sem dívida vira 0.
      used = outstanding.signum() > 0 ? outstanding : ZERO;
    }
    used = used.max(ZERO);

    BigDecimal limit =
        account.cardProfile() == null ? null : parse(account.cardProfile().creditLimit(), null);
    LocalDate nextDueDate = nextDueInvoice == null ? null : nextDueInvoice.dueDate();
    if (nextDueDate == null && currentInvoice != null && "OPEN".equals(currentInvoice.status())) {
      nextDueDate = currentInvoice.dueDate();
    }

    AttentionTotals attention = attentionTotals(userId, account.id(), currentInvoice);

    return new CardOverviewView.CardItem(
        account.id(),
        account.alias(),
        account.institutionName(),
        account.status(),
        account.currency(),
        account.cardProfile() != null,
        account.cardProfile() == null ? null : account.cardProfile().closingDay(),
        account.cardProfile() == null ? null : account.cardProfile().dueDay(),
        account.cardProfile() == null ? null : account.cardProfile().creditLimit(),
        account.cardProfile() == null ? null : account.cardProfile().creditBalance(),
        account.cardProfile() == null ? null : account.cardProfile().availableCreditLimit(),
        account.cardProfile() == null ? null : account.cardProfile().brand(),
        account.cardProfile() == null ? null : account.cardProfile().level(),
        account.cardProfile() == null ? null : account.cardProfile().last4(),
        format(used),
        format(outstanding),
        format(invoicedOutstanding),
        format(unbilledAmount),
        utilization(used, limit),
        nextDueDate,
        currentInvoice,
        nextDueInvoice,
        attention.pendingAllCount(),
        attention.unallocatedAllCount(),
        attention.pendingPurchaseCount(),
        format(attention.pendingPurchaseTotal()),
        attention.unallocatedPurchaseCount(),
        format(attention.unallocatedPurchaseTotal()),
        attention.unallocatedCreditCount(),
        format(attention.unallocatedCreditTotal()));
  }

  private AttentionTotals attentionTotals(UUID userId, UUID accountId, InvoiceView currentInvoice) {
    int pendingAllCount = 0;
    int pendingPurchaseCount = 0;
    BigDecimal pendingPurchaseTotal = ZERO;
    int unallocatedAllCount = 0;
    int unallocatedPurchaseCount = 0;
    BigDecimal unallocatedPurchaseTotal = ZERO;
    int unallocatedCreditCount = 0;
    BigDecimal unallocatedCreditTotal = ZERO;

    for (Transaction transaction :
        transactionRepository.findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(
            userId, accountId)) {
      if (transaction.getStatus() == TransactionStatus.PENDING) {
        pendingAllCount++;
        boolean inCurrentCycle =
            currentInvoice != null
                && !transaction.getPostedOn().isBefore(currentInvoice.cycleStart())
                && !transaction.getPostedOn().isAfter(currentInvoice.cycleEnd());
        if (transaction.getType() == TransactionType.DEBIT && inCurrentCycle) {
          pendingPurchaseCount++;
          pendingPurchaseTotal = pendingPurchaseTotal.add(amount(userId, transaction));
        }
        continue;
      }
      if (transaction.getInvoiceId() != null
          || !EFFECTIVE_STATUSES.contains(transaction.getStatus())) {
        continue;
      }
      unallocatedAllCount++;
      BigDecimal amount = amount(userId, transaction);
      if (isCredit(transaction)) {
        unallocatedCreditCount++;
        unallocatedCreditTotal = unallocatedCreditTotal.add(amount);
      } else {
        unallocatedPurchaseCount++;
        unallocatedPurchaseTotal = unallocatedPurchaseTotal.add(amount);
      }
    }
    return new AttentionTotals(
        pendingAllCount,
        pendingPurchaseCount,
        pendingPurchaseTotal,
        unallocatedAllCount,
        unallocatedPurchaseCount,
        unallocatedPurchaseTotal,
        unallocatedCreditCount,
        unallocatedCreditTotal);
  }

  private BigDecimal amount(UUID userId, Transaction transaction) {
    return encryptedValueReader
        .tryReadMoney(
            userId,
            "transaction",
            "amount_effective",
            transaction.getId(),
            transaction.getAmountEffectiveEnc())
        .map(BigDecimal::abs)
        .orElse(ZERO);
  }

  private static boolean isCredit(Transaction transaction) {
    return transaction.getType() == TransactionType.CREDIT
        || transaction.getType() == TransactionType.REFUND
        || transaction.getType() == TransactionType.CHARGEBACK;
  }

  private record AttentionTotals(
      int pendingAllCount,
      int pendingPurchaseCount,
      BigDecimal pendingPurchaseTotal,
      int unallocatedAllCount,
      int unallocatedPurchaseCount,
      BigDecimal unallocatedPurchaseTotal,
      int unallocatedCreditCount,
      BigDecimal unallocatedCreditTotal) {}

  private List<InvoiceView> invoicesFor(UUID userId, AccountView account) {
    if (account.cardProfile() == null) {
      return List.of();
    }
    return invoiceQueryService.toViews(
        userId,
        account.id(),
        invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(userId, account.id()));
  }

  private static String utilization(BigDecimal used, BigDecimal limit) {
    if (limit == null || limit.signum() <= 0 || used.signum() < 0) {
      return null;
    }
    return used.multiply(new BigDecimal("100"))
        .divide(limit, 2, RoundingMode.HALF_EVEN)
        .toPlainString();
  }

  private static BigDecimal parse(String value, BigDecimal fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Money.scaled(new BigDecimal(value));
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private static String format(BigDecimal value) {
    return Money.plain(value);
  }
}
