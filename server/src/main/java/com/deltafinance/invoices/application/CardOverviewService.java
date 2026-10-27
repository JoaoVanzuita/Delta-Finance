package com.deltafinance.invoices.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionStatus;
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

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Money.SCALE);
  private static final List<TransactionStatus> EFFECTIVE_STATUSES =
      List.of(TransactionStatus.POSTED, TransactionStatus.RECONCILED);

  private final AccountQueryService accountQueryService;
  private final InvoiceQueryService invoiceQueryService;
  private final InvoiceRepository invoiceRepository;
  private final TransactionRepository transactionRepository;

  public CardOverviewService(
      AccountQueryService accountQueryService,
      InvoiceQueryService invoiceQueryService,
      InvoiceRepository invoiceRepository,
      TransactionRepository transactionRepository) {
    this.accountQueryService = accountQueryService;
    this.invoiceQueryService = invoiceQueryService;
    this.invoiceRepository = invoiceRepository;
    this.transactionRepository = transactionRepository;
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
        utilization(used, limit),
        nextDueDate,
        currentInvoice,
        nextDueInvoice,
        unallocatedCount(userId, account.id()));
  }

  private List<InvoiceView> invoicesFor(UUID userId, AccountView account) {
    if (account.cardProfile() == null) {
      return List.of();
    }
    try {
      invoiceQueryService.ensureInvoices(userId, account.id());
    } catch (ValidationException | NotFoundException exception) {
      return List.of();
    }
    return invoiceQueryService.toViews(
        userId,
        account.id(),
        invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(userId, account.id()));
  }

  private int unallocatedCount(UUID userId, UUID accountId) {
    long count =
        transactionRepository.countByUserIdAndAccountIdAndInvoiceIdIsNullAndStatusIn(
            userId, accountId, EFFECTIVE_STATUSES);
    return (int) Math.min(Integer.MAX_VALUE, count);
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
      return new BigDecimal(value).setScale(Money.SCALE, RoundingMode.HALF_EVEN);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private static String format(BigDecimal value) {
    return value.setScale(Money.SCALE, RoundingMode.HALF_EVEN).toPlainString();
  }
}
