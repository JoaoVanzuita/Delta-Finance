package com.deltafinance.maintenance;

import com.deltafinance.transactions.application.SalaryCategoryBackfillService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Executa a reclassificação de salário uma única vez quando habilitada por propriedade.
 *
 * <pre>
 * ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--delta.categories.salary-backfill=true \
 *     --spring.main.web-application-type=none"
 * </pre>
 *
 * Use {@code --delta.categories.salary-backfill.dry-run=true} para simular.
 */
@Component
@ConditionalOnProperty(name = "delta.categories.salary-backfill", havingValue = "true")
public class SalaryCategoryBackfillRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(SalaryCategoryBackfillRunner.class);

  private final SalaryCategoryBackfillService backfillService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationContext applicationContext;
  private final boolean dryRun;

  public SalaryCategoryBackfillRunner(
      SalaryCategoryBackfillService backfillService,
      JdbcTemplate jdbcTemplate,
      ApplicationContext applicationContext,
      @Value("${delta.categories.salary-backfill.dry-run:false}") boolean dryRun) {
    this.backfillService = backfillService;
    this.jdbcTemplate = jdbcTemplate;
    this.applicationContext = applicationContext;
    this.dryRun = dryRun;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<UUID> userIds = jdbcTemplate.queryForList("select id from app_user", UUID.class);
    int total = 0;
    for (UUID userId : userIds) {
      SalaryCategoryBackfillService.Report report = backfillService.backfillUser(userId, dryRun);
      total += report.reclassified();
      LOG.info(
          "Salário: usuário={} dryRun={} reclassificadas={} valor={}",
          userId,
          report.dryRun(),
          report.reclassified(),
          report.amount());
    }
    LOG.info("Salário: total reclassificadas={} dryRun={}", total, dryRun);
    System.exit(SpringApplication.exit(applicationContext, () -> 0));
  }
}
