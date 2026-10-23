package com.deltafinance.connections.application;

import com.deltafinance.connections.config.SyncProperties;
import com.deltafinance.connections.domain.SyncJobType;
import com.deltafinance.shared.time.Clock;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agendador de sincronização com jitter e shutdown gracioso.
 *
 * <p>Diariamente enfileira a janela de 7 dias por conexão; uma vez por mês enfileira a
 * reconciliação de 12 meses. O jitter evita rajadas simultâneas. A cadência não promete tempo real
 * e um usuário com falha não bloqueia os demais. Ao desligar, nenhum job novo é enfileirado e o
 * processamento em andamento termina antes do encerramento do contexto.
 */
@Component
@ConditionalOnProperty(
    prefix = "delta.sync",
    name = "scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SyncScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(SyncScheduler.class);

  private final SyncJobService syncJobService;
  private final SyncProperties properties;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final String workerId = "worker-" + UUID.randomUUID();
  private volatile boolean running = true;
  private LocalDate lastDailyEnqueue;
  private YearMonth lastReconciliationEnqueue;

  public SyncScheduler(
      SyncJobService syncJobService,
      SyncProperties properties,
      JdbcTemplate jdbcTemplate,
      Clock clock) {
    this.syncJobService = syncJobService;
    this.properties = properties;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  @Scheduled(
      initialDelayString = "${delta.sync.initial-delay:30s}",
      fixedDelayString = "${delta.sync.scheduler-interval:1m}")
  public void tick() {
    if (!running) {
      return;
    }
    try {
      enqueueDueWindows();
      int processed =
          syncJobService.runDue(
              workerId, "scheduler-" + UUID.randomUUID(), properties.getBatchSize());
      if (processed > 0) {
        LOG.info("Scheduler processou {} job(s) de sincronização", processed);
      }
    } catch (RuntimeException exception) {
      // O scheduler nunca deve morrer por causa de uma falha isolada.
      LOG.error(
          "Falha no ciclo do scheduler de sincronização: {}", exception.getClass().getSimpleName());
    }
  }

  private void enqueueDueWindows() {
    LocalDate today = clock.today();
    if (!today.equals(lastDailyEnqueue)) {
      enqueueAll(SyncJobType.SYNC_CONNECTION);
      lastDailyEnqueue = today;
    }
    YearMonth currentMonth = YearMonth.from(today);
    if (today.getDayOfMonth() == properties.getReconciliationDayOfMonth()
        && !currentMonth.equals(lastReconciliationEnqueue)) {
      enqueueAll(SyncJobType.RECONCILE);
      lastReconciliationEnqueue = currentMonth;
    }
  }

  private void enqueueAll(SyncJobType jobType) {
    List<ActiveConnection> connections =
        jdbcTemplate.query(
            """
            SELECT id, user_id, integration_id
              FROM pluggy_connection
             WHERE status IN ('ACTIVE', 'SYNCED')
               AND external_item_id_enc IS NOT NULL
            """,
            (rs, rowNum) ->
                new ActiveConnection(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("integration_id", UUID.class)));
    for (ActiveConnection connection : connections) {
      if (!running) {
        return;
      }
      Instant runAfter = clock.now().plusSeconds(jitterSeconds());
      try {
        syncJobService.enqueue(
            connection.userId(), connection.integrationId(), connection.id(), jobType, runAfter);
      } catch (RuntimeException exception) {
        // Isolamento por conexão: uma falha de enfileiramento não interrompe as demais.
        LOG.warn(
            "Não foi possível enfileirar {} para connectionId={}: {}",
            jobType,
            connection.id(),
            exception.getClass().getSimpleName());
      }
    }
  }

  private long jitterSeconds() {
    long jitter = Math.max(0, properties.getJitter().toSeconds());
    return jitter == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitter + 1);
  }

  /** Sinaliza shutdown; jobs em andamento terminam antes do fechamento do contexto. */
  @PreDestroy
  void shutdown() {
    running = false;
    LOG.info("Scheduler de sincronização sinalizado para encerrar");
  }

  private record ActiveConnection(UUID id, UUID userId, UUID integrationId) {}
}
