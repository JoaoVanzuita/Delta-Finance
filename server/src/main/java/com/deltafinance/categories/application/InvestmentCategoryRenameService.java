package com.deltafinance.categories.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renomeia a categoria legada de débitos de corretagem de "Aportes" para "Aplicações".
 *
 * <p>A mudança é de apresentação: o débito de corretagem compra o ativo (aplicação) e não é um
 * aporte de caixa. A categoria antiga é desativada e suas transações são movidas para a nova. Não
 * há nova tabela, ledger ou regra de cálculo.
 */
@Service
public class InvestmentCategoryRenameService {

  private final CategoryService categoryService;

  public InvestmentCategoryRenameService(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  public record Report(boolean legacyDeactivated) {}

  @Transactional
  public Report renameUser(UUID userId) {
    // Garante que "Aplicações" exista antes de mover as transações legadas.
    categoryService.list(userId);
    UUID target = categoryService.canonicalCategoryId(userId, CategoryTaxonomy.APLICACOES);
    UUID legacy = categoryService.predefinedCategoryId(userId, CategoryTaxonomy.APORTES);
    if (legacy == null || legacy.equals(target)) {
      return new Report(false);
    }
    CategoryView legacyView =
        categoryService.listExisting(userId).stream()
            .filter(category -> category.id().equals(legacy))
            .findFirst()
            .orElse(null);
    if (legacyView == null
        || !legacyView.active()
        || !"Aportes".equalsIgnoreCase(legacyView.name())) {
      // Já migrada, inativa ou renomeada pelo usuário: não sobrescreve a escolha local.
      return new Report(false);
    }
    categoryService.deactivatePredefined(userId, legacy, target);
    return new Report(true);
  }
}
