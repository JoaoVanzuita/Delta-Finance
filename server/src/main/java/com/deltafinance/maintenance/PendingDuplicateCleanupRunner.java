package com.deltafinance.maintenance;

import com.deltafinance.transactions.application.PendingDuplicateCleanupService;
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
 * Limpa duplicatas históricas de pagamento de cartão uma única vez.
 *
 * <pre>
 * ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--delta.transactions.pending-dedup=true \
 *     --spring.main.web-application-type=none"
 * </pre>
 *
 * Use {@code --delta.transactions.pending-dedup.dry-run=true} para simular.
 */
@Component
@ConditionalOnProperty(name = "delta.transactions.pending-dedup", havingValue = "true")
public class PendingDuplicateCleanupRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(PendingDuplicateCleanupRunner.class);

  private final PendingDuplicateCleanupService cleanupService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationContext applicationContext;
  private final boolean dryRun;

  public PendingDuplicateCleanupRunner(
      PendingDuplicateCleanupService cleanupService,
      JdbcTemplate jdbcTemplate,
      ApplicationContext applicationContext,
      @Value("${delta.transactions.pending-dedup.dry-run:false}") boolean dryRun) {
    this.cleanupService = cleanupService;
    this.jdbcTemplate = jdbcTemplate;
    this.applicationContext = applicationContext;
    this.dryRun = dryRun;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<UUID> userIds = jdbcTemplate.queryForList("select id from app_user", UUID.class);
    int total = 0;
    for (UUID userId : userIds) {
      PendingDuplicateCleanupService.Report report = cleanupService.cleanupUser(userId, dryRun);
      total += report.cleaned();
      LOG.info(
          "Duplicatas pendentes: usuário={} dryRun={} limpas={} valor={}",
          userId,
          report.dryRun(),
          report.cleaned(),
          report.amount());
    }
    LOG.info("Duplicatas pendentes: total limpas={} dryRun={}", total, dryRun);
    System.exit(SpringApplication.exit(applicationContext, () -> 0));
  }
}
