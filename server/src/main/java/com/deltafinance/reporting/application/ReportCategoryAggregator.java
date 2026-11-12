package com.deltafinance.reporting.application;

import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.reporting.application.ReportEntry.Direction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Agrega entradas por categoria e calcula os totais usados no resumo. */
@Component
class ReportCategoryAggregator {

  Map<UUID, CategoryAggregate> categoriesOf(
      List<ReportEntry> entries, boolean incomeOnly, ResolvedReportQuery resolved) {
    Map<UUID, CategoryAggregate> aggregates = new LinkedHashMap<>();
    for (ReportEntry entry : entries) {
      if (incomeOnly) {
        if (entry.direction() != Direction.INCOME) {
          continue;
        }
        UUID key =
            entry.categoryId() == null ? ReportQueryResolver.UNCATEGORIZED_ID : entry.categoryId();
        // Salvaguarda para dados antigos ou lançamentos manuais: uma receita não pode
        // aparecer sob uma categoria de despesa. O valor continua no total de receitas,
        // mas é agrupado em Outros.
        if (resolved.categoryKinds().get(key) == CategoryKind.EXPENSE) {
          key =
              resolved.outrosCategoryId() == null
                  ? ReportQueryResolver.UNCATEGORIZED_ID
                  : resolved.outrosCategoryId();
        }
        CategoryAggregate aggregate =
            aggregates.computeIfAbsent(key, ignored -> new CategoryAggregate());
        aggregate.amount = aggregate.amount.add(entry.amount().abs());
        aggregate.count++;
        continue;
      }
      if (entry.direction() == Direction.INCOME) {
        continue;
      }
      UUID key =
          entry.categoryId() == null ? ReportQueryResolver.UNCATEGORIZED_ID : entry.categoryId();
      CategoryAggregate aggregate =
          aggregates.computeIfAbsent(key, ignored -> new CategoryAggregate());
      if (entry.direction() == Direction.EXPENSE) {
        aggregate.amount = aggregate.amount.add(entry.amount().abs());
      } else {
        aggregate.amount = aggregate.amount.subtract(entry.amount().abs());
      }
      aggregate.count++;
    }
    // Nenhuma categoria pode exibir despesa negativa. Quando as reduções de uma categoria superam
    // suas despesas (por exemplo, um ajuste de fatura sem categoria), o excedente é transportado
    // para as demais categorias em ordem decrescente de valor. Assim a soma das categorias continua
    // igual ao resumo, à série temporal e ao drill-down, em vez de a redução desaparecer.
    BigDecimal excess = BigDecimal.ZERO;
    for (CategoryAggregate aggregate : aggregates.values()) {
      if (aggregate.amount.signum() < 0) {
        excess = excess.add(aggregate.amount.negate());
        aggregate.amount = BigDecimal.ZERO;
      }
    }
    if (excess.signum() > 0) {
      List<CategoryAggregate> positives =
          new ArrayList<>(
              aggregates.values().stream()
                  .filter(aggregate -> aggregate.amount.signum() > 0)
                  .sorted(
                      Comparator.comparing((CategoryAggregate aggregate) -> aggregate.amount)
                          .reversed())
                  .toList());
      for (CategoryAggregate aggregate : positives) {
        if (excess.signum() <= 0) {
          break;
        }
        BigDecimal applied = excess.min(aggregate.amount);
        aggregate.amount = aggregate.amount.subtract(applied);
        excess = excess.subtract(applied);
      }
    }
    return aggregates;
  }

  BigDecimal incomeOf(List<ReportEntry> entries) {
    BigDecimal income = BigDecimal.ZERO;
    for (ReportEntry entry : entries) {
      if (entry.direction() == Direction.INCOME) {
        income = income.add(entry.amount());
      }
    }
    return income.max(BigDecimal.ZERO);
  }

  BigDecimal expensesOf(Map<UUID, CategoryAggregate> categories) {
    BigDecimal expenses = BigDecimal.ZERO;
    for (CategoryAggregate aggregate : categories.values()) {
      expenses = expenses.add(aggregate.amount);
    }
    return expenses.max(BigDecimal.ZERO);
  }
}

final class CategoryAggregate {
  BigDecimal amount = BigDecimal.ZERO;
  int count;
}
