package com.deltafinance.transactions.application;

import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.categories.application.CategoryTaxonomy;
import com.deltafinance.categories.application.CategoryView;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reclassifica débitos bancários de pagamento de fatura como Transferências.
 *
 * <p>O débito na conta que quita a fatura não é despesa: a despesa já está nas compras do cartão.
 * Sem essa reclassificação o mesmo gasto aparece duas vezes (compra + pagamento). Só atua em
 * descrições explícitas de pagamento de fatura/cartão e respeita categoria editada manualmente.
 */
@Service
public class CardPaymentCategoryBackfillService {

  private final TransactionRepository transactionRepository;
  private final FinancialAccountRepository accountRepository;
  private final CategoryService categoryService;
  private final EncryptedValueReader encryptedValueReader;
  private final AuditService auditService;

  public CardPaymentCategoryBackfillService(
      TransactionRepository transactionRepository,
      FinancialAccountRepository accountRepository,
      CategoryService categoryService,
      EncryptedValueReader encryptedValueReader,
      AuditService auditService) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.categoryService = categoryService;
    this.encryptedValueReader = encryptedValueReader;
    this.auditService = auditService;
  }

  public record Report(boolean dryRun, int reclassified, BigDecimal amount) {}

  @Transactional
  public Report backfillUser(UUID userId, boolean dryRun) {
    Map<UUID, FinancialAccount> accounts = new HashMap<>();
    for (FinancialAccount account : accountRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
      accounts.put(account.getId(), account);
    }
    Map<UUID, CategoryKind> categoryKinds = new HashMap<>();
    for (CategoryView category : categoryService.listExisting(userId)) {
      categoryKinds.put(category.id(), CategoryKind.valueOf(category.kind()));
    }
    UUID transferId = categoryService.canonicalCategoryId(userId, CategoryTaxonomy.TRANSFERENCIAS);
    int reclassified = 0;
    BigDecimal amount = BigDecimal.ZERO;
    for (Transaction transaction : transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
      if (transaction.getType() != TransactionType.DEBIT
          || transaction.hasEdited("category")
          || transferId.equals(transaction.getCategoryId())) {
        continue;
      }
      FinancialAccount account = accounts.get(transaction.getAccountId());
      if (account == null || account.getType() == AccountType.CREDIT) {
        continue;
      }
      if (categoryKinds.get(transaction.getCategoryId()) == CategoryKind.TRANSFER) {
        continue;
      }
      String description =
          encryptedValueReader.read(
              userId,
              "transaction",
              "description_effective",
              transaction.getId(),
              transaction.getDescriptionEffectiveEnc());
      if (!CategoryTaxonomy.isCardInvoicePaymentDescription(description)) {
        continue;
      }
      BigDecimal value =
          encryptedValueReader.readMoney(
              userId,
              "transaction",
              "amount_effective",
              transaction.getId(),
              transaction.getAmountEffectiveEnc());
      reclassified++;
      amount = amount.add(value.abs());
      if (!dryRun) {
        transaction.setCategoryId(transferId);
        transactionRepository.save(transaction);
        auditService.record(
            userId,
            AuditEventType.TRANSACTION_CORRECTED,
            "transaction",
            transaction.getId(),
            Map.of("field", "category", "reason", "card-invoice-payment"));
      }
    }
    return new Report(dryRun, reclassified, amount);
  }
}
