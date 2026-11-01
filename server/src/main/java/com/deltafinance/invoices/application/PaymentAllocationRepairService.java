package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoicePayment;
import com.deltafinance.invoices.domain.InvoicePaymentRepository;
import com.deltafinance.invoices.domain.InvoicePaymentStatus;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.transactions.domain.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Realoca pagamentos confirmados para a fatura não paga mais antiga.
 *
 * <p>A regra anterior usava o mês de faturamento menos um e recusava faturas {@code PAID}, o que
 * prendia pagamentos no ciclo errado. Aqui os pagamentos são percorridos em ordem cronológica e
 * cada um consome o saldo da primeira fatura ainda não coberta, pela ordem de vencimento. O
 * processo é idempotente e só muda pagamentos cujo ciclo de destino diverge do atual.
 *
 * <p>Deve ser executado por cartão. Cartões com histórico incompleto (fatura sem compras e sem
 * total, por exemplo) precisam de conferência manual antes.
 */
@Service
public class PaymentAllocationRepairService {

  private final TransactionRepository transactionRepository;
  private final InvoicePaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceBalanceService balanceService;
  private final CryptoService cryptoService;
  private final AuditService auditService;

  public PaymentAllocationRepairService(
      TransactionRepository transactionRepository,
      InvoicePaymentRepository paymentRepository,
      InvoiceRepository invoiceRepository,
      InvoiceBalanceService balanceService,
      CryptoService cryptoService,
      AuditService auditService) {
    this.transactionRepository = transactionRepository;
    this.paymentRepository = paymentRepository;
    this.invoiceRepository = invoiceRepository;
    this.balanceService = balanceService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
  }

  public record Report(boolean dryRun, UUID cardAccountId, int reassigned, BigDecimal amount) {}

  @Transactional
  public Report repairCard(UUID userId, UUID cardAccountId, boolean dryRun) {
    List<Invoice> invoices =
        new ArrayList<>(
            invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(
                userId, cardAccountId));
    invoices.sort(
        Comparator.comparing(Invoice::getDueDate)
            .thenComparing(Invoice::getCycleStart)
            .thenComparing(Invoice::getId));
    Map<UUID, BigDecimal> remaining = new LinkedHashMap<>();
    for (Invoice invoice : invoices) {
      remaining.put(invoice.getId(), decryptTotal(userId, invoice));
    }
    List<InvoicePayment> payments = new ArrayList<>();
    for (Invoice invoice : invoices) {
      payments.addAll(
          paymentRepository.findByUserIdAndInvoiceIdOrderByPaidOnAscCreatedAtAsc(
              userId, invoice.getId()));
    }
    payments.sort(
        Comparator.comparing(InvoicePayment::getPaidOn)
            .thenComparing(InvoicePayment::getCreatedAt)
            .thenComparing(InvoicePayment::getId));

    int reassigned = 0;
    BigDecimal moved = BigDecimal.ZERO;
    for (InvoicePayment payment : payments) {
      if (payment.getStatus() != InvoicePaymentStatus.CONFIRMED) {
        continue;
      }
      BigDecimal amount = decryptAmount(userId, payment);
      UUID target = oldestUnpaid(remaining, amount);
      if (target == null || target.equals(payment.getInvoiceId())) {
        continue;
      }
      reassigned++;
      moved = moved.add(amount);
      if (dryRun) {
        continue;
      }
      payment.reassignInvoice(target);
      paymentRepository.save(payment);
      if (payment.getSourceTransactionId() != null) {
        transactionRepository
            .findByIdAndUserId(payment.getSourceTransactionId(), userId)
            .ifPresent(
                transaction -> {
                  transaction.setInvoiceId(target);
                  transactionRepository.save(transaction);
                });
      }
      auditService.record(
          userId,
          AuditEventType.INVOICE_RECONCILED,
          "invoice_payment",
          payment.getId(),
          Map.of("reason", "payment-allocation-repair", "invoiceId", target.toString()));
    }
    if (!dryRun && reassigned > 0) {
      balanceService.recalculateCard(userId, cardAccountId);
    }
    return new Report(dryRun, cardAccountId, reassigned, moved.setScale(2, RoundingMode.HALF_EVEN));
  }

  /**
   * Consome o valor da primeira fatura com saldo em aberto e devolve o ciclo tocado.
   *
   * <p>Pagamentos que cobrem mais de uma fatura são atribuídos à primeira; o recálculo distribui o
   * excedente como crédito, como já ocorre no fluxo normal.
   */
  private UUID oldestUnpaid(Map<UUID, BigDecimal> remaining, BigDecimal amount) {
    BigDecimal left = amount;
    UUID first = null;
    for (Map.Entry<UUID, BigDecimal> entry : remaining.entrySet()) {
      BigDecimal available = entry.getValue();
      if (available.signum() <= 0) {
        continue;
      }
      BigDecimal applied = left.min(available);
      if (applied.signum() > 0 && first == null) {
        first = entry.getKey();
      }
      entry.setValue(available.subtract(applied));
      left = left.subtract(applied);
      if (left.signum() <= 0) {
        break;
      }
    }
    return first;
  }

  private BigDecimal decryptTotal(UUID userId, Invoice invoice) {
    return new BigDecimal(
            cryptoService.decryptString(
                invoice.getTotalEnc(),
                new CryptoContext(userId, "invoice", "total", invoice.getId())))
        .max(BigDecimal.ZERO);
  }

  private BigDecimal decryptAmount(UUID userId, InvoicePayment payment) {
    return new BigDecimal(
        cryptoService.decryptString(
            payment.getAmountEnc(),
            new CryptoContext(userId, "invoice_payment", "amount", payment.getId())));
  }
}
