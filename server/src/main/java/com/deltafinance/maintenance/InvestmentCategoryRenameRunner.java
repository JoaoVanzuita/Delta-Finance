package com.deltafinance.maintenance;

import com.deltafinance.categories.application.InvestmentCategoryRenameService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Executa uma única vez a renomeação da categoria legada "Aportes" para "Aplicações".
 *
 * <pre>
 * ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--delta.categories.investment-rename=true \
 *     --spring.main.web-application-type=none"
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "delta.categories.investment-rename", havingValue = "true")
public class InvestmentCategoryRenameRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(InvestmentCategoryRenameRunner.class);

  private final InvestmentCategoryRenameService renameService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationContext applicationContext;

  public InvestmentCategoryRenameRunner(
      InvestmentCategoryRenameService renameService,
      JdbcTemplate jdbcTemplate,
      ApplicationContext applicationContext) {
    this.renameService = renameService;
    this.jdbcTemplate = jdbcTemplate;
    this.applicationContext = applicationContext;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<UUID> userIds = jdbcTemplate.queryForList("select id from app_user", UUID.class);
    int renamed = 0;
    for (UUID userId : userIds) {
      InvestmentCategoryRenameService.Report report = renameService.renameUser(userId);
      if (report.legacyDeactivated()) {
        renamed++;
      }
    }
    LOG.info("Categoria de investimento: usuários com Aportes desativado={}", renamed);
    System.exit(SpringApplication.exit(applicationContext, () -> 0));
  }
}
