package com.deltafinance.connections.application;

import com.deltafinance.connections.config.SyncProperties;
import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.connections.domain.SyncJob;
import com.deltafinance.connections.domain.SyncJobRepository;
import com.deltafinance.connections.domain.SyncJobStatus;
import com.deltafinance.connections.domain.SyncJobType;
import com.deltafinance.connections.domain.SyncRun;
import com.deltafinance.connections.domain.SyncRunRepository;
import com.deltafinance.connections.domain.SyncRunStatus;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.sync.SyncCancellationRegistry;
import com.deltafinance.shared.sync.SyncCancelledException;
import com.deltafinance.shared.time.Clock;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Fila durável de sincronização.
 *
 * <p>O claim é atômico ({@code FOR UPDATE SKIP LOCKED}) e cada integração tem um lock local que
 * serializa conexões do mesmo usuário sem bloquear as demais. Retries usam backoff exponencial,
 * exceto quando o provedor informa {@code Retry-After}. A falha de uma integração nunca impede o
 * processamento das outras.
 */
@Service
public class SyncJobService {

  private static final Logger LOG = LoggerFactory.getLogger(SyncJobService.class);
  private static final List<SyncJobStatus> ACTIVE_STATUSES =
      List.of(SyncJobStatus.QUEUED, SyncJobStatus.RUNNING, SyncJobStatus.RETRY);

  private final SyncJobRepository jobRepository;
  private final PluggyIntegrationRepository integrationRepository;
  private final PluggyConnectionRepository connectionRepository;
  private final PluggySyncService syncService;
  private final SyncProperties properties;
  private final SyncMetrics metrics;
  private final Clock clock;
  private final JdbcTemplate jdbcTemplate;
  private final SyncRunRepository syncRunRepository;
  private final SyncCancellationRegistry cancellationRegistry;
  private final ConcurrentHashMap<UUID, ReentrantLock> integrationLocks = new ConcurrentHashMap<>();
  private final ScheduledExecutorService watchdog =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "sync-job-timeout");
            thread.setDaemon(true);
            return thread;
          });

  public SyncJobService(
      SyncJobRepository jobRepository,
      PluggyIntegrationRepository integrationRepository,
      PluggyConnectionRepository connectionRepository,
      PluggySyncService syncService,
      SyncProperties properties,
      SyncMetrics metrics,
      JdbcTemplate jdbcTemplate,
      SyncRunRepository syncRunRepository,
      SyncCancellationRegistry cancellationRegistry,
      Clock clock) {
    this.jobRepository = jobRepository;
    this.integrationRepository = integrationRepository;
    this.connectionRepository = connectionRepository;
    this.syncService = syncService;
    this.properties = properties;
    this.metrics = metrics;
    this.jdbcTemplate = jdbcTemplate;
    this.syncRunRepository = syncRunRepository;
    this.cancellationRegistry = cancellationRegistry;
    this.clock = clock;
  }

  @PreDestroy
  void shutdownWatchdog() {
    watchdog.shutdownNow();
  }

  /**
   * Recupera jobs {@code RUNNING} deixados por um encerramento abrupto (ADR-0004).
   *
   * <p>Como a implantação de referência usa uma única instância, todo job em execução no start é
   * órfão. A tentativa vira uma linha em {@code sync_run}, o job volta para retry/falha e os
   * estados {@code SYNCING} são liberados.
   */
  @EventListener(ApplicationReadyEvent.class)
  public int recoverOrphanedJobs() {
    int recovered = 0;
    for (SyncJob job : jobRepository.runningJobs()) {
      recoverOrphan(job);
      recovered++;
    }
    if (recovered > 0) {
      LOG.warn("Recuperados {} job(s) de sincronização órfão(s)", recovered);
    }
    return recovered;
  }

  private void recoverOrphan(SyncJob job) {
    Instant startedAt = job.getLockedAt() == null ? clock.now() : job.getLockedAt();
    recordInterruptedRun(job, startedAt, SyncRunStatus.FAILED, "INTERRUPTED");
    if (job.canRetry()) {
      job.scheduleRetry("INTERRUPTED", clock.now());
    } else {
      job.markFailed("INTERRUPTED");
    }
    jobRepository.save(job);
    releaseSyncState(job);
    metrics.recordJob(job.getStatus());
  }

  /** Enfileira a consulta de dados já disponíveis de uma conexão do usuário. */
  public SyncJob enqueueConnection(
      UUID userId, UUID connectionId, SyncJobType jobType, Instant runAfter) {
    PluggyConnection connection =
        connectionRepository
            .findByIdAndUserId(connectionId, userId)
            .orElseThrow(NotFoundException::new);
    if (!connection.hasExternalItemId()) {
      throw new ConflictException(
          ErrorCode.CONNECTION_UNAVAILABLE, "A conexão não possui um itemId válido.");
    }
    PluggyIntegration integration =
        integrationRepository
            .findByIdAndUserId(connection.getIntegrationId(), userId)
            .orElseThrow(NotFoundException::new);
    return enqueue(
        userId,
        integration.getId(),
        connection.getId(),
        jobType,
        runAfter == null ? clock.now() : runAfter);
  }

  /** Enfileira a consulta de dados já disponíveis e devolve o id do job. */
  public UUID enqueueAvailableData(UUID userId, UUID connectionId) {
    return enqueueConnection(userId, connectionId, SyncJobType.SYNC_CONNECTION, null).getId();
  }

  /** Enfileira de forma idempotente: devolve o job ativo existente quando houver. */
  public SyncJob enqueue(
      UUID userId, UUID integrationId, UUID connectionId, SyncJobType jobType, Instant runAfter) {
    Optional<SyncJob> existing =
        jobRepository.findFirstByUserIdAndConnectionIdAndJobTypeAndStatusInOrderByCreatedAtAsc(
            userId, connectionId, jobType, ACTIVE_STATUSES);
    if (existing.isPresent()) {
      return existing.get();
    }
    SyncJob job =
        new SyncJob(
            userId, integrationId, connectionId, jobType, runAfter, properties.getMaxAttempts());
    try {
      return jobRepository.save(job);
    } catch (DataIntegrityViolationException race) {
      // Outro processo enfileirou o mesmo alvo entre a consulta e o insert.
      return jobRepository
          .findFirstByUserIdAndConnectionIdAndJobTypeAndStatusInOrderByCreatedAtAsc(
              userId, connectionId, jobType, ACTIVE_STATUSES)
          .orElseThrow(() -> race);
    }
  }

  /**
   * Cancela jobs ativos de um usuário (exclusão de conta ou desconexão total).
   *
   * <p>O job é marcado como falho com código sanitizado. Jobs já em execução terminam no próximo
   * ponto seguro; conexões removidas fazem as escritas seguintes falhar por integridade
   * referencial.
   */
  public int cancelActiveForUser(UUID userId) {
    return cancel(jobRepository.findActiveByUserId(userId), userId);
  }

  /** Cancela jobs ativos de uma integração do usuário. */
  public int cancelActiveForIntegration(UUID userId, UUID integrationId) {
    return cancel(jobRepository.findActiveByIntegrationId(integrationId), userId);
  }

  /** Cancela jobs ativos de uma conexão do usuário. */
  public int cancelActiveForConnection(UUID userId, UUID connectionId) {
    return cancel(
        jobRepository.findActiveByUserId(userId).stream()
            .filter(job -> connectionId.equals(job.getConnectionId()))
            .toList(),
        userId);
  }

  private int cancel(List<SyncJob> jobs, UUID userId) {
    int cancelled = 0;
    for (SyncJob job : jobs) {
      if (!userId.equals(job.getUserId()) || !job.isActive()) {
        continue;
      }
      cancellationRegistry.cancel(job.getId(), SyncCancellationRegistry.Reason.CANCELLED);
      job.markFailed("DISCONNECTED");
      jobRepository.save(job);
      releaseSyncState(job);
      cancelled++;
    }
    return cancelled;
  }

  /** Claim + processamento de um job. Retorna vazio quando a fila está sem trabalho vencido. */
  public Optional<UUID> processNext(String worker, String correlationId) {
    UUID claimed = claimNext(worker);
    if (claimed == null) {
      return Optional.empty();
    }
    SyncJob job = jobRepository.findById(claimed).orElse(null);
    if (job == null) {
      return Optional.of(claimed);
    }

    UUID lockKey = job.getIntegrationId() == null ? job.getId() : job.getIntegrationId();
    ReentrantLock lock = integrationLocks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
    if (!lock.tryLock()) {
      job.release(clock.now().plus(properties.getRetryBase()));
      jobRepository.save(job);
      LOG.debug("Job {} devolvido à fila: integração já em processamento", job.getId());
      return Optional.of(job.getId());
    }
    SyncCancellationRegistry.Token token = cancellationRegistry.begin(job.getId());
    cancellationRegistry.bind(token);
    ScheduledFuture<?> timeout =
        watchdog.schedule(
            () -> onTimeout(job.getId(), token),
            Math.max(1, properties.getJobTimeout().toMillis()),
            TimeUnit.MILLISECONDS);
    try {
      process(job, correlationId, token);
    } catch (SyncCancelledException cancelled) {
      // O cancelamento/timeout já marcou o job; um worker atrasado não deve sobrescrevê-lo.
      LOG.debug("Job {} interrompido por {}", job.getId(), cancelled.reason());
    } catch (RuntimeException exception) {
      // Fail-safe: um erro inesperado não pode deixar o job preso em RUNNING.
      LOG.error(
          "Falha inesperada ao processar job {}: {}",
          job.getId(),
          exception.getClass().getSimpleName());
      LOG.debug("Detalhes da falha inesperada do job", exception);
      if (!token.isCancelled()) {
        job.markFailed("INTERNAL_ERROR");
        jobRepository.save(job);
      }
    } finally {
      timeout.cancel(false);
      cancellationRegistry.finish(token);
      cancellationRegistry.unbind();
      lock.unlock();
      if (!lock.hasQueuedThreads()) {
        integrationLocks.remove(lockKey, lock);
      }
    }
    return Optional.of(job.getId());
  }

  private void onTimeout(UUID jobId, SyncCancellationRegistry.Token token) {
    if (token.isCancelled()) {
      return;
    }
    cancellationRegistry.cancel(jobId, SyncCancellationRegistry.Reason.TIMEOUT);
    SyncJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != SyncJobStatus.RUNNING) {
      return;
    }
    if (job.canRetry()) {
      job.scheduleRetry("JOB_TIMEOUT", clock.now());
    } else {
      job.markFailed("JOB_TIMEOUT");
    }
    // Libera o estado antes de publicar o novo status do job, para leitores consistentes.
    releaseSyncState(job);
    jobRepository.save(job);
    metrics.recordJob(job.getStatus());
    LOG.warn("Job {} excedeu o timeout de sincronização e foi liberado", jobId);
  }

  /** Processa até {@code maxJobs} jobs vencidos. */
  public int runDue(String worker, String correlationId, int maxJobs) {
    int processed = 0;
    while (processed < maxJobs) {
      if (processNext(worker, correlationId).isEmpty()) {
        break;
      }
      processed++;
    }
    return processed;
  }

  /** Quantidade de jobs ativos do usuário (para o painel de status). */
  public long pendingCount(UUID userId) {
    return jobRepository.findActiveByUserId(userId).size();
  }

  /**
   * Claim atômico de um job vencido.
   *
   * <p>{@code FOR UPDATE SKIP LOCKED} impede que dois workers processem o mesmo job. O índice
   * parcial de jobs ativos também impede trabalho concorrente para a mesma conexão.
   */
  private UUID claimNext(String worker) {
    java.sql.Timestamp now = java.sql.Timestamp.from(clock.now());
    return jdbcTemplate.query(
        connection -> {
          java.sql.PreparedStatement statement =
              connection.prepareStatement(
                  """
                  UPDATE sync_job
                     SET status = 'RUNNING',
                         locked_by = ?,
                         locked_at = ?,
                         attempts = attempts + 1,
                         updated_at = ?
                   WHERE id = (
                     SELECT id
                       FROM sync_job
                      WHERE status IN ('QUEUED', 'RETRY')
                        AND run_after <= ?
                      ORDER BY run_after, created_at
                      LIMIT 1
                      FOR UPDATE SKIP LOCKED
                   )
                  RETURNING id
                  """);
          statement.setString(1, worker);
          statement.setTimestamp(2, now);
          statement.setTimestamp(3, now);
          statement.setTimestamp(4, now);
          return statement;
        },
        rows -> {
          if (!rows.next()) {
            return null;
          }
          return rows.getObject(1, UUID.class);
        });
  }

  private void process(SyncJob job, String correlationId, SyncCancellationRegistry.Token token) {
    boolean success =
        switch (job.getJobType()) {
          case SYNC_CONNECTION, SYNC_INTEGRATION, RECONCILE -> runSync(job, correlationId);
          case RECALCULATE_INVOICE -> true;
        };
    if (token.isCancelled()) {
      // O job já foi marcado por cancelamento/timeout; não sobrescreva o estado mais novo.
      return;
    }
    if (!success && job.getStatus() == SyncJobStatus.RUNNING) {
      job.markFailed("SYNC_FAILED");
    }
    jobRepository.save(job);
    metrics.recordJob(job.getStatus());
  }

  private boolean runSync(SyncJob job, String correlationId) {
    if (job.getUserId() == null
        || job.getIntegrationId() == null
        || job.getConnectionId() == null) {
      job.markFailed("INVALID_JOB");
      return false;
    }
    Optional<PluggyIntegration> integration =
        integrationRepository.findByIdAndUserId(job.getIntegrationId(), job.getUserId());
    Optional<PluggyConnection> connection =
        connectionRepository.findByIdAndUserId(job.getConnectionId(), job.getUserId());
    if (integration.isEmpty() || connection.isEmpty()) {
      job.markFailed("CONNECTION_NOT_FOUND");
      return false;
    }
    if (!connection.get().hasExternalItemId()) {
      job.markFailed("ITEM_UNAVAILABLE");
      return false;
    }

    java.time.LocalDate from =
        job.getJobType() == SyncJobType.RECONCILE
            ? clock.today().minusMonths(properties.getReconciliationWindowMonths())
            : null;
    cancellationRegistry.checkpoint();
    SyncResult result = syncService.sync(integration.get(), connection.get(), from, correlationId);
    if (result.succeeded()) {
      job.markSucceeded();
      return true;
    }
    String code = result.failure() == null ? "SYNC_FAILED" : result.failure().errorCode();
    if (result.retryable() && job.canRetry()) {
      Instant next =
          clock
              .now()
              .plus(
                  result.failure() != null && result.failure().retryAfter() != null
                      ? result.failure().retryAfter()
                      : properties.retryDelay(job.getAttempts()));
      job.scheduleRetry(code, next);
    } else {
      job.markFailed(code);
    }
    return false;
  }

  private void recordInterruptedRun(
      SyncJob job, Instant startedAt, SyncRunStatus status, String errorCode) {
    if (job.getUserId() == null
        || job.getIntegrationId() == null
        || job.getJobType() == SyncJobType.RECALCULATE_INVOICE) {
      return;
    }
    SyncRun run =
        new SyncRun(
            job.getUserId(),
            job.getIntegrationId(),
            job.getConnectionId(),
            startedAt,
            "recovery-" + job.getId());
    run.finish(status, 0, 0, 0, 0, 0, errorCode, clock.now());
    syncRunRepository.save(run);
    metrics.recordRun(run);
  }

  /** Libera {@code SYNCING} na conexão/integração sem apagar o último estado conhecido. */
  private void releaseSyncState(SyncJob job) {
    if (job.getUserId() == null) {
      return;
    }
    if (job.getConnectionId() != null) {
      connectionRepository
          .findByIdAndUserId(job.getConnectionId(), job.getUserId())
          .ifPresent(
              connection -> {
                connection.releaseSync();
                connectionRepository.save(connection);
              });
    }
    if (job.getIntegrationId() != null) {
      integrationRepository
          .findByIdAndUserId(job.getIntegrationId(), job.getUserId())
          .ifPresent(
              integration -> {
                integration.releaseSync();
                integrationRepository.save(integration);
              });
    }
  }
}
