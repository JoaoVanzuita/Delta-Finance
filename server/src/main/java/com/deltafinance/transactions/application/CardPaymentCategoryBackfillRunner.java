package com.deltafinance.transactions.application;

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
 * Reclassifica pagamentos de fatura em contas bancárias uma única vez.
 *
 * <pre>
 * ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--delta.categories.card-payment-backfill=true \
 *     --spring.main.web-application-type=none"
 * </pre>
 *
 * Use {@code --delta.categories.card-payment-backfill.dry-run=true} para simular.
 */
@Component
@ConditionalOnProperty(name = "delta.categories.card-payment-backfill", havingValue = "true")
public class CardPaymentCategoryBackfillRunner implements ApplicationRunner {

  private static final Logger LOG =
      LoggerFactory.getLogger(CardPaymentCategoryBackfillRunner.class);

  private final CardPaymentCategoryBackfillService backfillService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationContext applicationContext;
  private final boolean dryRun;

  public CardPaymentCategoryBackfillRunner(
      CardPaymentCategoryBackfillService backfillService,
      JdbcTemplate jdbcTemplate,
      ApplicationContext applicationContext,
      @Value("${delta.categories.card-payment-backfill.dry-run:false}") boolean dryRun) {
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
      CardPaymentCategoryBackfillService.Report report =
          backfillService.backfillUser(userId, dryRun);
      total += report.reclassified();
      LOG.info(
          "Pagamento de fatura: usuário={} dryRun={} reclassificadas={} valor={}",
          userId,
          report.dryRun(),
          report.reclassified(),
          report.amount());
    }
    LOG.info("Pagamento de fatura: total reclassificadas={} dryRun={}", total, dryRun);
    System.exit(SpringApplication.exit(applicationContext, () -> 0));
  }
}
