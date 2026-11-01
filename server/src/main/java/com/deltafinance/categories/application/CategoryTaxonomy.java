package com.deltafinance.categories.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Taxonomia de apresentação das categorias.
 *
 * <p>O Pluggy devolve rótulos livres. Sem um mapa canônico, cada rótulo vira uma categoria nova e o
 * dashboard acumula dezenas de categorias. Este mapa resolve os nomes conhecidos para as categorias
 * predefinidas; qualquer nome novo cai em {@code outros}, evitando fragmentação silenciosa.
 */
public final class CategoryTaxonomy {

  public static final String OUTROS = "outros";
  public static final String TRANSFERENCIAS = "transferencias";
  public static final String TRANSFERENCIAS_EXTERNAS = "transferencias_externas";
  public static final String INVESTIMENTOS = "investimentos";
  public static final String APORTES = "aportes";
  public static final String RESGATES = "resgates";

  private static final Set<String> EXPENSE_KEYS =
      Set.of(
          "moradia",
          "alimentacao",
          "transporte",
          "saude",
          "educacao",
          "lazer",
          "compras",
          "servicos");

  private static final Map<String, String> KEY_BY_NAME =
      Map.ofEntries(
          // Categorias canônicas já existentes.
          Map.entry("moradia", "moradia"),
          Map.entry("alimentacao", "alimentacao"),
          Map.entry("alimentação", "alimentacao"),
          Map.entry("transporte", "transporte"),
          Map.entry("saude", "saude"),
          Map.entry("saúde", "saude"),
          Map.entry("educacao", "educacao"),
          Map.entry("educação", "educacao"),
          Map.entry("lazer", "lazer"),
          Map.entry("compras", "compras"),
          Map.entry("servicos", "servicos"),
          Map.entry("serviços", "servicos"),
          Map.entry("salario", "salario"),
          Map.entry("salário", "salario"),
          Map.entry("rendimentos", "rendimentos"),
          Map.entry("rendimentos de investimentos", "rendimentos"),
          Map.entry("investimentos", INVESTIMENTOS),
          Map.entry("aportes", APORTES),
          Map.entry("resgates", RESGATES),
          Map.entry("transferencias", "transferencias"),
          Map.entry("transferências", "transferencias"),
          Map.entry("outros", OUTROS),

          // Rótulos conhecidos do provedor.
          Map.entry("taxi and ride-hailing", "transporte"),
          Map.entry("groceries", "alimentacao"),
          Map.entry("food delivery", "alimentacao"),
          Map.entry("eating out", "alimentacao"),
          Map.entry("electricity", "moradia"),
          Map.entry("water", "moradia"),
          Map.entry("healthcare", "saude"),
          Map.entry("hospital clinics and labs", "saude"),
          Map.entry("university", "educacao"),
          Map.entry("shopping", "compras"),
          Map.entry("clothing", "compras"),
          Map.entry("electronics", "compras"),
          Map.entry("houseware", "compras"),
          Map.entry("services", "servicos"),
          Map.entry("telecommunications", "servicos"),
          Map.entry("internet", "servicos"),
          Map.entry("salary", "salario"),

          // Transferências internas e pagamento de fatura nunca entram em receitas/despesas.
          Map.entry("same person transfer", TRANSFERENCIAS),
          Map.entry("same person transfer - cash", TRANSFERENCIAS),
          Map.entry("same person transfer - pix", TRANSFERENCIAS),
          Map.entry("same person transfer - ted", TRANSFERENCIAS),
          Map.entry("transfer - internal", TRANSFERENCIAS),
          Map.entry("internal transfer", TRANSFERENCIAS),
          Map.entry("credit card payment", TRANSFERENCIAS),
          Map.entry("pagamento de cartao de credito", TRANSFERENCIAS),
          Map.entry("pagamento de cartão de crédito", TRANSFERENCIAS),
          Map.entry("pagamento de fatura", TRANSFERENCIAS),
          Map.entry("transferencia - interna", TRANSFERENCIAS),
          Map.entry("transferência - interna", TRANSFERENCIAS),
          Map.entry("transferencia entre contas", TRANSFERENCIAS),
          Map.entry("transferência entre contas", TRANSFERENCIAS),
          Map.entry("transferencia mesma titularidade", TRANSFERENCIAS),
          Map.entry("transferência mesma titularidade", TRANSFERENCIAS),
          Map.entry("mesma titularidade", TRANSFERENCIAS),

          // Transferências genéricas/com terceiros: a direção decide se é receita ou despesa.
          // O Pluggy usa "Same person transfer" e "Transfer - Internal" apenas para o titular.
          Map.entry("transfers", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transfer - pix", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transferencia - pix", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transferência - pix", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transfer - ted", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transferencia - ted", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transferência - ted", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transfer - doc", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transferencia - doc", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transferência - doc", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transfer - bank slip", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transfer - boleto", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transferencia - boleto", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transferência - boleto", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transfer - cash", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transfer - check", TRANSFERENCIAS_EXTERNAS),
          Map.entry("transfer - foreign exchange", TRANSFERENCIAS_EXTERNAS),
          Map.entry("third-party transfers", TRANSFERENCIAS_EXTERNAS),
          Map.entry("third party transfers", TRANSFERENCIAS_EXTERNAS),

          // Nomes ambíguos ou sem destino canônico aprovado: Outros.
          Map.entry("digital services", OUTROS),
          Map.entry("non-recurring income", OUTROS),
          Map.entry("proceeds interests and dividends", "rendimentos"),
          Map.entry("interest", "rendimentos"),
          Map.entry("dividends", "rendimentos"),
          Map.entry("jcp", "rendimentos"),
          Map.entry("rent", "rendimentos"),
          Map.entry("coupon", "rendimentos"),
          Map.entry("investments", INVESTIMENTOS),
          Map.entry("automatic investment", INVESTIMENTOS),
          Map.entry("fixed income", INVESTIMENTOS),
          Map.entry("mutual funds", INVESTIMENTOS),
          Map.entry("variable income", INVESTIMENTOS),
          Map.entry("margin", INVESTIMENTOS),
          Map.entry("pension", INVESTIMENTOS),
          Map.entry("loans and financing", OUTROS),
          Map.entry("insurance", OUTROS),
          Map.entry("gyms and fitness centers", OUTROS),
          Map.entry("wellness and fitness", OUTROS),
          Map.entry("sports goods", OUTROS),
          Map.entry("bookstore", OUTROS),
          Map.entry("pet supplies and vet", OUTROS),
          Map.entry("tax on financial operations", OUTROS));

  private CategoryTaxonomy() {}

  /**
   * Retorna a chave canônica ou {@code null} quando o provedor não informou categoria. Nomes
   * desconhecidos retornam {@link #OUTROS}.
   */
  public static String canonicalKey(String providerCategory) {
    if (providerCategory == null || providerCategory.isBlank()) {
      return null;
    }
    return KEY_BY_NAME.getOrDefault(normalize(providerCategory), OUTROS);
  }

  /** Usado no backfill para não apagar categorias manuais desconhecidas. */
  public static boolean isKnownProviderName(String name) {
    return name != null && KEY_BY_NAME.containsKey(normalize(name));
  }

  public static boolean isTransferKey(String key) {
    return TRANSFERENCIAS.equals(key);
  }

  public static boolean isExternalTransferKey(String key) {
    return TRANSFERENCIAS_EXTERNAS.equals(key);
  }

  public static boolean isInvestmentKey(String key) {
    return INVESTIMENTOS.equals(key);
  }

  public static boolean isExpenseKey(String key) {
    return key != null && EXPENSE_KEYS.contains(key);
  }

  public static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .strip()
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }
}
