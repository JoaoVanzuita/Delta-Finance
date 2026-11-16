package com.deltafinance.transactions.application;

import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Cadeia de âncoras para reconhecer uma transação já importada quando o identificador externo
 * mudou.
 *
 * <p>O provedor pode remover e recriar uma transação (novo id externo) quando data, descrição ou
 * valor mudam além do tolerado. Sem uma âncora alternativa, a recriação nasce como registro novo: a
 * linha antiga só sai dos totais na marcação de ausência, correções do usuário se perdem e vínculos
 * de reembolso/movimento interno ficam órfãos. A cadeia, em ordem de confiança:
 *
 * <ol>
 *   <li>{@code dedupKey} (HMAC do id externo) — resolvido antes, no importador;
 *   <li>{@code providerId} — identificador estável do Open Finance;
 *   <li>{@code providerCode} — código da instituição de origem;
 *   <li>{@code fingerprint} (conta, valor, data, descrição, parcelamento) — apenas quando o id
 *       externo do candidato não está mais na leitura atual, para não fundir duas compras legítimas
 *       idênticas.
 * </ol>
 *
 * <p>Toda âncora exige candidato único dentro da conta: ambíguo ou já reivindicado neste lote ⇒ não
 * reusa e cria um novo registro.
 */
@Component
public class TransactionIdentityResolver {

  private final TransactionRepository transactionRepository;

  public TransactionIdentityResolver(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  /**
   * Âncoras determinísticas do provedor, dentro da mesma conta.
   *
   * @param claimedIds transações já reivindicadas por outra linha deste lote; evita que duas linhas
   *     da leitura atual colapsem no mesmo registro local.
   */
  public Optional<Transaction> resolveByProviderAnchor(
      UUID userId, UUID accountId, String providerId, String providerCode, Set<UUID> claimedIds) {
    if (providerId != null && !providerId.isBlank()) {
      Optional<Transaction> match =
          uniqueUnclaimed(
              transactionRepository.findByUserIdAndAccountIdAndProviderId(
                  userId, accountId, providerId),
              claimedIds);
      if (match.isPresent()) {
        return match;
      }
    }
    if (providerCode != null && !providerCode.isBlank()) {
      return uniqueUnclaimed(
          transactionRepository.findByUserIdAndAccountIdAndProviderCode(
              userId, accountId, providerCode),
          claimedIds);
    }
    return Optional.empty();
  }

  /**
   * Âncora por fingerprint.
   *
   * @param requireAbsentFromCurrentRead quando verdadeiro (fluxo normal, mesmo itemId), o candidato
   *     só é adotado se o id externo dele <em>não</em> estiver na leitura atual — a origem deixou
   *     de reportá-lo, sinal de recriação. Duas compras legítimas idênticas na mesma leitura seguem
   *     sendo duas linhas. Na troca de itemId, a leitura nova nunca contém os ids antigos e a
   *     verificação é dispensada (comportamento atual da reconciliação de identidade).
   * @param seenInCurrentRead informa se um hash de id externo está presente na leitura atual.
   */
  public Optional<Transaction> resolveByFingerprint(
      UUID userId,
      UUID accountId,
      byte[] fingerprint,
      boolean requireAbsentFromCurrentRead,
      Predicate<byte[]> seenInCurrentRead,
      Set<UUID> claimedIds) {
    if (fingerprint == null) {
      return Optional.empty();
    }
    List<Transaction> candidates =
        transactionRepository.findByUserIdAndAccountIdAndFingerprint(
            userId, accountId, fingerprint);
    if (requireAbsentFromCurrentRead) {
      candidates =
          candidates.stream()
              .filter(
                  candidate ->
                      candidate.getExternalTransactionIdHash() == null
                          || !seenInCurrentRead.test(candidate.getExternalTransactionIdHash()))
              .toList();
    }
    return uniqueUnclaimed(candidates, claimedIds);
  }

  /** Zero candidatos ou mais de um ⇒ ambíguo; não reusa para não fundir transações distintas. */
  private static Optional<Transaction> uniqueUnclaimed(
      List<Transaction> candidates, Set<UUID> claimedIds) {
    List<Transaction> usable =
        candidates.stream().filter(candidate -> !claimedIds.contains(candidate.getId())).toList();
    if (usable.size() == 1) {
      return Optional.of(usable.get(0));
    }
    return Optional.empty();
  }
}
