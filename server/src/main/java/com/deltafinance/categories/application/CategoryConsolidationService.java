package com.deltafinance.categories.application;

import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consolida categorias importadas antigas na taxonomia canônica.
 *
 * <p>As categorias conhecidas do provedor são reatribuídas para a categoria predefinida
 * correspondente e removidas. Categorias desconhecidas que possuam transações são preservadas como
 * categorias manuais; novos imports desconhecidos passam a usar “Outros”. O processo é idempotente
 * e auditado.
 */
@Service
public class CategoryConsolidationService {

  public record Report(
      boolean dryRun,
      int categoriesMerged,
      int transactionsMoved,
      Map<String, Integer> transactionsByTarget) {}

  private final CategoryService categoryService;
  private final CategoryRepository categoryRepository;
  private final JdbcTemplate jdbcTemplate;
  private final AuditService auditService;

  public CategoryConsolidationService(
      CategoryService categoryService,
      CategoryRepository categoryRepository,
      JdbcTemplate jdbcTemplate,
      AuditService auditService) {
    this.categoryService = categoryService;
    this.categoryRepository = categoryRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.auditService = auditService;
  }

  /**
   * Executa ou simula a consolidação para todos os usuários.
   *
   * <p>No modo de simulação nenhuma categoria/transação é alterada. No modo de aplicação, as
   * transações são reatribuídas e as categorias de origem são removidas somente depois de ficarem
   * vazias.
   */
  @Transactional
  public Report consolidateAllUsers(boolean dryRun) {
    List<UUID> userIds = jdbcTemplate.queryForList("select id from app_user", UUID.class);
    int categoriesMerged = 0;
    int transactionsMoved = 0;
    Map<String, Integer> byTarget = new LinkedHashMap<>();

    for (UUID userId : userIds) {
      if (dryRun) {
        for (CategoryView category : categoryService.listExisting(userId)) {
          String targetKey = targetKey(category);
          if (targetKey == null) {
            continue;
          }
          int count = transactionCount(userId, category.id());
          if (count == 0) {
            continue;
          }
          categoriesMerged++;
          transactionsMoved += count;
          byTarget.merge(targetKey, count, Integer::sum);
        }
        continue;
      }

      for (CategoryView category : categoryService.list(userId)) {
        String targetKey = targetKey(category);
        if (targetKey == null) {
          continue;
        }
        UUID targetId = categoryService.canonicalCategoryId(userId, targetKey);
        if (targetId.equals(category.id())) {
          continue;
        }
        int moved =
            jdbcTemplate.update(
                "update transaction set category_id = ? where user_id = ? and category_id = ?",
                targetId,
                userId,
                category.id());
        if (moved > 0) {
          auditService.record(
              userId,
              AuditEventType.CATEGORY_MERGED,
              "category",
              category.id(),
              Map.of("targetCategory", targetKey, "transactions", moved));
        }
        if (transactionCount(userId, category.id()) == 0) {
          categoryRepository
              .findByIdAndUserId(category.id(), userId)
              .ifPresent(categoryRepository::delete);
          categoriesMerged++;
          transactionsMoved += moved;
          byTarget.merge(targetKey, moved, Integer::sum);
        }
      }
    }
    return new Report(dryRun, categoriesMerged, transactionsMoved, byTarget);
  }

  private String targetKey(CategoryView category) {
    if (category.predefined() || !CategoryTaxonomy.isKnownProviderName(category.name())) {
      return null;
    }
    String key = CategoryTaxonomy.canonicalKey(category.name());
    return key == null ? null : key;
  }

  private int transactionCount(UUID userId, UUID categoryId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from transaction where user_id = ? and category_id = ?",
            Integer.class,
            userId,
            categoryId);
    return count == null ? 0 : count;
  }
}
