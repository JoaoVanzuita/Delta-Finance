package com.deltafinance.categories.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Executa a consolidação de categorias uma única vez quando habilitada por propriedade. Não faz
 * parte do fluxo normal da aplicação.
 *
 * <pre>
 * ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--delta.categories.backfill=true --spring.main.web-application-type=none"
 * </pre>
 *
 * Use {@code --delta.categories.backfill.dry-run=true} para simular.
 */
@Component
@ConditionalOnProperty(name = "delta.categories.backfill", havingValue = "true")
public class CategoryConsolidationRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(CategoryConsolidationRunner.class);

  private final CategoryConsolidationService consolidationService;
  private final ApplicationContext applicationContext;
  private final boolean dryRun;

  public CategoryConsolidationRunner(
      CategoryConsolidationService consolidationService,
      ApplicationContext applicationContext,
      @Value("${delta.categories.backfill.dry-run:false}") boolean dryRun) {
    this.consolidationService = consolidationService;
    this.applicationContext = applicationContext;
    this.dryRun = dryRun;
  }

  @Override
  public void run(ApplicationArguments args) {
    CategoryConsolidationService.Report report = consolidationService.consolidateAllUsers(dryRun);
    LOG.info(
        "Consolidação de categorias dryRun={} categorias={} transações={} destinos={}",
        report.dryRun(),
        report.categoriesMerged(),
        report.transactionsMoved(),
        report.transactionsByTarget());
    int exitCode = SpringApplication.exit(applicationContext, () -> 0);
    System.exit(exitCode);
  }
}
