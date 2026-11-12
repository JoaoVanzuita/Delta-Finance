package com.deltafinance.invoices.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardPaymentClassifierTest {

  @Mock private CategoryRepository categoryRepository;
  @Mock private EncryptedValueReader encryptedValueReader;

  @Test
  void operationTypeDoProvedorTemPrecedenciaSobreADescricao() {
    Transaction transaction = transaction(TransactionType.DEBIT);
    transaction.applyProviderMetadata("PAGAMENTO_FATURA", null, null, null);

    assertThat(classifier().isCardPayment(transaction, Set.of())).isTrue();
  }

  @Test
  void estornoDeComercianteSemSinalDePagamentoNaoEhPagamentoDeFatura() {
    when(encryptedValueReader.read(any(), any(), any(), any(), any()))
        .thenReturn("Estorno Mercado");

    Transaction transaction = transaction(TransactionType.REFUND);

    assertThat(classifier().isCardPayment(transaction, Set.of())).isFalse();
  }

  @Test
  void boletoUsaAMesmaHeuristicaEmTodosOsFluxos() {
    when(encryptedValueReader.read(any(), any(), any(), any(), any()))
        .thenReturn("PAGAMENTO-BOLETO BANCARIO");

    Transaction transaction = transaction(TransactionType.REFUND);

    assertThat(classifier().isCardPayment(transaction, Set.of())).isTrue();
  }

  private CardPaymentClassifier classifier() {
    return new CardPaymentClassifier(categoryRepository, encryptedValueReader);
  }

  private static Transaction transaction(TransactionType type) {
    UUID userId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    return new Transaction(
        userId,
        accountId,
        TransactionSource.IMPORTED,
        type,
        TransactionStatus.POSTED,
        LocalDate.of(2026, 10, 20),
        LocalDate.of(2026, 10, 20),
        "BRL",
        new byte[0],
        new byte[0],
        new byte[0],
        new byte[0]);
  }
}
