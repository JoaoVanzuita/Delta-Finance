package com.deltafinance.transactions.application;

import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpa duplicatas históricas {@code PENDING}/{@code POSTED} de pagamentos de cartão.
 *
 * <p>A via efetiva representa o pagamento; a pendente é marcada como {@code POSTED} e {@code
 * MATCHED} para sair do bloco de pendências e dos totais, sem apagar histórico. O import passou a
 * recusar novas vias pendentes quando já existe uma efetiva equivalente.
 */
@Service
public class PendingDuplicateCleanupService {

  private static final String TABLE = "transaction";

  private final TransactionRepository transactionRepository;
  private final FinancialAccountRepository accountRepository;
  private final CryptoService cryptoService;
  private final AuditService auditService;

  public PendingDuplicateCleanupService(
      TransactionRepository transactionRepository,
      FinancialAccountRepository accountRepository,
      CryptoService cryptoService,
      AuditService auditService) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
  }

  public record Report(boolean dryRun, int cleaned, BigDecimal amount) {}

  @Transactional
  public Report cleanupUser(UUID userId, boolean dryRun) {
    Map<UUID, FinancialAccount> accounts = new HashMap<>();
    for (FinancialAccount account : accountRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
      accounts.put(account.getId(), account);
    }
    int cleaned = 0;
    BigDecimal amount = BigDecimal.ZERO;
    for (Transaction pending : transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
      if (pending.getStatus() != TransactionStatus.PENDING
          || pending.getType() != TransactionType.REFUND) {
        continue;
      }
      FinancialAccount account = accounts.get(pending.getAccountId());
      if (account == null || account.getType() != AccountType.CREDIT) {
        continue;
      }
      String description = decryptDescription(userId, pending);
      if (!looksLikeCardPayment(description)) {
        continue;
      }
      BigDecimal value = decryptAmount(userId, pending);
      if (!hasEffectiveTwin(userId, pending, value)) {
        continue;
      }
      cleaned++;
      amount = amount.add(value.abs());
      if (dryRun) {
        continue;
      }
      pending.markStatus(TransactionStatus.POSTED);
      pending.setReconciliationState(ReconciliationState.MATCHED);
      transactionRepository.save(pending);
      auditService.record(
          userId,
          AuditEventType.TRANSACTION_CORRECTED,
          "transaction",
          pending.getId(),
          Map.of("previousStatus", "PENDING", "status", "POSTED", "reason", "pending-duplicate"));
    }
    return new Report(dryRun, cleaned, amount);
  }

  private boolean hasEffectiveTwin(UUID userId, Transaction pending, BigDecimal value) {
    for (Transaction candidate :
        transactionRepository.findByUserIdAndAccountIdAndPostedOn(
            userId, pending.getAccountId(), pending.getPostedOn())) {
      if (candidate.getId().equals(pending.getId())
          || candidate.getType() != pending.getType()
          || (candidate.getStatus() != TransactionStatus.POSTED
              && candidate.getStatus() != TransactionStatus.RECONCILED)) {
        continue;
      }
      BigDecimal candidateValue = decryptAmount(userId, candidate);
      if (candidateValue.compareTo(value) == 0
          && looksLikeCardPayment(decryptDescription(userId, candidate))) {
        return true;
      }
    }
    return false;
  }

  private BigDecimal decryptAmount(UUID userId, Transaction transaction) {
    return new BigDecimal(
        cryptoService.decryptString(
            transaction.getAmountEffectiveEnc(),
            new CryptoContext(userId, TABLE, "amount_effective", transaction.getId())));
  }

  private String decryptDescription(UUID userId, Transaction transaction) {
    try {
      return cryptoService.decryptString(
          transaction.getDescriptionEffectiveEnc(),
          new CryptoContext(userId, TABLE, "description_effective", transaction.getId()));
    } catch (RuntimeException exception) {
      return "";
    }
  }

  private static boolean looksLikeCardPayment(String description) {
    String normalized =
        Normalizer.normalize(description == null ? "" : description, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return normalized.contains("pagamento")
        || normalized.contains("payment")
        || normalized.contains("boleto");
  }
}
