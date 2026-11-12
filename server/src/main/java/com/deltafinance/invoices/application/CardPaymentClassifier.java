package com.deltafinance.invoices.application;

import com.deltafinance.categories.domain.Category;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.transactions.application.CardPaymentDescription;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionType;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reconhece pagamento de fatura no extrato importado. */
@Component
class CardPaymentClassifier {

  private final CategoryRepository categoryRepository;
  private final EncryptedValueReader encryptedValueReader;

  CardPaymentClassifier(
      CategoryRepository categoryRepository, EncryptedValueReader encryptedValueReader) {
    this.categoryRepository = categoryRepository;
    this.encryptedValueReader = encryptedValueReader;
  }

  boolean isCardPayment(Transaction transaction, Set<UUID> transferCategoryIds) {
    // O metadado estruturado do provedor tem precedência sobre qualquer heurística de texto.
    if (isProviderCardPayment(transaction)) {
      return true;
    }
    if (transaction.getType() != TransactionType.REFUND) {
      return false;
    }
    if (transaction.getCategoryId() != null
        && transferCategoryIds.contains(transaction.getCategoryId())) {
      return true;
    }
    return isPaymentDescription(transaction);
  }

  /** {@code PAGAMENTO_FATURA} é o sinal canônico do provedor para pagamento de cartão. */
  private static boolean isProviderCardPayment(Transaction transaction) {
    String operationType = transaction.getOperationType();
    if (operationType == null || operationType.isBlank()) {
      return false;
    }
    String normalized = operationType.trim().toUpperCase(Locale.ROOT);
    return normalized.equals("PAGAMENTO_FATURA")
        || normalized.equals("CREDIT_CARD_PAYMENT")
        || normalized.contains("PAGAMENTO_FATURA");
  }

  boolean isPaymentDescription(Transaction transaction) {
    try {
      String description =
          encryptedValueReader.read(
              transaction.getUserId(),
              "transaction",
              "description_effective",
              transaction.getId(),
              transaction.getDescriptionEffectiveEnc());
      return CardPaymentDescription.looksLikePayment(description);
    } catch (RuntimeException exception) {
      return false;
    }
  }

  Set<UUID> transferCategoryIds(UUID userId) {
    Set<UUID> ids = new HashSet<>();
    for (Category category : categoryRepository.findByUserIdOrderByNameEncAsc(userId)) {
      if (category.getKind() == CategoryKind.TRANSFER) {
        ids.add(category.getId());
      }
    }
    return ids;
  }
}
