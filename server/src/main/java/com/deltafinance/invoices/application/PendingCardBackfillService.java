package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfill documental de compras de cartão presas em {@code PENDING}.
 *
 * <p>Quando o conector deixa compras antigas como {@code PENDING} mesmo após o fechamento das
 * faturas, o app não consegue gerar itens nem alocar pagamentos. Este procedimento promove para
 * {@code POSTED} as transações que já têm um mês de fatura informado pelo provedor e cuja fatura já
 * fechou, usando as faturas oficiais como evidência. Em seguida roda a geração de faturas normal,
 * que cria os ciclos ausentes e converte os pagamentos em {@code invoice_payment}.
 *
 * <p>É uma operação de manutenção, idempotente e auditada. Não deve rodar no fluxo normal nem ser
 * usada para "adivinhar" status: só entram transações importadas, com {@code provider_bill_month}
 * preenchido e dentro da janela explicitamente informada.
 */
@Service
public class PendingCardBackfillService {

  private final TransactionRepository transactionRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceGenerationService invoiceGenerationService;
  private final CardCycleService cardCycleService;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final Clock clock;

  public PendingCardBackfillService(
      TransactionRepository transactionRepository,
      InvoiceRepository invoiceRepository,
      InvoiceGenerationService invoiceGenerationService,
      CardCycleService cardCycleService,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceGenerationService = invoiceGenerationService;
    this.cardCycleService = cardCycleService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  public record Report(
      boolean dryRun,
      UUID cardAccountId,
      int promoted,
      int skippedBeforeWindow,
      BigDecimal purchases,
      BigDecimal credits,
      int invoicesBefore,
      int invoicesAfter) {}

  /**
   * Promove as transações pendentes da janela e recalcula as faturas do cartão.
   *
   * @param fromBillMonth primeiro mês de fatura incluído (inclusive)
   * @param untilBillMonth último mês de fatura incluído (inclusive)
   */
  @Transactional
  public Report backfill(
      UUID userId,
      UUID cardAccountId,
      LocalDate fromBillMonth,
      LocalDate untilBillMonth,
      boolean dryRun) {
    int promoted = 0;
    int skipped = 0;
    BigDecimal purchases = BigDecimal.ZERO;
    BigDecimal credits = BigDecimal.ZERO;
    CardProfile profile = cardCycleService.profileFor(userId, cardAccountId).orElse(null);
    if (profile == null) {
      return new Report(dryRun, cardAccountId, 0, 0, purchases, credits, 0, 0);
    }
    int invoicesBefore =
        invoiceRepository
            .findByUserIdAndCardAccountIdOrderByCycleStartDesc(userId, cardAccountId)
            .size();

    for (Transaction transaction :
        transactionRepository.findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(
            userId, cardAccountId)) {
      if (transaction.getStatus() != TransactionStatus.PENDING
          || transaction.getSource() != TransactionSource.IMPORTED
          || transaction.getReconciliationState() == ReconciliationState.SOURCE_REMOVED) {
        continue;
      }
      LocalDate billMonth = transaction.getProviderBillMonth();
      if (billMonth == null) {
        continue;
      }
      if (billMonth.isBefore(fromBillMonth) || billMonth.isAfter(untilBillMonth)) {
        skipped++;
        continue;
      }
      // Só promove depois que a fatura do ciclo já fechou; compras do ciclo aberto continuam
      // PENDING até o provedor confirmar.
      CardCycleService.CardCycle cycle =
          cardCycleService.cycleForBillMonth(profile, YearMonth.from(billMonth));
      if (cycle.closingDate().isAfter(clock.today())) {
        skipped++;
        continue;
      }
      BigDecimal amount = decryptAmount(userId, transaction);
      if (amount.signum() < 0) {
        purchases = purchases.add(amount.abs());
      } else {
        credits = credits.add(amount);
      }
      promoted++;
      if (!dryRun) {
        transaction.markStatus(TransactionStatus.POSTED);
        // O provedor continua devolvendo PENDING; sem esta marca o próximo sync desfaria o
        // backfill documental.
        transaction.markEdited("status");
        transactionRepository.save(transaction);
        auditService.record(
            userId,
            AuditEventType.TRANSACTION_CORRECTED,
            "transaction",
            transaction.getId(),
            Map.of(
                "previousStatus", "PENDING",
                "status", "POSTED",
                "reason", "invoice-backfill"));
      }
    }

    if (!dryRun && promoted > 0) {
      // A geração cria itens, faturas ausentes, pagamentos e recalcula totais/status.
      invoiceGenerationService.ensureInvoices(userId, cardAccountId);
    }

    int invoicesAfter =
        invoiceRepository
            .findByUserIdAndCardAccountIdOrderByCycleStartDesc(userId, cardAccountId)
            .size();
    return new Report(
        dryRun,
        cardAccountId,
        promoted,
        skipped,
        purchases.setScale(2, RoundingMode.HALF_EVEN),
        credits.setScale(2, RoundingMode.HALF_EVEN),
        invoicesBefore,
        invoicesAfter);
  }

  private BigDecimal decryptAmount(UUID userId, Transaction transaction) {
    return new BigDecimal(
        cryptoService.decryptString(
            transaction.getAmountEffectiveEnc(),
            new CryptoContext(userId, "transaction", "amount_effective", transaction.getId())));
  }
}
