package com.deltafinance.reporting.application;

import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.reporting.application.ReportEntry.Direction;
import java.math.BigDecimal;
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
    // Nenhuma categoria pode exibir despesa negativa: a redução é limitada ao que entrou nela.
    for (CategoryAggregate aggregate : aggregates.values()) {
      aggregate.amount = aggregate.amount.max(BigDecimal.ZERO);
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
