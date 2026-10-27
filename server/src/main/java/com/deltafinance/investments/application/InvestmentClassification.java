package com.deltafinance.investments.application;

import com.deltafinance.investments.domain.ProviderInvestment;

/** Classificação de exibição de uma posição importada. */
final class InvestmentClassification {

  private InvestmentClassification() {}

  record Result(String assetClass, String source) {}

  static Result classify(ProviderInvestment investment, String code) {
    String userClass = investment.getUserAssetClass();
    if (userClass != null && !userClass.isBlank()) {
      return new Result(userClass.trim().toUpperCase(java.util.Locale.ROOT), "USER");
    }
    String type = text(investment.getType());
    String subtype = text(investment.getSubtype());
    String normalizedCode = code == null ? null : code.trim();
    if ("EQUITY".equals(type)) {
      if ("REAL_ESTATE_FUND".equals(subtype)) {
        return new Result("FII", "PROVIDER");
      }
      if (normalizedCode != null && normalizedCode.matches("(?i)[A-Z]{4}11")) {
        return new Result("FII", "CODE_HEURISTIC");
      }
      if ("BDR".equals(subtype)) {
        return new Result("BDR", "PROVIDER");
      }
      if ("OPTION".equals(subtype) || "DERIVATIVE".equals(subtype)) {
        return new Result("DERIVATIVO", "PROVIDER");
      }
      return new Result("ACAO", "PROVIDER");
    }
    return switch (type) {
      case "FIXED_INCOME" -> new Result("RENDA_FIXA", "PROVIDER");
      case "MUTUAL_FUND" -> new Result("FUNDO", "PROVIDER");
      case "ETF" -> new Result("ETF", "PROVIDER");
      case "SECURITY" -> new Result("TITULO", "PROVIDER");
      case "COE" -> new Result("COE", "PROVIDER");
      default -> new Result("OUTRO", "PROVIDER");
    };
  }

  private static String text(String value) {
    return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
