package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoicePayment;
import com.deltafinance.invoices.domain.InvoicePaymentRepository;
import com.deltafinance.invoices.domain.InvoicePaymentSource;
import com.deltafinance.invoices.domain.InvoicePaymentStatus;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionStatus;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reconciliação de pagamentos de fatura importados, sem gerar espelhos comuns. */
@Component
class CardPaymentReconciler {

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final InvoicePaymentRepository paymentRepository;
  private final TransactionRepository transactionRepository;
  private final InvoiceCycleLookup invoiceLookup;
  private final CardPaymentClassifier paymentClassifier;
  private final CardCycleService cardCycleService;
  private final InvoiceBalanceService balanceService;
  private final CryptoService cryptoService;
  private final EncryptedValueReader encryptedValueReader;
  private final IndexHmacService hmacService;
  private final Clock clock;

  CardPaymentReconciler(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      InvoicePaymentRepository paymentRepository,
      TransactionRepository transactionRepository,
      InvoiceCycleLookup invoiceLookup,
      CardPaymentClassifier paymentClassifier,
      CardCycleService cardCycleService,
      InvoiceBalanceService balanceService,
      CryptoService cryptoService,
      EncryptedValueReader encryptedValueReader,
      IndexHmacService hmacService,
      Clock clock) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.paymentRepository = paymentRepository;
    this.transactionRepository = transactionRepository;
    this.invoiceLookup = invoiceLookup;
    this.paymentClassifier = paymentClassifier;
    this.cardCycleService = cardCycleService;
    this.balanceService = balanceService;
    this.cryptoService = cryptoService;
    this.encryptedValueReader = encryptedValueReader;
    this.hmacService = hmacService;
    this.clock = clock;
  }

  boolean ensureCardPayment(
      UUID userId,
      UUID cardAccountId,
      Transaction transaction,
      CardProfile profile,
      Set<UUID> transferCategoryIds) {
    boolean changed = false;
    for (InvoiceItem legacy :
        itemRepository.findByUserIdAndTransactionId(userId, transaction.getId())) {
      if (legacy.isActive()) {
        legacy.remove(clock.now());
        itemRepository.save(legacy);
        changed = true;
      }
    }

    byte[] dedupKey = paymentDedupKey(userId, transaction);
    InvoicePayment existing =
        paymentRepository
            .findByUserIdAndSourceTransactionId(userId, transaction.getId())
            .orElse(null);
    if (existing != null) {
      if (existing.getDedupKey() == null) {
        existing.applyDedupKey(dedupKey);
        paymentRepository.save(existing);
        changed = true;
      }
      if (!existing.getInvoiceId().equals(transaction.getInvoiceId())) {
        transaction.setInvoiceId(existing.getInvoiceId());
        transactionRepository.save(transaction);
        changed = true;
      }
      return changed;
    }

    InvoicePayment duplicate = findDuplicatePayment(userId, cardAccountId, dedupKey, transaction);
    if (duplicate != null) {
      changed |= adoptDuplicate(userId, transaction, duplicate);
      return changed;
    }

    Invoice invoice =
        invoiceLookup.invoiceForTransaction(
            userId, cardAccountId, transaction, profile, transferCategoryIds);
    if (invoice == null) {
      return changed;
    }
    createImportedPayment(userId, invoice, transaction, dedupKey);
    transaction.setInvoiceId(invoice.getId());
    transactionRepository.save(transaction);
    return true;
  }

  void backfillPaymentDedupKeys(UUID userId, UUID cardAccountId) {
    Set<String> assigned = new HashSet<>();
    for (Invoice invoice :
        invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(
            userId, cardAccountId)) {
      for (InvoicePayment payment :
          paymentRepository.findByUserIdAndInvoiceIdOrderByPaidOnAscCreatedAtAsc(
              userId, invoice.getId())) {
        if (payment.getDedupKey() != null || payment.getSourceTransactionId() == null) {
          continue;
        }
        Transaction source =
            transactionRepository
                .findByIdAndUserId(payment.getSourceTransactionId(), userId)
                .orElse(null);
        if (source == null) {
          continue;
        }
        byte[] key = paymentDedupKey(userId, source);
        String hex = HexFormat.of().formatHex(key);
        if (!assigned.add(hex)
            || paymentRepository.findByUserIdAndDedupKey(userId, key).isPresent()) {
          continue;
        }
        payment.applyDedupKey(key);
        paymentRepository.save(payment);
      }
    }
  }

  public byte[] paymentDedupKey(UUID userId, Transaction transaction) {
    BigDecimal amount = decryptAmount(userId, transaction);
    String description = decryptDescription(userId, transaction);
    String normalized =
        Normalizer.normalize(description, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("\\s+", " ");
    String value =
        transaction.getAccountId()
            + "|"
            + amount.toPlainString()
            + "|"
            + transaction.getPostedOn()
            + "|"
            + normalized;
    return hmacService.externalIdHash("invoice-payment-dedup", value);
  }

  private InvoicePayment findDuplicatePayment(
      UUID userId, UUID cardAccountId, byte[] dedupKey, Transaction transaction) {
    Optional<InvoicePayment> direct = paymentRepository.findByUserIdAndDedupKey(userId, dedupKey);
    if (direct.isPresent()) {
      return direct.get();
    }
    Set<String> assigned = new HashSet<>();
    BigDecimal amount = decryptAmount(userId, transaction);
    boolean paymentDescription = paymentClassifier.isPaymentDescription(transaction);
    for (Invoice invoice :
        invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(
            userId, cardAccountId)) {
      for (InvoicePayment candidate :
          paymentRepository.findByUserIdAndInvoiceIdOrderByPaidOnAscCreatedAtAsc(
              userId, invoice.getId())) {
        Transaction source = null;
        if (candidate.getSourceTransactionId() != null) {
          source =
              transactionRepository
                  .findByIdAndUserId(candidate.getSourceTransactionId(), userId)
                  .orElse(null);
        }
        if (candidate.getDedupKey() == null && source != null) {
          byte[] key = paymentDedupKey(userId, source);
          String hex = HexFormat.of().formatHex(key);
          if (!assigned.add(hex)) {
            continue;
          }
          if (Arrays.equals(key, dedupKey)) {
            candidate.applyDedupKey(key);
            paymentRepository.save(candidate);
            return candidate;
          }
        }
        if (paymentDescription
            && candidate.getStatus() == InvoicePaymentStatus.CONFIRMED
            && candidate.getPaidOn().equals(transaction.getPostedOn())
            && (source != null && paymentClassifier.isPaymentDescription(source))
            && decryptPayment(candidate).compareTo(amount) == 0) {
          return candidate;
        }
      }
    }
    return null;
  }

  private boolean adoptDuplicate(UUID userId, Transaction transaction, InvoicePayment duplicate) {
    boolean changed = false;
    if (duplicate.getStatus() == InvoicePaymentStatus.REVERSED) {
      duplicate.confirm();
      duplicate.repointSource(transaction.getId());
      paymentRepository.save(duplicate);
      changed = true;
    }
    if (!duplicate.getInvoiceId().equals(transaction.getInvoiceId())) {
      transaction.setInvoiceId(duplicate.getInvoiceId());
      transactionRepository.save(transaction);
      changed = true;
    }
    return changed;
  }

  public boolean synchronizePaymentData(
      UUID userId, Transaction transaction, InvoicePayment payment) {
    BigDecimal amount = decryptAmount(userId, transaction);
    byte[] dedupKey = paymentDedupKey(userId, transaction);
    InvoicePayment collision =
        paymentRepository.findByUserIdAndDedupKey(userId, dedupKey).orElse(null);
    if (collision != null && !collision.getId().equals(payment.getId())) {
      // Mantém a chave atual para não violar a unicidade; o valor/data ainda são corrigidos.
      dedupKey = payment.getDedupKey();
    }
    BigDecimal currentAmount =
        encryptedValueReader.readMoney(
            userId, "invoice_payment", "amount", payment.getId(), payment.getAmountEnc());
    boolean changed =
        currentAmount.compareTo(amount) != 0
            || !payment.getPaidOn().equals(transaction.getPostedOn())
            || !Arrays.equals(payment.getDedupKey(), dedupKey);

    CardProfile profile =
        cardCycleService.profileFor(userId, transaction.getAccountId()).orElse(null);
    if (profile != null) {
      Invoice target =
          invoiceLookup.invoiceForTransaction(
              userId,
              transaction.getAccountId(),
              transaction,
              profile,
              paymentClassifier.transferCategoryIds(userId));
      if (target != null && !target.getId().equals(payment.getInvoiceId())) {
        Invoice current =
            invoiceRepository.findByIdAndUserId(payment.getInvoiceId(), userId).orElse(null);
        if (current != null && current.getStatus() != InvoiceStatus.PAID) {
          payment.reassignInvoice(target.getId());
          transaction.setInvoiceId(target.getId());
          transactionRepository.save(transaction);
          changed = true;
        }
      }
    }

    if (!changed) {
      return false;
    }
    payment.applySourceData(
        cryptoService.encrypt(
            amount.toPlainString(),
            new CryptoContext(userId, "invoice_payment", "amount", payment.getId())),
        transaction.getPostedOn(),
        dedupKey);
    paymentRepository.save(payment);
    balanceService.recalculateCard(userId, transaction.getAccountId());
    return true;
  }

  public Optional<UUID> paymentSourceReplacement(UUID userId, Transaction removed) {
    byte[] key = paymentDedupKey(userId, removed);
    for (Transaction candidate :
        transactionRepository.findByUserIdAndAccountIdAndPostedOn(
            userId, removed.getAccountId(), removed.getPostedOn())) {
      if (candidate.getId().equals(removed.getId()) || !isEffective(candidate)) {
        continue;
      }
      if (Arrays.equals(paymentDedupKey(userId, candidate), key)) {
        return Optional.of(candidate.getId());
      }
    }
    return Optional.empty();
  }

  public boolean reassignPaymentSource(UUID userId, Transaction removed, InvoicePayment payment) {
    if (payment.getSourceTransactionId() == null
        || !payment.getSourceTransactionId().equals(removed.getId())) {
      return false;
    }
    Optional<UUID> replacement = paymentSourceReplacement(userId, removed);
    if (replacement.isEmpty()) {
      return false;
    }
    Transaction candidate =
        transactionRepository.findByIdAndUserId(replacement.get(), userId).orElse(null);
    if (candidate == null) {
      return false;
    }
    payment.repointSource(candidate.getId());
    paymentRepository.save(payment);
    candidate.setInvoiceId(payment.getInvoiceId());
    transactionRepository.save(candidate);
    balanceService.recalculateCard(userId, removed.getAccountId());
    return true;
  }

  private void createImportedPayment(
      UUID userId, Invoice invoice, Transaction transaction, byte[] dedupKey) {
    BigDecimal amount = decryptAmount(userId, transaction);
    UUID paymentId = UUID.randomUUID();
    InvoicePayment payment =
        new InvoicePayment(
            paymentId,
            userId,
            invoice.getId(),
            transaction.getAccountId(),
            cryptoService.encrypt(
                amount.toPlainString(),
                new CryptoContext(userId, "invoice_payment", "amount", paymentId)),
            transaction.getPostedOn(),
            InvoicePaymentStatus.CONFIRMED,
            InvoicePaymentSource.IMPORTED,
            transaction.getId());
    payment.applyDedupKey(dedupKey);
    paymentRepository.save(payment);
  }

  private BigDecimal decryptAmount(UUID userId, Transaction transaction) {
    return encryptedValueReader
        .readMoney(
            userId,
            "transaction",
            "amount_effective",
            transaction.getId(),
            transaction.getAmountEffectiveEnc())
        .abs();
  }

  private BigDecimal decryptPayment(InvoicePayment payment) {
    return encryptedValueReader
        .readMoney(
            payment.getUserId(),
            "invoice_payment",
            "amount",
            payment.getId(),
            payment.getAmountEnc())
        .abs();
  }

  private String decryptDescription(UUID userId, Transaction transaction) {
    // A chave ainda diferencia cartão, valor e data; descrição ilegível não deve quebrar o fluxo.
    return encryptedValueReader
        .tryRead(
            userId,
            "transaction",
            "description_effective",
            transaction.getId(),
            transaction.getDescriptionEffectiveEnc())
        .orElse("");
  }

  private static boolean isEffective(Transaction transaction) {
    return (transaction.getStatus() == TransactionStatus.POSTED
            || transaction.getStatus() == TransactionStatus.RECONCILED)
        && transaction.getReconciliationState()
            != com.deltafinance.transactions.domain.ReconciliationState.SOURCE_REMOVED;
  }
}
