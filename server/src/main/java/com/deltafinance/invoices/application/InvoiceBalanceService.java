package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.accounts.domain.CardProfileRepository;
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
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recálculo de totais, saldo devedor, crédito acumulado e status das faturas.
 *
 * <p>Itens espelho compõem o total uma única vez. {@code EXPENSE_DECREASE} sem transação reduz no
 * máximo o total corrente da fatura; o excedente vira crédito para faturas futuras. O crédito
 * acumulado é aplicado à primeira fatura não paga em ordem de vencimento, nunca reabrindo faturas
 * pagas. Pagamentos não criam despesas.
 */
@Service
public class InvoiceBalanceService {

  private static final String INVOICE_TABLE = "invoice";
  private static final String CARD_TABLE = "card_profile";
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Money.SCALE);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final InvoicePaymentRepository paymentRepository;
  private final CardProfileRepository cardProfileRepository;
  private final ProviderBillService providerBillService;
  private final CryptoService cryptoService;
  private final Clock clock;

  public InvoiceBalanceService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      InvoicePaymentRepository paymentRepository,
      CardProfileRepository cardProfileRepository,
      ProviderBillService providerBillService,
      CryptoService cryptoService,
      Clock clock) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.paymentRepository = paymentRepository;
    this.cardProfileRepository = cardProfileRepository;
    this.providerBillService = providerBillService;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  @Transactional
  public void recalculateCard(UUID userId, UUID cardAccountId) {
    CardProfile profile =
        cardProfileRepository.findByAccountIdAndUserId(cardAccountId, userId).orElse(null);
    if (profile == null) {
      return;
    }
    List<Invoice> invoices =
        invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(userId, cardAccountId);
    // O crédito é consumido pela ordem de vencimento, com desempate estável pelo ciclo e pelo ID.
    invoices.sort(
        java.util.Comparator.comparing(Invoice::getDueDate)
            .thenComparing(Invoice::getCycleStart)
            .thenComparing(Invoice::getId));
    BigDecimal creditPool = ZERO;
    LocalDate today = clock.today();
    Map<UUID, BigDecimal> providerTotals =
        providerBillService.totalsByInvoice(userId, cardAccountId, invoices);

    for (Invoice invoice : invoices) {
      List<InvoiceItem> items =
          itemRepository.findByUserIdAndInvoiceIdAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
              userId, invoice.getId());
      Totals totals = totals(items);
      BigDecimal payments = paymentsTotal(userId, invoice.getId());
      BigDecimal applied = decryptInvoice(invoice, "applied_credit");
      BigDecimal providerTotal = providerTotals.get(invoice.getId());
      // O provedor pode confirmar um total maior que o local (compras ausentes). O excedente de
      // pagamento só vira crédito sobre a maior obrigação conhecida, nunca sobre dados parciais.
      BigDecimal effectiveTotal =
          providerTotal == null ? totals.total() : totals.total().max(providerTotal);
      InvoiceStatus status;

      if (invoice.getStatus() == InvoiceStatus.PAID) {
        // Fatura paga não é reaberta, mas o crédito aplicado é normalizado para manter
        // total = pagamentos + crédito aplicado. Isso corrige históricos em que o crédito
        // ficou menor que o total e o saldo devedor era forçado a zero.
        status = InvoiceStatus.PAID;
        applied = totals.total().subtract(payments).max(ZERO).min(totals.total());
        creditPool = creditPool.subtract(applied.min(creditPool));
        creditPool = creditPool.add(totals.overflow());
        creditPool = creditPool.add(excessPayment(effectiveTotal, applied, payments));
      } else {
        applied = creditPool.min(totals.total()).max(ZERO);
        creditPool = creditPool.subtract(applied);
        BigDecimal outstanding = totals.total().subtract(applied).subtract(payments).max(ZERO);
        status = statusFor(invoice, totals.total(), payments, outstanding, today);
        creditPool = creditPool.add(totals.overflow());
        creditPool = creditPool.add(excessPayment(effectiveTotal, applied, payments));
      }
      BigDecimal outstanding =
          status == InvoiceStatus.PAID
              ? ZERO
              : totals.total().subtract(applied).subtract(payments).max(ZERO);
      invoice.applyTotals(
          status,
          encrypt(invoice, "total", totals.total()),
          encrypt(invoice, "payments_total", payments),
          encrypt(invoice, "outstanding", outstanding),
          encrypt(invoice, "applied_credit", applied));
      invoiceRepository.save(invoice);
    }

    profile.updateCreditBalance(
        cryptoService.encrypt(
            creditPool.setScale(Money.SCALE, RoundingMode.HALF_EVEN).toPlainString(),
            new CryptoContext(userId, CARD_TABLE, "credit_balance", profile.getId())));
    cardProfileRepository.save(profile);
  }

  @Transactional
  public void recalculateInvoice(UUID userId, UUID invoiceId) {
    Invoice invoice =
        invoiceRepository.findByIdAndUserId(invoiceId, userId).orElseThrow(NotFoundException::new);
    recalculateCard(userId, invoice.getCardAccountId());
  }

  private Totals totals(List<InvoiceItem> items) {
    BigDecimal running = ZERO;
    BigDecimal overflow = ZERO;
    for (InvoiceItem item : items) {
      BigDecimal amount = decryptItem(item);
      boolean mirror = item.getTransactionId() != null;
      if (mirror) {
        if (item.getKind() == InvoiceItemKind.PURCHASE
            || item.getKind() == InvoiceItemKind.INSTALLMENT) {
          running = running.add(amount);
        } else if (item.getKind() == InvoiceItemKind.CREDIT
            || item.getKind() == InvoiceItemKind.REFUND) {
          BigDecimal applied = amount.min(running);
          running = running.subtract(applied);
          overflow = overflow.add(amount.subtract(applied));
        } else {
          running = running.add(amount);
        }
        continue;
      }
      if (item.getReportingEffect() == ReportingEffect.EXPENSE_INCREASE) {
        running = running.add(amount);
      } else if (item.getReportingEffect() == ReportingEffect.EXPENSE_DECREASE) {
        BigDecimal applied = amount.min(running);
        running = running.subtract(applied);
        overflow = overflow.add(amount.subtract(applied));
      }
    }
    return new Totals(
        running.setScale(Money.SCALE, RoundingMode.HALF_EVEN),
        overflow.setScale(Money.SCALE, RoundingMode.HALF_EVEN));
  }

  /**
   * Excedente de pagamento que deve virar crédito para faturas futuras.
   *
   * <p>Limitado a faturas com obrigação conhecida maior que zero: faturas históricas sem compras
   * locais (total zero) normalmente indicam lacuna do conector, não crédito real.
   */
  private static BigDecimal excessPayment(
      BigDecimal total, BigDecimal applied, BigDecimal payments) {
    if (total.signum() <= 0) {
      return ZERO;
    }
    BigDecimal obligation = total.subtract(applied).max(ZERO);
    return payments.subtract(obligation).max(ZERO);
  }

  private BigDecimal paymentsTotal(UUID userId, UUID invoiceId) {
    return paymentRepository
        .findByUserIdAndInvoiceIdOrderByPaidOnAscCreatedAtAsc(userId, invoiceId)
        .stream()
        .filter(payment -> payment.getStatus() == InvoicePaymentStatus.CONFIRMED)
        .map(payment -> decryptPayment(payment))
        .reduce(ZERO, BigDecimal::add);
  }

  private InvoiceStatus statusFor(
      Invoice invoice,
      BigDecimal total,
      BigDecimal payments,
      BigDecimal outstanding,
      LocalDate today) {
    if (total.signum() <= 0) {
      return today.isAfter(invoice.getClosingDate()) ? InvoiceStatus.CLOSED : InvoiceStatus.OPEN;
    }
    if (outstanding.signum() == 0) {
      return InvoiceStatus.PAID;
    }
    if (invoice.getDueDate().isBefore(today)) {
      // Atraso prevalece sobre pagamento parcial: a dívida restante já venceu.
      return InvoiceStatus.OVERDUE;
    }
    if (payments.signum() > 0) {
      return InvoiceStatus.PARTIALLY_PAID;
    }
    if (today.isAfter(invoice.getClosingDate())) {
      return InvoiceStatus.CLOSED;
    }
    return InvoiceStatus.OPEN;
  }

  private BigDecimal decryptInvoice(Invoice invoice, String column, byte[] value) {
    return new BigDecimal(
        cryptoService.decryptString(
            value, new CryptoContext(invoice.getUserId(), INVOICE_TABLE, column, invoice.getId())));
  }

  private BigDecimal decryptInvoice(Invoice invoice, String column) {
    return switch (column) {
      case "applied_credit" -> decryptInvoice(invoice, column, invoice.getAppliedCreditEnc());
      default -> throw new IllegalArgumentException("coluna inválida: " + column);
    };
  }

  private byte[] encrypt(Invoice invoice, String column, BigDecimal value) {
    return cryptoService.encrypt(
        value.setScale(Money.SCALE, RoundingMode.HALF_EVEN).toPlainString(),
        new CryptoContext(invoice.getUserId(), INVOICE_TABLE, column, invoice.getId()));
  }

  private BigDecimal decryptItem(InvoiceItem item) {
    return new BigDecimal(
        cryptoService.decryptString(
            item.getAmountEnc(),
            new CryptoContext(item.getUserId(), "invoice_item", "amount", item.getId())));
  }

  private BigDecimal decryptPayment(InvoicePayment payment) {
    return new BigDecimal(
        cryptoService.decryptString(
            payment.getAmountEnc(),
            new CryptoContext(payment.getUserId(), "invoice_payment", "amount", payment.getId())));
  }

  private record Totals(BigDecimal total, BigDecimal overflow) {}
}
