package com.deltafinance.unit.categories;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.categories.application.CategoryTaxonomy;
import org.junit.jupiter.api.Test;

class CategoryTaxonomyTest {

  @Test
  void mapeiaNomesConhecidosDoProvedorParaCategoriasCanonicas() {
    assertThat(CategoryTaxonomy.canonicalKey("Groceries")).isEqualTo("alimentacao");
    assertThat(CategoryTaxonomy.canonicalKey("Food delivery")).isEqualTo("alimentacao");
    assertThat(CategoryTaxonomy.canonicalKey("Taxi and ride-hailing")).isEqualTo("transporte");
    assertThat(CategoryTaxonomy.canonicalKey("Electricity")).isEqualTo("moradia");
    assertThat(CategoryTaxonomy.canonicalKey("Healthcare")).isEqualTo("saude");
    assertThat(CategoryTaxonomy.canonicalKey("Shopping")).isEqualTo("compras");
    assertThat(CategoryTaxonomy.canonicalKey("Services")).isEqualTo("servicos");
    assertThat(CategoryTaxonomy.canonicalKey("Salary")).isEqualTo("salario");
    assertThat(CategoryTaxonomy.canonicalKey("Proceeds interests and dividends"))
        .isEqualTo("rendimentos");
  }

  @Test
  void trataNomesDesconhecidosComoOutros() {
    assertThat(CategoryTaxonomy.canonicalKey("Mercado do bairro"))
        .isEqualTo(CategoryTaxonomy.OUTROS);
    assertThat(CategoryTaxonomy.canonicalKey("Digital services"))
        .isEqualTo(CategoryTaxonomy.OUTROS);
    assertThat(CategoryTaxonomy.canonicalKey("Non-recurring income"))
        .isEqualTo(CategoryTaxonomy.OUTROS);
    assertThat(CategoryTaxonomy.canonicalKey("  Gastos   diversos "))
        .isEqualTo(CategoryTaxonomy.OUTROS);
  }

  @Test
  void reconheceTransferenciasInternasSemelhantesAoProvedor() {
    for (String name :
        new String[] {
          "Same person transfer",
          "Same person transfer - PIX",
          "Transfer - Internal",
          "Credit card payment",
          "Pagamento de fatura"
        }) {
      assertThat(CategoryTaxonomy.canonicalKey(name)).isEqualTo(CategoryTaxonomy.TRANSFERENCIAS);
    }
    assertThat(CategoryTaxonomy.isTransferKey(CategoryTaxonomy.TRANSFERENCIAS)).isTrue();
    assertThat(CategoryTaxonomy.isExternalTransferKey(CategoryTaxonomy.TRANSFERENCIAS)).isFalse();
  }

  @Test
  void reconheceTransferenciasExternasSemelhantesAoProvedor() {
    for (String name :
        new String[] {
          "Transfers",
          "Transfer - PIX",
          "Transfer - Bank Slip",
          "Transfer - TED",
          "Transfer - DOC",
          "Third-party transfers"
        }) {
      assertThat(CategoryTaxonomy.canonicalKey(name))
          .isEqualTo(CategoryTaxonomy.TRANSFERENCIAS_EXTERNAS);
    }
    assertThat(CategoryTaxonomy.isTransferKey(CategoryTaxonomy.TRANSFERENCIAS_EXTERNAS)).isFalse();
    assertThat(CategoryTaxonomy.isExternalTransferKey(CategoryTaxonomy.TRANSFERENCIAS_EXTERNAS))
        .isTrue();
  }

  @Test
  void reconheceCategoriasDeInvestimentoParaSepararAporteEResgate() {
    for (String name :
        new String[] {
          "Investments",
          "Automatic investment",
          "Fixed income",
          "Mutual funds",
          "Variable income",
          "Margin",
          "Pension"
        }) {
      assertThat(CategoryTaxonomy.canonicalKey(name)).isEqualTo(CategoryTaxonomy.INVESTIMENTOS);
    }
    assertThat(CategoryTaxonomy.isInvestmentKey(CategoryTaxonomy.INVESTIMENTOS)).isTrue();
    assertThat(CategoryTaxonomy.isInvestmentKey(CategoryTaxonomy.OUTROS)).isFalse();
    assertThat(CategoryTaxonomy.canonicalKey("Proceeds interests and dividends"))
        .isEqualTo("rendimentos");
  }

  @Test
  void identificaCategoriasDeDespesaParaNaoUsaLasEmCreditos() {
    assertThat(CategoryTaxonomy.isExpenseKey("alimentacao")).isTrue();
    assertThat(CategoryTaxonomy.isExpenseKey("compras")).isTrue();
    assertThat(CategoryTaxonomy.isExpenseKey("salario")).isFalse();
    assertThat(CategoryTaxonomy.isExpenseKey("outros")).isFalse();
    assertThat(CategoryTaxonomy.isExpenseKey(null)).isFalse();
  }

  @Test
  void retornaNuloQuandoNaoHaCategoriaDoProvedor() {
    assertThat(CategoryTaxonomy.canonicalKey(null)).isNull();
    assertThat(CategoryTaxonomy.canonicalKey("  ")).isNull();
  }
}
