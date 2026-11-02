package com.deltafinance.maintenance;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.invoices.application.PendingCardBackfillService;
import com.deltafinance.shared.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * Executa o backfill documental de um cartão uma única vez, habilitado por propriedade.
 *
 * <pre>
 * ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--delta.cards.backfill=true \
 *     --delta.cards.backfill.account='Cartão Sicoob' \
 *     --delta.cards.backfill.from-bill-month=2025-12-01 \
 *     --spring.main.web-application-type=none"
 * </pre>
 *
 * Use {@code --delta.cards.backfill.dry-run=true} para apenas contar as transações que seriam
 * promovidas.
 */
@Component
@ConditionalOnProperty(name = "delta.cards.backfill", havingValue = "true")
public class PendingCardBackfillRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(PendingCardBackfillRunner.class);

  private final PendingCardBackfillService backfillService;
  private final AccountQueryService accountQueryService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationContext applicationContext;
  private final Clock clock;
  private final String accountReference;
  private final LocalDate fromBillMonth;
  private final LocalDate untilBillMonth;
  private final boolean dryRun;

  public PendingCardBackfillRunner(
      PendingCardBackfillService backfillService,
      AccountQueryService accountQueryService,
      JdbcTemplate jdbcTemplate,
      ApplicationContext applicationContext,
      Clock clock,
      @Value("${delta.cards.backfill.account:}") String accountReference,
      @Value("${delta.cards.backfill.from-bill-month:2025-12-01}") String fromBillMonth,
      @Value("${delta.cards.backfill.until-bill-month:}") String untilBillMonth,
      @Value("${delta.cards.backfill.dry-run:false}") boolean dryRun) {
    this.backfillService = backfillService;
    this.accountQueryService = accountQueryService;
    this.jdbcTemplate = jdbcTemplate;
    this.applicationContext = applicationContext;
    this.clock = clock;
    this.accountReference = accountReference;
    this.fromBillMonth = LocalDate.parse(fromBillMonth);
    this.untilBillMonth =
        untilBillMonth == null || untilBillMonth.isBlank()
            ? YearMonth.from(clock.today()).atDay(1)
            : LocalDate.parse(untilBillMonth);
    this.dryRun = dryRun;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (accountReference == null || accountReference.isBlank()) {
      LOG.error("Backfill ignorado: informe --delta.cards.backfill.account com UUID ou apelido.");
      System.exit(SpringApplication.exit(applicationContext, () -> 2));
      return;
    }
    List<UUID> userIds = jdbcTemplate.queryForList("select id from app_user", UUID.class);
    int processed = 0;
    for (UUID userId : userIds) {
      for (AccountView account : accountQueryService.list(userId)) {
        if (!"CREDIT".equals(account.type()) || !matches(account)) {
          continue;
        }
        PendingCardBackfillService.Report report =
            backfillService.backfill(userId, account.id(), fromBillMonth, untilBillMonth, dryRun);
        processed++;
        LOG.info(
            "Backfill cartão {} ({}): dryRun={} promovidas={} foraDaJanela={} compras={} créditos={} faturas {}->{}",
            report.cardAccountId(),
            account.alias(),
            report.dryRun(),
            report.promoted(),
            report.skippedBeforeWindow(),
            report.purchases(),
            report.credits(),
            report.invoicesBefore(),
            report.invoicesAfter());
      }
    }
    if (processed == 0) {
      LOG.warn("Nenhum cartão encontrado para a referência '{}'.", accountReference);
      System.exit(SpringApplication.exit(applicationContext, () -> 3));
      return;
    }
    System.exit(SpringApplication.exit(applicationContext, () -> 0));
  }

  private boolean matches(AccountView account) {
    return account.id().toString().equalsIgnoreCase(accountReference)
        || (account.alias() != null && account.alias().equalsIgnoreCase(accountReference))
        || (account.providerName() != null
            && account.providerName().equalsIgnoreCase(accountReference));
  }
}
