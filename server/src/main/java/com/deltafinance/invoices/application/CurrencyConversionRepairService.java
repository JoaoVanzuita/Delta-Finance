package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.ReportingEffect;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converte ajustes manuais de conciliação em valor efetivo de transações internacionais.
 *
 * <p>Quando uma compra em moeda estrangeira era importada sem a conversão ({@code
 * amountInAccountCurrency}), a fatura só fechava com um item manual de "Conciliação Bill oficial".
 * Este procedimento soma esse ajuste ao item espelho, grava o valor convertido na transação,
 * preserva a moeda original em {@code source_currency} e remove o ajuste manual. O total da fatura
 * não muda.
 */
@Service
public class CurrencyConversionRepairService {

  private static final String TRANSACTION_TABLE = "transaction";
  private static final String ITEM_TABLE = "invoice_item";

  private final TransactionRepository transactionRepository;
  private final FinancialAccountRepository accountRepository;
  private final InvoiceItemRepository itemRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceBalanceService balanceService;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final Clock clock;

  public CurrencyConversionRepairService(
      TransactionRepository transactionRepository,
      FinancialAccountRepository accountRepository,
      InvoiceItemRepository itemRepository,
      InvoiceRepository invoiceRepository,
      InvoiceBalanceService balanceService,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.itemRepository = itemRepository;
    this.invoiceRepository = invoiceRepository;
    this.balanceService = balanceService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  public record Report(boolean dryRun, int repaired, BigDecimal amount) {}

  @Transactional
  public Report repairUser(UUID userId, boolean dryRun) {
    int repaired = 0;
    BigDecimal total = BigDecimal.ZERO;
    for (Transaction transaction : transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
      if (transaction.hasEdited("amount") || transaction.getSourceCurrency() != null) {
        continue;
      }
      FinancialAccount account =
          accountRepository.findByIdAndUserId(transaction.getAccountId(), userId).orElse(null);
      if (account == null || account.getCurrency().equalsIgnoreCase(transaction.getCurrency())) {
        continue;
      }
      List<InvoiceItem> mirrors =
          itemRepository.findByUserIdAndTransactionIdAndRemovedAtIsNull(
              userId, transaction.getId());
      if (mirrors.isEmpty()) {
        continue;
      }
      BigDecimal adjustment = BigDecimal.ZERO;
      List<InvoiceItem> adjustments = new ArrayList<>();
      for (InvoiceItem mirror : mirrors) {
        for (InvoiceItem candidate :
            itemRepository
                .findByUserIdAndInvoiceIdAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
                    userId, mirror.getInvoiceId())) {
          if (candidate.getTransactionId() != null
              || candidate.getReportingEffect() != ReportingEffect.EXPENSE_INCREASE) {
            continue;
          }
          String description = decryptItemDescription(candidate);
          if (!isBillReconciliation(description)) {
            continue;
          }
          adjustment = adjustment.add(decryptItemAmount(candidate));
          adjustments.add(candidate);
        }
      }
      if (adjustment.signum() <= 0) {
        continue;
      }
      BigDecimal sign =
          decryptTransactionAmount(userId, transaction).signum() < 0
              ? BigDecimal.ONE.negate()
              : BigDecimal.ONE;
      BigDecimal converted = BigDecimal.ZERO;
      for (InvoiceItem mirror : mirrors) {
        converted = converted.add(decryptItemAmount(mirror));
      }
      converted = converted.add(adjustment).setScale(2, RoundingMode.HALF_EVEN);
      repaired++;
      total = total.add(converted);
      if (dryRun) {
        continue;
      }
      transaction.setAmountEffectiveEnc(
          cryptoService.encrypt(
              converted.multiply(sign).toPlainString(),
              new CryptoContext(
                  userId, TRANSACTION_TABLE, "amount_effective", transaction.getId())));
      String sourceCurrency = transaction.getCurrency();
      transaction.applyAccountCurrency(account.getCurrency());
      transaction.applySourceCurrency(sourceCurrency);
      transactionRepository.save(transaction);

      boolean first = true;
      for (InvoiceItem mirror : mirrors) {
        BigDecimal newAmount = decryptItemAmount(mirror);
        if (first) {
          newAmount = newAmount.add(adjustment);
          first = false;
        }
        mirror.applyMirror(
            mirror.getKind(),
            mirror.getReportingEffect(),
            mirror.getDescriptionEnc(),
            cryptoService.encrypt(
                newAmount.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                new CryptoContext(userId, ITEM_TABLE, "amount", mirror.getId())),
            mirror.getOccurredOn());
        itemRepository.save(mirror);
      }
      for (InvoiceItem candidate : adjustments) {
        candidate.remove(clock.now());
        itemRepository.save(candidate);
      }
      Invoice invoice =
          invoiceRepository
              .findByIdAndUserId(mirrors.getFirst().getInvoiceId(), userId)
              .orElse(null);
      if (invoice != null) {
        balanceService.recalculateCard(userId, invoice.getCardAccountId());
      }
      auditService.record(
          userId,
          AuditEventType.TRANSACTION_CORRECTED,
          "transaction",
          transaction.getId(),
          Map.of(
              "field", "amount",
              "reason", "currency-conversion",
              "sourceCurrency", transaction.getSourceCurrency()));
    }
    return new Report(dryRun, repaired, total.setScale(2, RoundingMode.HALF_EVEN));
  }

  private BigDecimal decryptTransactionAmount(UUID userId, Transaction transaction) {
    return new BigDecimal(
        cryptoService.decryptString(
            transaction.getAmountEffectiveEnc(),
            new CryptoContext(userId, TRANSACTION_TABLE, "amount_effective", transaction.getId())));
  }

  private BigDecimal decryptItemAmount(InvoiceItem item) {
    return new BigDecimal(
        cryptoService.decryptString(
            item.getAmountEnc(),
            new CryptoContext(item.getUserId(), ITEM_TABLE, "amount", item.getId())));
  }

  private String decryptItemDescription(InvoiceItem item) {
    try {
      return cryptoService.decryptString(
          item.getDescriptionEnc(),
          new CryptoContext(item.getUserId(), ITEM_TABLE, "description", item.getId()));
    } catch (RuntimeException exception) {
      return "";
    }
  }

  private static boolean isBillReconciliation(String description) {
    String normalized =
        Normalizer.normalize(description == null ? "" : description, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return normalized.contains("conciliacao") && normalized.contains("bill oficial");
  }
}
