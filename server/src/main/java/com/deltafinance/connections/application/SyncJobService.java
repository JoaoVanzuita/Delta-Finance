package com.deltafinance.connections.application;

import com.deltafinance.connections.config.SyncProperties;
import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.connections.domain.SyncJob;
import com.deltafinance.connections.domain.SyncJobRepository;
import com.deltafinance.connections.domain.SyncJobStatus;
import com.deltafinance.connections.domain.SyncJobType;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private final WebhookEventProcessor webhookEventProcessor;
  private final SyncProperties properties;
  private final SyncMetrics metrics;
  private final Clock clock;
  private final JdbcTemplate jdbcTemplate;
  private final ConcurrentHashMap<UUID, ReentrantLock> integrationLocks = new ConcurrentHashMap<>();

  public SyncJobService(
      SyncJobRepository jobRepository,
      PluggyIntegrationRepository integrationRepository,
      PluggyConnectionRepository connectionRepository,
      PluggySyncService syncService,
      WebhookEventProcessor webhookEventProcessor,
      SyncProperties properties,
      SyncMetrics metrics,
      JdbcTemplate jdbcTemplate,
      Clock clock) {
    this.jobRepository = jobRepository;
    this.integrationRepository = integrationRepository;
    this.connectionRepository = connectionRepository;
    this.syncService = syncService;
    this.webhookEventProcessor = webhookEventProcessor;
    this.properties = properties;
    this.metrics = metrics;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
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
    try {
      process(job, correlationId);
    } catch (RuntimeException exception) {
      // Fail-safe: um erro inesperado não pode deixar o job preso em RUNNING.
      LOG.error(
          "Falha inesperada ao processar job {}: {}",
          job.getId(),
          exception.getClass().getSimpleName());
      LOG.debug("Detalhes da falha inesperada do job", exception);
      job.markFailed("INTERNAL_ERROR");
      jobRepository.save(job);
    } finally {
      lock.unlock();
      if (!lock.hasQueuedThreads()) {
        integrationLocks.remove(lockKey, lock);
      }
    }
    return Optional.of(job.getId());
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

  private void process(SyncJob job, String correlationId) {
    boolean success =
        switch (job.getJobType()) {
          case SYNC_CONNECTION, SYNC_INTEGRATION, RECONCILE -> runSync(job, correlationId);
          case PROCESS_WEBHOOK -> processWebhook(job, correlationId);
          case RECALCULATE_INVOICE -> true;
        };
    if (!success && job.getStatus() == SyncJobStatus.RUNNING) {
      job.markFailed("SYNC_FAILED");
    }
    jobRepository.save(job);
    metrics.recordJob(job.getStatus());
  }

  private boolean processWebhook(SyncJob job, String correlationId) {
    boolean processed = webhookEventProcessor.processPending(job, correlationId);
    if (!processed && job.canRetry()) {
      job.scheduleRetry(
          "WEBHOOK_SYNC_FAILED", clock.now().plus(properties.retryDelay(job.getAttempts())));
    }
    return processed;
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
}
