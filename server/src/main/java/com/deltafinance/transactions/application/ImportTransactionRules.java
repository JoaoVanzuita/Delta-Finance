package com.deltafinance.transactions.application;

import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/** Regras puras de tradução do payload do provedor para o modelo interno. */
final class ImportTransactionRules {

  private ImportTransactionRules() {}

  /**
   * Converte o valor da origem para o sinal interno.
   *
   * <p>Para contas {@code CREDIT}, o Pluggy retorna compras como positivas (aumentam a dívida) e
   * pagamentos/créditos como negativos. No restante do sistema a saída é negativa, então o valor é
   * invertido. Contas {@code BANK}, {@code CASH} e {@code WALLET} já seguem a convenção interna.
   */
  static BigDecimal normalizeAmount(FinancialAccount account, BigDecimal amount) {
    return account.getType() == AccountType.CREDIT ? amount.negate() : amount;
  }

  /** Moeda de origem só é preservada quando difere da moeda da conta. */
  static String sourceCurrency(FinancialAccount account, PluggyModels.PluggyTransaction imported) {
    String source = imported.currency();
    if (source == null || source.isBlank() || source.equalsIgnoreCase(account.getCurrency())) {
      return null;
    }
    return source;
  }

  /**
   * Moeda do valor efetivo.
   *
   * <p>Com {@code amountInAccountCurrency}, o valor já está na moeda da conta. Sem conversão
   * disponível, mantém a moeda original para que o relatório continue excluindo o lançamento em vez
   * de tratá-lo como BRL.
   */
  static String effectiveCurrency(
      FinancialAccount account, PluggyModels.PluggyTransaction imported) {
    if (imported.amountInAccountCurrency() != null) {
      return account.getCurrency();
    }
    return imported.currency() == null || imported.currency().isBlank()
        ? account.getCurrency()
        : imported.currency();
  }

  static TransactionStatus mapStatus(String status) {
    return status != null && "PENDING".equalsIgnoreCase(status)
        ? TransactionStatus.PENDING
        : TransactionStatus.POSTED;
  }

  static TransactionType mapType(FinancialAccount account, BigDecimal amount, String description) {
    if (amount.signum() > 0) {
      String normalized = normalize(description);
      if (normalized.contains("chargeback")) {
        return TransactionType.CHARGEBACK;
      }
      if (normalized.contains("estorno")
          || normalized.contains("reembolso")
          || normalized.contains("refund")) {
        return TransactionType.REFUND;
      }
      // Entrada em cartão é crédito/estorno que reduz a despesa, nunca receita.
      return account.getType() == AccountType.CREDIT
          ? TransactionType.REFUND
          : TransactionType.CREDIT;
    }
    return TransactionType.DEBIT;
  }

  static Integer installmentValue(JsonNode metadata, String field) {
    if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
      return null;
    }
    JsonNode value = metadata.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    int parsed = value.asInt(0);
    return parsed > 0 ? parsed : null;
  }

  static LocalDate billForecastMonth(JsonNode metadata) {
    if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
      return null;
    }
    String text = metadata.path("billForecastDate").asString(null);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return YearMonth.parse(text.trim()).atDay(1);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  static String installmentMetadata(JsonNode metadata) {
    if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
      return "";
    }
    return metadata.path("totalInstallments").asString("")
        + "/"
        + metadata.path("installmentNumber").asString("");
  }

  static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .strip()
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }

  static boolean looksLikeCardPayment(String description) {
    String normalized =
        Normalizer.normalize(description == null ? "" : description, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return normalized.contains("pagamento")
        || normalized.contains("payment")
        || normalized.contains("boleto");
  }
}
