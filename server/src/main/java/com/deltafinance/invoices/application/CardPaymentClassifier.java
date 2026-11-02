package com.deltafinance.invoices.application;

import com.deltafinance.categories.domain.Category;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionType;
import java.text.Normalizer;
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
    if (transaction.getType() != TransactionType.REFUND) {
      return false;
    }
    if (transaction.getCategoryId() != null
        && transferCategoryIds.contains(transaction.getCategoryId())) {
      return true;
    }
    return isPaymentDescription(transaction);
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
      String normalized =
          Normalizer.normalize(description, Normalizer.Form.NFD)
              .replaceAll("\\p{M}", "")
              .toLowerCase(Locale.ROOT);
      return normalized.contains("pagamento") || normalized.contains("payment");
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
