package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.privacy.application.RetentionJob;
import com.deltafinance.support.PostgresTestBase;
import com.deltafinance.support.TestDataFactory;
import com.deltafinance.support.TwoUserFixture.TestUser;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Retenção operacional (T191): expurgo dos expirados e preservação dos vigentes. */
class RetentionJobIT extends PostgresTestBase {

  @Autowired private RetentionJob retentionJob;
  @Autowired private PasswordEncoder passwordEncoder;

  private TestUser user;
  private UUID integrationId;
  private UUID connectionId;

  @BeforeEach
  void setUp() {
    cleanIdentityData();
    user = TestDataFactory.createUser(jdbcTemplate, passwordEncoder, "retention");
    integrationId = UUID.randomUUID();
    connectionId = UUID.randomUUID();
    Timestamp now = Timestamp.from(java.time.Instant.now());
    jdbcTemplate.update(
        "insert into pluggy_integration"
            + " (id, user_id, provider, status, created_at, updated_at, version)"
            + " values (?, ?, 'MEU_PLUGGY', 'INCOMPLETE', ?, ?, 0)",
        integrationId,
        user.id(),
        now,
        now);
    jdbcTemplate.update(
        "insert into pluggy_connection"
            + " (id, user_id, integration_id, status, consecutive_failures, created_at,"
            + " updated_at, version) values (?, ?, ?, 'ITEM_UNAVAILABLE', 0, ?, ?, 0)",
        connectionId,
        user.id(),
        integrationId,
        now,
        now);
  }

  @Test
  void expurgaExpiradosEPreservaVigentesComContagensEsperadas() {
    Timestamp now = Timestamp.from(java.time.Instant.now());

    insertLoginAttempt(now, Duration.ofDays(31), true);
    insertLoginAttempt(now, Duration.ofDays(29), false);

    insertSyncRun(now, Duration.ofDays(91), "SUCCESS");
    insertSyncRun(now, Duration.ofDays(89), "EMPTY");

    insertWebhookEvent(now, Duration.ofDays(91), "old-event");
    insertWebhookEvent(now, Duration.ofDays(89), "new-event");

    insertAuditEvent(now, Duration.ofDays(366), "LOGIN_SUCCESS");
    insertAuditEvent(now, Duration.ofDays(364), "LOGOUT");

    insertIdempotencyKey(
        Timestamp.from(now.toInstant().minus(Duration.ofDays(2))),
        Timestamp.from(now.toInstant().minus(Duration.ofHours(1))),
        "old-key-001");
    insertIdempotencyKey(
        now, Timestamp.from(now.toInstant().plus(Duration.ofHours(23))), "new-key-002");

    RetentionJob.RetentionResult result = retentionJob.run();

    assertThat(result.loginAttempts()).isEqualTo(1);
    assertThat(result.syncRuns()).isEqualTo(1);
    assertThat(result.webhookEvents()).isEqualTo(1);
    assertThat(result.auditEvents()).isEqualTo(1);
    assertThat(result.idempotencyKeys()).isEqualTo(1);

    assertThat(count("login_attempt")).isEqualTo(1);
    assertThat(count("sync_run")).isEqualTo(1);
    assertThat(count("webhook_event")).isEqualTo(1);
    assertThat(count("audit_event")).isEqualTo(1);
    assertThat(count("idempotency_key")).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from login_attempt where attempted_at < ?",
                Integer.class,
                Timestamp.from(java.time.Instant.now().minus(Duration.ofDays(30)))))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_event where created_at < ?",
                Integer.class,
                Timestamp.from(java.time.Instant.now().minus(Duration.ofDays(365)))))
        .isZero();
  }

  private void insertLoginAttempt(Timestamp now, Duration age, boolean success) {
    jdbcTemplate.update(
        "insert into login_attempt"
            + " (id, username_hash, ip_hash, attempted_at, success, failure_reason)"
            + " values (?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        new byte[] {1, 2, 3},
        new byte[] {4, 5, 6},
        Timestamp.from(now.toInstant().minus(age)),
        success,
        success ? null : "BAD_CREDENTIALS");
  }

  private void insertSyncRun(Timestamp now, Duration age, String status) {
    Timestamp startedAt = Timestamp.from(now.toInstant().minus(age));
    jdbcTemplate.update(
        "insert into sync_run"
            + " (id, user_id, integration_id, connection_id, status, started_at, finished_at,"
            + " read_count, created_count, updated_count, skipped_count, failed_count,"
            + " created_at, updated_at, version)"
            + " values (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, ?, ?, 0)",
        UUID.randomUUID(),
        user.id(),
        integrationId,
        connectionId,
        status,
        startedAt,
        startedAt,
        startedAt,
        startedAt);
  }

  private void insertWebhookEvent(Timestamp now, Duration age, String eventId) {
    Timestamp receivedAt = Timestamp.from(now.toInstant().minus(age));
    jdbcTemplate.update(
        "insert into webhook_event"
            + " (id, user_id, integration_id, connection_id, provider_event_id, payload_hash,"
            + " status, received_at, created_at, updated_at, version)"
            + " values (?, ?, ?, ?, ?, ?, 'RECEIVED', ?, ?, ?, 0)",
        UUID.randomUUID(),
        user.id(),
        integrationId,
        connectionId,
        eventId,
        new byte[] {7, 8},
        receivedAt,
        receivedAt,
        receivedAt);
  }

  private void insertAuditEvent(Timestamp now, Duration age, String eventType) {
    jdbcTemplate.update(
        "insert into audit_event (id, user_id, event_type, metadata, created_at)"
            + " values (?, ?, ?, '{}'::jsonb, ?)",
        UUID.randomUUID(),
        user.id(),
        eventType,
        Timestamp.from(now.toInstant().minus(age)));
  }

  private void insertIdempotencyKey(Timestamp createdAt, Timestamp expiresAt, String key) {
    jdbcTemplate.update(
        "insert into idempotency_key"
            + " (id, user_id, key, method, path, request_hash, created_at, expires_at)"
            + " values (?, ?, ?, 'POST', '/api/v1/test', ?, ?, ?)",
        UUID.randomUUID(),
        user.id(),
        key,
        new byte[] {9, 10},
        createdAt,
        expiresAt);
  }

  private int count(String table) {
    Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    return count == null ? 0 : count;
  }
}
