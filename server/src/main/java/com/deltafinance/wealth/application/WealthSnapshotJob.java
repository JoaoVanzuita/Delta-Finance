package com.deltafinance.wealth.application;

import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Captura mensal dos snapshots patrimoniais.
 *
 * <p>Roda no primeiro dia do mês e usa como referência o dia anterior à execução. É idempotente:
 * snapshots já existentes não são sobrescritos. Falhas por falta de dados são puladas; falhas
 * inesperadas são contadas e registradas sem valores financeiros.
 */
@Component
@ConditionalOnProperty(
    prefix = "delta.wealth.snapshot",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WealthSnapshotJob {

  private static final Logger LOG = LoggerFactory.getLogger(WealthSnapshotJob.class);

  private final WealthSnapshotService snapshotService;
  private final JdbcTemplate jdbcTemplate;
  private final AuditService auditService;
  private final Clock clock;

  public WealthSnapshotJob(
      WealthSnapshotService snapshotService,
      JdbcTemplate jdbcTemplate,
      AuditService auditService,
      Clock clock) {
    this.snapshotService = snapshotService;
    this.jdbcTemplate = jdbcTemplate;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Scheduled(cron = "${delta.wealth.snapshot.cron:0 10 0 1 * *}", zone = "America/Sao_Paulo")
  public void scheduled() {
    Result result = run();
    LOG.info(
        "Snapshots patrimoniais: created={} skipped={} failed={}",
        result.created(),
        result.skipped(),
        result.failed());
  }

  /** Executa a captura e devolve contagens sanitizadas. */
  public Result run() {
    LocalDate referenceDate = clock.today().minusDays(1);
    List<UUID> userIds = jdbcTemplate.queryForList("select id from app_user", UUID.class);
    int created = 0;
    int skipped = 0;
    int failed = 0;
    for (UUID userId : userIds) {
      try {
        WealthSnapshotService.CaptureResult result =
            snapshotService.captureScheduled(userId, referenceDate);
        if (result.created()) {
          created++;
        } else {
          skipped++;
        }
      } catch (ValidationException exception) {
        skipped++;
      } catch (RuntimeException exception) {
        failed++;
        LOG.error(
            "Falha ao capturar snapshot patrimonial userId={} referenceDate={} error={}",
            userId,
            referenceDate,
            exception.getClass().getSimpleName());
        auditService.record(
            userId,
            AuditEventType.WEALTH_SNAPSHOT_FAILED,
            WealthSnapshotService.SNAPSHOT_TABLE,
            null,
            Map.of(
                "referenceDate", referenceDate.toString(),
                "error", exception.getClass().getSimpleName()));
      }
    }
    return new Result(created, skipped, failed);
  }

  public record Result(int created, int skipped, int failed) {}
}
