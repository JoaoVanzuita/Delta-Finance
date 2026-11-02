package com.deltafinance.unit.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Metadados parciais do provedor não podem apagar dados já conhecidos. */
class TransactionMetadataTest {

  private static Transaction transaction() {
    return new Transaction(
        UUID.randomUUID(),
        UUID.randomUUID(),
        TransactionSource.IMPORTED,
        TransactionType.DEBIT,
        TransactionStatus.POSTED,
        LocalDate.of(2026, 10, 3),
        LocalDate.of(2026, 10, 3),
        "BRL",
        new byte[] {1},
        new byte[] {2},
        new byte[] {3},
        new byte[] {4});
  }

  @Test
  void mesDaFaturaAusentePreservaAlocacaoExistente() {
    Transaction transaction = transaction();
    transaction.applyProviderBillMonth(LocalDate.of(2026, 10, 15));

    transaction.applyProviderBillMonth(null);

    assertThat(transaction.getProviderBillMonth()).isEqualTo(LocalDate.of(2026, 10, 1));
  }

  @Test
  void parcelamentoAusentePreservaMetadadosExistentes() {
    Transaction transaction = transaction();
    transaction.applyInstallments(1, 3);

    transaction.applyInstallments(null, null);

    assertThat(transaction.getInstallmentNumber()).isEqualTo(1);
    assertThat(transaction.getTotalInstallments()).isEqualTo(3);
  }

  @Test
  void parcelamentoParcialNaoApagaEstadoExistente() {
    Transaction transaction = transaction();
    transaction.applyInstallments(1, 3);

    transaction.applyInstallments(2, null);

    assertThat(transaction.getInstallmentNumber()).isEqualTo(1);
    assertThat(transaction.getTotalInstallments()).isEqualTo(3);
  }

  @Test
  void estadoInicialPermaneceNuloQuandoProvedorOmiteMetadados() {
    Transaction transaction = transaction();

    transaction.applyProviderBillMonth(null);
    transaction.applyInstallments(null, null);

    assertThat(transaction.getProviderBillMonth()).isNull();
    assertThat(transaction.getInstallmentNumber()).isNull();
    assertThat(transaction.getTotalInstallments()).isNull();
    assertThat(transaction.getReconciliationState()).isEqualTo(ReconciliationState.NONE);
  }
}
