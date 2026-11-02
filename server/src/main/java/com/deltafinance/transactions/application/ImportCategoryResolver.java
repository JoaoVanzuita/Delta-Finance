package com.deltafinance.transactions.application;

import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.categories.application.CategoryTaxonomy;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolve a categoria canônica de uma transação importada.
 *
 * <p>O cache é por lote de importação: a taxonomia tem poucas chaves e resolver uma vez evita
 * consultas por linha.
 */
@Component
class ImportCategoryResolver {

  private final CategoryService categoryService;

  ImportCategoryResolver(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  UUID resolve(
      UUID userId,
      FinancialAccount account,
      TransactionType type,
      BigDecimal amount,
      String providerCategory,
      String description,
      Transaction existing,
      Map<String, UUID> categoryCache) {
    if (existing != null
        && existing.getCategoryId() != null
        && (providerCategory == null || providerCategory.isBlank())) {
      return existing.getCategoryId();
    }
    String canonicalKey =
        providerCategory == null || providerCategory.isBlank()
            ? CategoryTaxonomy.OUTROS
            : CategoryTaxonomy.canonicalKey(providerCategory);
    // Salário costuma chegar como "Same person transfer" porque a conta pagadora não está
    // conectada. A descrição explícita prevalece sobre esse rótulo genérico e garante que a
    // entrada componha receitas.
    if (type == TransactionType.CREDIT
        && account.getType() != AccountType.CREDIT
        && CategoryTaxonomy.isSalaryDescription(description)) {
      canonicalKey = CategoryTaxonomy.SALARIO;
    }
    // Débito bancário que paga a fatura do cartão é transferência, não despesa.
    if (type == TransactionType.DEBIT
        && account.getType() != AccountType.CREDIT
        && CategoryTaxonomy.isCardInvoicePaymentDescription(description)) {
      canonicalKey = CategoryTaxonomy.TRANSFERENCIAS;
    }
    // Aplicação e resgate são movimentos patrimoniais, não receita/despesa. O Pluggy usa a mesma
    // família de categorias ("Investments" e filhos) para os dois sentidos; a direção separa.
    // O débito de corretagem compra o ativo (Aplicações); o crédito vende/resgata (Resgates).
    if (CategoryTaxonomy.isInvestmentKey(canonicalKey)) {
      canonicalKey =
          type == TransactionType.CREDIT ? CategoryTaxonomy.RESGATES : CategoryTaxonomy.APLICACOES;
    }
    // Em conta bancária, um crédito numa categoria de despesa é uma entrada atípica (reembolso,
    // devolução ou categorização incorreta do provedor). Ele não pode aparecer como “despesa” no
    // gráfico de receitas; vai para Outros e o usuário pode recategorizar depois. No cartão, o
    // crédito é estorno e deve permanecer na categoria original para reduzir a despesa correta.
    if (type == TransactionType.CREDIT
        && account.getType() != AccountType.CREDIT
        && CategoryTaxonomy.isExpenseKey(canonicalKey)) {
      canonicalKey = CategoryTaxonomy.OUTROS;
    }
    return categoryCache.computeIfAbsent(
        canonicalKey, key -> categoryService.canonicalCategoryId(userId, key));
  }
}
