package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.ProviderBill;
import com.deltafinance.invoices.domain.ProviderBillPayment;
import com.deltafinance.invoices.domain.ProviderBillPaymentRepository;
import com.deltafinance.invoices.domain.ProviderBillRepository;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
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
 * vencimento. As Bills nunca reescrevem compras locais. Seguindo a semântica do Pluggy, os
 * pagamentos de uma Bill liquidam a fatura anterior; por isso a conciliação de uma fatura usa os
 * pagamentos da Bill seguinte, acrescidos dos encargos financeiros desse ciclo.
 */
@Service
public class ProviderBillService {

  private static final String BILL_TABLE = "provider_bill";
  private static final String PAYMENT_TABLE = "provider_bill_payment";
  private static final String INVOICE_TABLE = "invoice";
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private static final BigDecimal ZERO = Money.scaled(BigDecimal.ZERO);

  private final ProviderBillRepository billRepository;
  private final ProviderBillPaymentRepository paymentRepository;
  private final EncryptedValueReader encryptedValueReader;

  public ProviderBillService(
      ProviderBillRepository billRepository,
      ProviderBillPaymentRepository paymentRepository,
      EncryptedValueReader encryptedValueReader) {
    this.billRepository = billRepository;
    this.paymentRepository = paymentRepository;
    this.encryptedValueReader = encryptedValueReader;
  }

  /**
   * Quitação oficial de uma fatura local.
   *
   * <p>{@code officialPayments} é o total pago no ciclo seguinte; {@code appliedToInvoice} limita
   * esse total ao valor da Bill anterior, porque os encargos do ciclo seguinte não pagam compras da
   * fatura anterior. {@code settled} segue a regra documentada pelo provedor.
   */
  public record InvoiceSettlement(
      BigDecimal providerTotal,
      BigDecimal officialPayments,
      BigDecimal appliedToInvoice,
      BigDecimal financeCharges,
      boolean settled,
      List<ProviderBillPayment> payments,
      int ignoredPayments) {}

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
    List<ProviderBill> bills = bills(userId, cardAccountId);
    Map<UUID, ProviderBill> matches = matches(invoices, bills);
    Map<UUID, ProviderBillView> result = new HashMap<>();
    for (Invoice invoice : invoices) {
      ProviderBill bill = matches.get(invoice.getId());
      if (bill != null) {
        result.put(invoice.getId(), view(userId, invoice, bill, bills));
      }
    }
    return result;
  }

  /** Quitação oficial por fatura local, usada pelo cálculo de saldo e status. */
  public Map<UUID, InvoiceSettlement> settlementsByInvoice(
      UUID userId, UUID cardAccountId, List<Invoice> invoices) {
    List<ProviderBill> bills = bills(userId, cardAccountId);
    Map<UUID, ProviderBill> matches = matches(invoices, bills);
    Map<UUID, InvoiceSettlement> result = new HashMap<>();
    for (Invoice invoice : invoices) {
      ProviderBill bill = matches.get(invoice.getId());
      if (bill != null) {
        result.put(invoice.getId(), settlement(userId, bill, bills));
      }
    }
    return result;
  }

  private Map<UUID, ProviderBill> matches(List<Invoice> invoices, List<ProviderBill> bills) {
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

  private ProviderBillView view(
      UUID userId, Invoice invoice, ProviderBill bill, List<ProviderBill> bills) {
    BigDecimal localTotal = decryptInvoice(invoice, "total", invoice.getTotalEnc());
    BigDecimal localPayments =
        decryptInvoice(invoice, "payments_total", invoice.getPaymentsTotalEnc());
    BigDecimal providerMinimum =
        bill.getMinimumPaymentEnc() == null
            ? null
            : decryptBill(bill, "minimum_payment", bill.getMinimumPaymentEnc());
    InvoiceSettlement settlement = settlement(userId, bill, bills);

    BigDecimal totalDifference = localTotal.subtract(settlement.providerTotal());
    BigDecimal paymentsDifference = localPayments.subtract(settlement.appliedToInvoice());
    List<ProviderBillPaymentView> paymentViews =
        settlement.payments().stream().map(this::paymentView).toList();

    return new ProviderBillView(
        bill.getClosingDate(),
        bill.getDueDate(),
        format(settlement.providerTotal()),
        providerMinimum == null ? null : format(providerMinimum),
        format(settlement.appliedToInvoice()),
        format(settlement.financeCharges()),
        settlement.settled(),
        format(totalDifference),
        format(paymentsDifference),
        totalDifference.signum() == 0 && paymentsDifference.signum() == 0,
        settlement.ignoredPayments(),
        bill.getSyncedAt().atZone(ZONE).toLocalDate(),
        paymentViews);
  }

  private InvoiceSettlement settlement(UUID userId, ProviderBill bill, List<ProviderBill> bills) {
    BigDecimal providerTotal = decryptBill(bill, "total", bill.getTotalEnc());
    ProviderBill next = nextBill(bills, bill);
    OfficialPayments official =
        next == null ? new OfficialPayments(List.of(), 0) : officialPayments(userId, next);
    BigDecimal officialPayments =
        official.payments().stream()
            .map(payment -> decryptPayment(payment))
            .reduce(ZERO, BigDecimal::add);
    BigDecimal financeCharges =
        next == null
            ? ZERO
            : decryptBill(next, "finance_charges_total", next.getFinanceChargesTotalEnc());
    BigDecimal required = providerTotal.add(financeCharges);
    BigDecimal appliedToInvoice = officialPayments.min(providerTotal).max(ZERO);
    boolean settled =
        next != null && required.signum() > 0 && officialPayments.compareTo(required) >= 0;
    return new InvoiceSettlement(
        providerTotal,
        officialPayments,
        appliedToInvoice,
        financeCharges,
        settled,
        official.payments(),
        official.ignored());
  }

  private OfficialPayments officialPayments(UUID userId, ProviderBill bill) {
    List<ProviderBillPayment> allPayments =
        paymentRepository.findByUserIdAndProviderBillIdOrderByPaymentDateAscCreatedAtAsc(
            userId, bill.getId());
    LocalDate windowStart =
        (bill.getClosingDate() == null ? bill.getDueDate() : bill.getClosingDate()).minusMonths(2);
    LocalDate windowEnd = bill.getDueDate().plusMonths(1);
    List<ProviderBillPayment> filtered =
        allPayments.stream()
            .filter(
                payment ->
                    !payment.getPaymentDate().isBefore(windowStart)
                        && !payment.getPaymentDate().isAfter(windowEnd))
            .toList();
    return new OfficialPayments(filtered, allPayments.size() - filtered.size());
  }

  private static ProviderBill nextBill(List<ProviderBill> bills, ProviderBill bill) {
    int index = bills.indexOf(bill);
    if (index < 0 || index + 1 >= bills.size()) {
      return null;
    }
    return bills.get(index + 1);
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
    return encryptedValueReader.readMoney(
        invoice.getUserId(), INVOICE_TABLE, column, invoice.getId(), value);
  }

  private BigDecimal decryptBill(ProviderBill bill, String column, byte[] value) {
    if (value == null) {
      return ZERO;
    }
    return encryptedValueReader.readMoney(
        bill.getUserId(), BILL_TABLE, column, bill.getId(), value);
  }

  private BigDecimal decryptPayment(ProviderBillPayment payment) {
    return encryptedValueReader.readMoney(
        payment.getUserId(), PAYMENT_TABLE, "amount", payment.getId(), payment.getAmountEnc());
  }

  private static String format(BigDecimal value) {
    return Money.plain(value);
  }

  private record OfficialPayments(List<ProviderBillPayment> payments, int ignored) {}
}
