package com.deltafinance.invoices.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
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
 * Realoca pagamentos de um cartão uma única vez quando habilitado por propriedade.
 *
 * <pre>
 * ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--delta.invoices.payment-allocation-repair=true \
 *     --delta.invoices.payment-allocation-repair.account='Cartão Nubank' \
 *     --spring.main.web-application-type=none"
 * </pre>
 *
 * Use {@code --delta.invoices.payment-allocation-repair.dry-run=true} para simular. O cartão deve
 * ser informado por UUID ou apelido; cartões com histórico incompleto exigem conferência manual.
 */
@Component
@ConditionalOnProperty(name = "delta.invoices.payment-allocation-repair", havingValue = "true")
public class PaymentAllocationRepairRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentAllocationRepairRunner.class);

  private final PaymentAllocationRepairService repairService;
  private final AccountQueryService accountQueryService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationContext applicationContext;
  private final String accountReference;
  private final boolean dryRun;

  public PaymentAllocationRepairRunner(
      PaymentAllocationRepairService repairService,
      AccountQueryService accountQueryService,
      JdbcTemplate jdbcTemplate,
      ApplicationContext applicationContext,
      @Value("${delta.invoices.payment-allocation-repair.account:}") String accountReference,
      @Value("${delta.invoices.payment-allocation-repair.dry-run:false}") boolean dryRun) {
    this.repairService = repairService;
    this.accountQueryService = accountQueryService;
    this.jdbcTemplate = jdbcTemplate;
    this.applicationContext = applicationContext;
    this.accountReference = accountReference;
    this.dryRun = dryRun;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (accountReference == null || accountReference.isBlank()) {
      LOG.error("Informe --delta.invoices.payment-allocation-repair.account com UUID ou apelido.");
      System.exit(SpringApplication.exit(applicationContext, () -> 2));
      return;
    }
    List<UUID> userIds = jdbcTemplate.queryForList("select id from app_user", UUID.class);
    int total = 0;
    for (UUID userId : userIds) {
      for (AccountView account : accountQueryService.list(userId)) {
        if (!"CREDIT".equals(account.type()) || !matches(account)) {
          continue;
        }
        PaymentAllocationRepairService.Report report =
            repairService.repairCard(userId, account.id(), dryRun);
        total += report.reassigned();
        LOG.info(
            "Pagamentos: cartão={} ({}) dryRun={} realocados={} valor={}",
            report.cardAccountId(),
            account.alias(),
            report.dryRun(),
            report.reassigned(),
            report.amount());
      }
    }
    if (total == 0) {
      LOG.warn("Nenhum pagamento precisou de realocação para '{}'.", accountReference);
    }
    LOG.info("Pagamentos: total realocados={} dryRun={}", total, dryRun);
    System.exit(SpringApplication.exit(applicationContext, () -> 0));
  }

  private boolean matches(AccountView account) {
    return account.id().toString().equalsIgnoreCase(accountReference)
        || (account.alias() != null && account.alias().equalsIgnoreCase(accountReference))
        || (account.providerName() != null
            && account.providerName().equalsIgnoreCase(accountReference));
  }
}
