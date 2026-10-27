package com.deltafinance.transactions.application;

import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Vínculo determinístico entre reembolso/chargeback e despesa original.
 *
 * <p>Critérios: mesma conta, despesa dentro de 90 dias antes do reembolso, valor que ainda cobre o
 * reembolso e descrição compatível. Quando duas despesas são indistinguíveis pelo valor, não há
 * vínculo automático; o usuário escolhe a original no diálogo de correção.
 */
@Component
public class RefundMatcher {

  private static final int WINDOW_DAYS = 90;

  private static final Set<String> GENERIC_WORDS =
      Set.of(
          "estorno",
          "reembolso",
          "refund",
          "chargeback",
          "parcial",
          "credito",
          "debito",
          "cartao",
          "compra",
          "pagamento",
          "pix",
          "transferencia",
          "conta",
          "banco",
          "valor",
          "transacao");

  private final TransactionRepository transactionRepository;
  private final CryptoService cryptoService;

  public RefundMatcher(TransactionRepository transactionRepository, CryptoService cryptoService) {
    this.transactionRepository = transactionRepository;
    this.cryptoService = cryptoService;
  }

  public Optional<Transaction> findOriginal(
      UUID userId,
      UUID accountId,
      TransactionType type,
      BigDecimal amount,
      LocalDate postedOn,
      String refundDescription) {
    if (type != TransactionType.REFUND && type != TransactionType.CHARGEBACK) {
      return Optional.empty();
    }
    LocalDate limit = postedOn.minusDays(WINDOW_DAYS);
    BigDecimal magnitude = amount.abs();
    Transaction best = null;
    BigDecimal bestMagnitude = null;
    boolean ambiguous = false;

    for (Transaction candidate :
        transactionRepository.findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(
            userId, accountId)) {
      if (candidate.getType() != TransactionType.DEBIT
          || candidate.getPostedOn().isBefore(limit)
          || candidate.getPostedOn().isAfter(postedOn)) {
        continue;
      }
      BigDecimal candidateAmount = effectiveAmount(candidate);
      if (candidateAmount.signum() >= 0) {
        continue;
      }
      BigDecimal candidateMagnitude = candidateAmount.abs();
      if (candidateMagnitude.compareTo(magnitude) < 0) {
        continue;
      }
      String candidateDescription = decryptDescription(candidate);
      if (!descriptionCompatible(refundDescription, candidateDescription)) {
        continue;
      }
      int comparison = bestMagnitude == null ? -1 : candidateMagnitude.compareTo(bestMagnitude);
      if (comparison < 0) {
        best = candidate;
        bestMagnitude = candidateMagnitude;
        ambiguous = false;
      } else if (comparison == 0) {
        ambiguous = true;
      }
    }
    return ambiguous ? Optional.empty() : Optional.ofNullable(best);
  }

  /**
   * Reembolsos com descrição apenas genérica ("Reembolso parcial") caem no critério de valor. Se a
   * descrição tem um comerciante, exige ao menos um token em comum com a despesa.
   */
  static boolean descriptionCompatible(String refundDescription, String purchaseDescription) {
    Set<String> refundTokens = merchantTokens(refundDescription);
    if (refundTokens.isEmpty()) {
      return true;
    }
    Set<String> purchaseTokens = merchantTokens(purchaseDescription);
    if (purchaseTokens.isEmpty()) {
      return true;
    }
    return refundTokens.stream().anyMatch(purchaseTokens::contains);
  }

  private static Set<String> merchantTokens(String value) {
    String normalized =
        Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return Arrays.stream(normalized.split("[^a-z0-9]+"))
        .filter(token -> token.length() >= 3)
        .filter(token -> !GENERIC_WORDS.contains(token))
        .collect(Collectors.toSet());
  }

  private BigDecimal effectiveAmount(Transaction transaction) {
    return new BigDecimal(
        cryptoService.decryptString(
            transaction.getAmountEffectiveEnc(),
            new CryptoContext(
                transaction.getUserId(), "transaction", "amount_effective", transaction.getId())));
  }

  private String decryptDescription(Transaction transaction) {
    return cryptoService.decryptString(
        transaction.getDescriptionEffectiveEnc(),
        new CryptoContext(
            transaction.getUserId(), "transaction", "description_effective", transaction.getId()));
  }
}
