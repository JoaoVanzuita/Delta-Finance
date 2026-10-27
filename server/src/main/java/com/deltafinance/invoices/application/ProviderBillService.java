package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.ProviderBill;
import com.deltafinance.invoices.domain.ProviderBillPayment;
import com.deltafinance.invoices.domain.ProviderBillPaymentRepository;
import com.deltafinance.invoices.domain.ProviderBillRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Associa as Bills oficiais às faturas locais para conciliação.
 *
 * <p>O vínculo é por ciclo: primeiro pela data de fechamento e, quando o provedor não a envia, pelo
 * vencimento. As Bills nunca alteram a fatura local. Seguindo a semântica do Pluggy, os pagamentos
 * de uma Bill liquidam a fatura anterior; por isso a conciliação de uma fatura usa os pagamentos da
 * Bill seguinte quando ela existe.
 */
@Service
public class ProviderBillService {

  private static final String BILL_TABLE = "provider_bill";
  private static final String PAYMENT_TABLE = "provider_bill_payment";
  private static final String INVOICE_TABLE = "invoice";
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  private final ProviderBillRepository billRepository;
  private final ProviderBillPaymentRepository paymentRepository;
  private final CryptoService cryptoService;

  public ProviderBillService(
      ProviderBillRepository billRepository,
      ProviderBillPaymentRepository paymentRepository,
      CryptoService cryptoService) {
    this.billRepository = billRepository;
    this.paymentRepository = paymentRepository;
    this.cryptoService = cryptoService;
  }

  /** Bills do cartão em ordem crescente de vencimento. */
  public List<ProviderBill> bills(UUID userId, UUID cardAccountId) {
    List<ProviderBill> bills =
        new ArrayList<>(
            billRepository.findByUserIdAndCardAccountIdOrderByDueDateDesc(userId, cardAccountId));
    bills.sort(Comparator.comparing(ProviderBill::getDueDate).thenComparing(ProviderBill::getId));
    return bills;
  }

  /** Mapeia cada fatura local para a Bill oficial correspondente, quando existir. */
  public Map<UUID, ProviderBillView> views(
      UUID userId, UUID cardAccountId, List<Invoice> invoices) {
    Map<UUID, ProviderBill> matches = matches(userId, cardAccountId, invoices);
    Map<UUID, ProviderBillView> result = new HashMap<>();
    for (Invoice invoice : invoices) {
      ProviderBill bill = matches.get(invoice.getId());
      if (bill != null) {
        result.put(invoice.getId(), view(userId, invoice, bill));
      }
    }
    return result;
  }

  /** Totais oficiais por fatura local, usados no cálculo de crédito por pagamento excedente. */
  public Map<UUID, BigDecimal> totalsByInvoice(
      UUID userId, UUID cardAccountId, List<Invoice> invoices) {
    Map<UUID, BigDecimal> totals = new HashMap<>();
    for (Map.Entry<UUID, ProviderBill> entry :
        matches(userId, cardAccountId, invoices).entrySet()) {
      totals.put(
          entry.getKey(), decryptBill(entry.getValue(), "total", entry.getValue().getTotalEnc()));
    }
    return totals;
  }

  private Map<UUID, ProviderBill> matches(UUID userId, UUID cardAccountId, List<Invoice> invoices) {
    List<ProviderBill> bills = bills(userId, cardAccountId);
    Map<UUID, ProviderBill> result = new HashMap<>();
    if (bills.isEmpty() || invoices.isEmpty()) {
      return result;
    }
    Set<UUID> used = new HashSet<>();
    for (Invoice invoice : invoices) {
      ProviderBill bill = match(invoice, bills, used);
      if (bill == null) {
        continue;
      }
      used.add(bill.getId());
      result.put(invoice.getId(), bill);
    }
    return result;
  }

  private ProviderBillView view(UUID userId, Invoice invoice, ProviderBill bill) {
    BigDecimal localTotal = decryptInvoice(invoice, "total", invoice.getTotalEnc());
    BigDecimal localPayments =
        decryptInvoice(invoice, "payments_total", invoice.getPaymentsTotalEnc());
    BigDecimal providerTotal = decryptBill(bill, "total", bill.getTotalEnc());
    BigDecimal providerMinimum =
        bill.getMinimumPaymentEnc() == null
            ? null
            : decryptBill(bill, "minimum_payment", bill.getMinimumPaymentEnc());

    List<ProviderBillPayment> allPayments =
        paymentRepository.findByUserIdAndProviderBillIdOrderByPaymentDateAscCreatedAtAsc(
            userId, bill.getId());
    LocalDate windowStart =
        (bill.getClosingDate() == null ? bill.getDueDate() : bill.getClosingDate()).minusMonths(2);
    LocalDate windowEnd = bill.getDueDate().plusMonths(1);
    List<ProviderBillPayment> officialPayments =
        allPayments.stream()
            .filter(
                payment ->
                    !payment.getPaymentDate().isBefore(windowStart)
                        && !payment.getPaymentDate().isAfter(windowEnd))
            .toList();
    BigDecimal providerPayments =
        officialPayments.stream()
            .map(payment -> decryptPayment(payment))
            .reduce(BigDecimal.ZERO.setScale(Money.SCALE), BigDecimal::add);

    BigDecimal totalDifference = localTotal.subtract(providerTotal);
    BigDecimal paymentsDifference = localPayments.subtract(providerPayments);
    List<ProviderBillPaymentView> paymentViews =
        officialPayments.stream().map(payment -> paymentView(payment)).toList();

    return new ProviderBillView(
        bill.getClosingDate(),
        bill.getDueDate(),
        format(providerTotal),
        providerMinimum == null ? null : format(providerMinimum),
        format(providerPayments),
        format(totalDifference),
        format(paymentsDifference),
        totalDifference.signum() == 0 && paymentsDifference.signum() == 0,
        allPayments.size() - officialPayments.size(),
        bill.getSyncedAt().atZone(ZONE).toLocalDate(),
        paymentViews);
  }

  private ProviderBill match(Invoice invoice, List<ProviderBill> bills, Set<UUID> used) {
    for (ProviderBill bill : bills) {
      if (used.contains(bill.getId()) || bill.getClosingDate() == null) {
        continue;
      }
      if (bill.getClosingDate().equals(invoice.getClosingDate())) {
        return bill;
      }
    }
    for (ProviderBill bill : bills) {
      if (used.contains(bill.getId()) || bill.getClosingDate() == null) {
        continue;
      }
      if (YearMonth.from(bill.getClosingDate()).equals(YearMonth.from(invoice.getClosingDate()))) {
        return bill;
      }
    }
    for (ProviderBill bill : bills) {
      if (used.contains(bill.getId())) {
        continue;
      }
      if (bill.getDueDate().equals(invoice.getDueDate())) {
        return bill;
      }
    }
    for (ProviderBill bill : bills) {
      if (used.contains(bill.getId())) {
        continue;
      }
      if (YearMonth.from(bill.getDueDate()).equals(YearMonth.from(invoice.getDueDate()))) {
        return bill;
      }
    }
    return null;
  }

  private ProviderBillPaymentView paymentView(ProviderBillPayment payment) {
    return new ProviderBillPaymentView(
        payment.getPaymentDate(),
        format(decryptPayment(payment)),
        payment.getPaymentMode(),
        payment.getValueType());
  }

  private BigDecimal decryptInvoice(Invoice invoice, String column, byte[] value) {
    return new BigDecimal(
        cryptoService.decryptString(
            value, new CryptoContext(invoice.getUserId(), INVOICE_TABLE, column, invoice.getId())));
  }

  private BigDecimal decryptBill(ProviderBill bill, String column, byte[] value) {
    return new BigDecimal(
        cryptoService.decryptString(
            value, new CryptoContext(bill.getUserId(), BILL_TABLE, column, bill.getId())));
  }

  private BigDecimal decryptPayment(ProviderBillPayment payment) {
    return new BigDecimal(
        cryptoService.decryptString(
            payment.getAmountEnc(),
            new CryptoContext(payment.getUserId(), PAYMENT_TABLE, "amount", payment.getId())));
  }

  private static String format(BigDecimal value) {
    return value.setScale(Money.SCALE, RoundingMode.HALF_EVEN).toPlainString();
  }
}
