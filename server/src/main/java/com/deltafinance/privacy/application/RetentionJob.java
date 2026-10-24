package com.deltafinance.privacy.application;

import com.deltafinance.shared.time.Clock;
import java.sql.Timestamp;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expurgo diário de dados operacionais conforme a política de retenção.
 *
 * <ul>
 *   <li>{@code login_attempt}: 30 dias;
 *   <li>{@code sync_run} e {@code webhook_event}: 90 dias;
 *   <li>{@code audit_event}: 1 ano;
 *   <li>{@code idempotency_key}: expiração de 24 h registrada em {@code expires_at}.
 * </ul>
 *
 * <p>O job nunca remove dados financeiros ativos nem eventos de auditoria dentro do prazo. A
 * execução é idempotente e registra apenas contadores sanitizados.
 */
@Component
@ConditionalOnProperty(
    prefix = "delta.privacy.retention",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RetentionJob {

  private static final Logger LOG = LoggerFactory.getLogger(RetentionJob.class);

  private static final Duration LOGIN_ATTEMPTS = Duration.ofDays(30);
  private static final Duration OPERATIONAL_HISTORY = Duration.ofDays(90);
  private static final Duration AUDIT_EVENTS = Duration.ofDays(365);

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  public RetentionJob(JdbcTemplate jdbcTemplate, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  @Scheduled(cron = "${delta.privacy.retention.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
  public void scheduled() {
    RetentionResult result = run();
    LOG.info(
        "Retenção executada: loginAttempts={} syncRuns={} webhookEvents={} auditEvents={}"
            + " idempotencyKeys={}",
        result.loginAttempts(),
        result.syncRuns(),
        result.webhookEvents(),
        result.auditEvents(),
        result.idempotencyKeys());
  }

  /** Executa o expurgo e devolve as contagens efetivamente removidas. */
  public RetentionResult run() {
    Timestamp now = Timestamp.from(clock.now());
    int loginAttempts =
        jdbcTemplate.update(
            "delete from login_attempt where attempted_at < ?",
            Timestamp.from(clock.now().minus(LOGIN_ATTEMPTS)));
    int syncRuns =
        jdbcTemplate.update(
            "delete from sync_run where coalesce(finished_at, started_at) < ?",
            Timestamp.from(clock.now().minus(OPERATIONAL_HISTORY)));
    int webhookEvents =
        jdbcTemplate.update(
            "delete from webhook_event where received_at < ?",
            Timestamp.from(clock.now().minus(OPERATIONAL_HISTORY)));
    int auditEvents =
        jdbcTemplate.update(
            "delete from audit_event where created_at < ?",
            Timestamp.from(clock.now().minus(AUDIT_EVENTS)));
    int idempotencyKeys =
        jdbcTemplate.update("delete from idempotency_key where expires_at < ?", now);
    return new RetentionResult(
        loginAttempts, syncRuns, webhookEvents, auditEvents, idempotencyKeys);
  }

  /** Contagens sanitizadas de uma execução. */
  public record RetentionResult(
      int loginAttempts, int syncRuns, int webhookEvents, int auditEvents, int idempotencyKeys) {}
}
