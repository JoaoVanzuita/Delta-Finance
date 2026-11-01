package com.deltafinance.invoices.application;

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
 * Executa a conversão de ajustes manuais de câmbio uma única vez, habilitada por propriedade.
 *
 * <pre>
 * ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--delta.transactions.currency-repair=true \
 *     --spring.main.web-application-type=none"
 * </pre>
 *
 * Use {@code --delta.transactions.currency-repair.dry-run=true} para simular.
 */
@Component
@ConditionalOnProperty(name = "delta.transactions.currency-repair", havingValue = "true")
public class CurrencyConversionRepairRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(CurrencyConversionRepairRunner.class);

  private final CurrencyConversionRepairService repairService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationContext applicationContext;
  private final boolean dryRun;

  public CurrencyConversionRepairRunner(
      CurrencyConversionRepairService repairService,
      JdbcTemplate jdbcTemplate,
      ApplicationContext applicationContext,
      @Value("${delta.transactions.currency-repair.dry-run:false}") boolean dryRun) {
    this.repairService = repairService;
    this.jdbcTemplate = jdbcTemplate;
    this.applicationContext = applicationContext;
    this.dryRun = dryRun;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<UUID> userIds = jdbcTemplate.queryForList("select id from app_user", UUID.class);
    int total = 0;
    for (UUID userId : userIds) {
      CurrencyConversionRepairService.Report report = repairService.repairUser(userId, dryRun);
      total += report.repaired();
      LOG.info(
          "Câmbio: usuário={} dryRun={} reparadas={} valor={}",
          userId,
          report.dryRun(),
          report.repaired(),
          report.amount());
    }
    LOG.info("Câmbio: total reparadas={} dryRun={}", total, dryRun);
    System.exit(SpringApplication.exit(applicationContext, () -> 0));
  }
}
