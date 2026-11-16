package com.deltafinance.unit.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.deltafinance.transactions.application.TransactionIdentityResolver;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Cadeia de âncoras de identidade: candidato único adota; ambíguo ou reivindicado não reusa. */
@ExtendWith(MockitoExtension.class)
class TransactionIdentityResolverTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();

  @Mock private TransactionRepository repository;

  @Test
  void providerIdUnicoAdotaRegistroExistente() {
    Transaction existing = transaction();
    when(repository.findByUserIdAndAccountIdAndProviderId(USER_ID, ACCOUNT_ID, "prov-1"))
        .thenReturn(List.of(existing));

    var match =
        new TransactionIdentityResolver(repository)
            .resolveByProviderAnchor(USER_ID, ACCOUNT_ID, "prov-1", null, Set.of());

    assertThat(match).contains(existing);
  }

  @Test
  void providerCodeAncoraQuandoProviderIdAusente() {
    Transaction existing = transaction();
    when(repository.findByUserIdAndAccountIdAndProviderCode(USER_ID, ACCOUNT_ID, "code-1"))
        .thenReturn(List.of(existing));

    var match =
        new TransactionIdentityResolver(repository)
            .resolveByProviderAnchor(USER_ID, ACCOUNT_ID, null, "code-1", Set.of());

    assertThat(match).contains(existing);
  }

  @Test
  void doisCandidatosComMesmaAncoraSaoAmbiguosENaoReusam() {
    when(repository.findByUserIdAndAccountIdAndProviderId(USER_ID, ACCOUNT_ID, "prov-1"))
        .thenReturn(List.of(transaction(), transaction()));

    var match =
        new TransactionIdentityResolver(repository)
            .resolveByProviderAnchor(USER_ID, ACCOUNT_ID, "prov-1", null, Set.of());

    assertThat(match).isEmpty();
  }

  @Test
  void transacaoReivindicadaNoMesmoLoteNaoEhReusada() {
    Transaction existing = transaction();
    when(repository.findByUserIdAndAccountIdAndProviderId(USER_ID, ACCOUNT_ID, "prov-1"))
        .thenReturn(List.of(existing));

    var match =
        new TransactionIdentityResolver(repository)
            .resolveByProviderAnchor(USER_ID, ACCOUNT_ID, "prov-1", null, Set.of(existing.getId()));

    assertThat(match).isEmpty();
  }

  @Test
  void fingerprintAdotaSomenteQuandoIdExternoAusenteDaLeituraAtual() {
    Transaction gone = transaction();
    byte[] externalHash = new byte[] {7};
    gone.updateIdentity(new byte[] {1}, externalHash, new byte[] {2});
    when(repository.findByUserIdAndAccountIdAndFingerprint(eq(USER_ID), eq(ACCOUNT_ID), any()))
        .thenReturn(List.of(gone));

    // Id externo do candidato ainda está na leitura: duas compras legítimas idênticas.
    var stillThere =
        new TransactionIdentityResolver(repository)
            .resolveByFingerprint(
                USER_ID,
                ACCOUNT_ID,
                new byte[] {3},
                true,
                hash -> Arrays.equals(hash, externalHash),
                Set.of());
    assertThat(stillThere).isEmpty();

    // Id externo do candidato sumiu da leitura: sinal de recriação.
    var recreated =
        new TransactionIdentityResolver(repository)
            .resolveByFingerprint(
                USER_ID, ACCOUNT_ID, new byte[] {3}, true, hash -> false, Set.of());
    assertThat(recreated).contains(gone);
  }

  @Test
  void fingerprintSemRequireAbsentMantemReconciliacaoDeTrocaDeItemId() {
    Transaction existing = transaction();
    existing.updateIdentity(new byte[] {1}, new byte[] {7}, new byte[] {2});
    when(repository.findByUserIdAndAccountIdAndFingerprint(eq(USER_ID), eq(ACCOUNT_ID), any()))
        .thenReturn(List.of(existing));

    var match =
        new TransactionIdentityResolver(repository)
            .resolveByFingerprint(
                USER_ID, ACCOUNT_ID, new byte[] {3}, false, hash -> true, Set.of());

    assertThat(match).contains(existing);
  }

  @Test
  void semAncorasDisponiveisNaoReusaNada() {
    var byAnchor =
        new TransactionIdentityResolver(repository)
            .resolveByProviderAnchor(USER_ID, ACCOUNT_ID, null, null, Set.of());
    var byFingerprint =
        new TransactionIdentityResolver(repository)
            .resolveByFingerprint(USER_ID, ACCOUNT_ID, null, true, hash -> false, Set.of());

    assertThat(byAnchor).isEmpty();
    assertThat(byFingerprint).isEmpty();
  }

  private static Transaction transaction() {
    return new Transaction(
        USER_ID,
        ACCOUNT_ID,
        com.deltafinance.transactions.domain.TransactionSource.IMPORTED,
        com.deltafinance.transactions.domain.TransactionType.DEBIT,
        com.deltafinance.transactions.domain.TransactionStatus.POSTED,
        java.time.LocalDate.of(2026, 10, 20),
        java.time.LocalDate.of(2026, 10, 20),
        "BRL",
        new byte[] {1},
        new byte[] {2},
        new byte[] {3},
        new byte[] {4});
  }
}
