package com.deltafinance.transactions.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Heurística única de descrição de pagamento de fatura.
 *
 * <p>Centraliza a contingência usada quando o provedor não informa um {@code operationType}
 * conclusivo. A classificação principal continua sendo o metadado do provedor; a descrição só deve
 * desempatar casos sem sinal estruturado. Manter uma única regra evita que importação,
 * classificação de fatura e limpeza de duplicatas discordem sobre o mesmo lançamento.
 */
public final class CardPaymentDescription {

  private static final Set<String> PAYMENT_TERMS = Set.of("pagamento", "payment", "boleto");

  private CardPaymentDescription() {}

  public static boolean looksLikePayment(String description) {
    String normalized =
        Normalizer.normalize(description == null ? "" : description, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return PAYMENT_TERMS.stream().anyMatch(normalized::contains);
  }
}
