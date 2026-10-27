package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.connections.config.SyncProperties;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cancelamento, timeout e recuperação de jobs de sincronização (C14, ADR-0004). */
@AutoConfigureMockMvc
class SyncCancellationIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.cancelamento";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-cancelamento";

  @DynamicPropertySource
  static void pluggyProperties(DynamicPropertyRegistry registry) {
    registry.add("delta.pluggy.base-url", PLUGGY::baseUrl);
  }

  @AfterAll
  static void stopStub() {
    PLUGGY.stop();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private SyncJobService syncJobService;
  @Autowired private SyncProperties syncProperties;
  @Autowired private PluggyRateLimiter rateLimiter;

  private AuthTestClient alice;
  private String connectionId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-cancel", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-cancel",
        new AccountFixture("acc-cancel", "BANK", "CHECKING_ACCOUNT", "Conta", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-cancel",
        new TransactionFixture(
            "tx-cancel-1", "acc-cancel", "Padaria", "-20.00", "2026-10-20", "POSTED", null));
    PLUGGY.addTransaction(
        "acc-cancel",
        new TransactionFixture(
            "tx-cancel-2", "acc-cancel", "Mercado", "-30.00", "2026-10-21", "POSTED", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    connectionId =
        json(alice
                .post("/api/v1/connections", Map.of("itemId", "item-cancel"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("id")
            .asString();
  }

  @Test
  void remocaoDaConexaoCancelaEscritasAntesDoProximoLote() throws Exception {
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    PLUGGY.blockPath("/v2/transactions");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> future =
          executor.submit(() -> syncJobService.processNext("worker", "cancel-remove"));
      assertThat(PLUGGY.awaitBlocked(10)).isTrue();

      alice.delete("/api/v1/connections/" + connectionId).andExpect(status().isNoContent());
      PLUGGY.releaseBlocked();
      future.get(15, TimeUnit.SECONDS);
    } finally {
      PLUGGY.releaseBlocked();
      executor.shutdownNow();
    }

    assertThat(transactions().path("items")).hasSize(2);
    assertThat(syncJobService.pendingCount(userId())).isZero();
    assertThat(connectionStatus()).isEqualTo("ITEM_UNAVAILABLE");
    JsonNode latest = syncRuns().path("items").get(0);
    assertThat(latest.path("status").asString()).isEqualTo("FAILED");
    assertThat(latest.path("errorCode").asString()).isEqualTo("SYNC_CANCELLED");
  }

  @Test
  void desconexaoDaIntegracaoCancelaJobEmAndamento() throws Exception {
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    PLUGGY.blockPath("/v2/transactions");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> future =
          executor.submit(() -> syncJobService.processNext("worker", "cancel-disconnect"));
      assertThat(PLUGGY.awaitBlocked(10)).isTrue();

      alice.delete("/api/v1/integrations/pluggy").andExpect(status().isNoContent());
      PLUGGY.releaseBlocked();
      future.get(15, TimeUnit.SECONDS);
    } finally {
      PLUGGY.releaseBlocked();
      executor.shutdownNow();
    }

    assertThat(transactions().path("items")).hasSize(2);
    assertThat(syncJobService.pendingCount(userId())).isZero();
    assertThat(connectionStatus()).isEqualTo("ITEM_UNAVAILABLE");
  }

  @Test
  void timeoutLiberaJobEConexaoSemDuplicarDados() throws Exception {
    Duration previous = syncProperties.getJobTimeout();
    syncProperties.setJobTimeout(Duration.ofMillis(300));
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    PLUGGY.blockPath("/v2/transactions");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> future =
          executor.submit(() -> syncJobService.processNext("worker", "cancel-timeout"));
      assertThat(PLUGGY.awaitBlocked(10)).isTrue();

      awaitJobNotRunning(Duration.ofSeconds(5));
      String status = jobStatus();
      assertThat(status).isIn("RETRY", "FAILED");
      assertThat(connectionStatus()).isNotEqualTo("SYNCING");

      PLUGGY.releaseBlocked();
      future.get(15, TimeUnit.SECONDS);
    } finally {
      PLUGGY.releaseBlocked();
      executor.shutdownNow();
      syncProperties.setJobTimeout(previous);
    }

    assertThat(transactions().path("items")).hasSize(2);
    JsonNode latest = syncRuns().path("items").get(0);
    assertThat(latest.path("status").asString()).isEqualTo("TIMEOUT");
    assertThat(latest.path("errorCode").asString()).isEqualTo("JOB_TIMEOUT");
    assertThat(connectionStatus()).isNotEqualTo("SYNCING");
  }

  @Test
  void jobOrfaoEhRecuperadoNoStartComSyncRunELiberacaoDeSyncing() throws Exception {
    UUID userId = userId();
    UUID integrationId =
        jdbcTemplate.queryForObject(
            "select integration_id from pluggy_connection where id = ?",
            UUID.class,
            UUID.fromString(connectionId));
    UUID jobId = UUID.randomUUID();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into sync_job (id, user_id, integration_id, connection_id, job_type, status,
                              run_after, attempts, max_attempts, locked_by, locked_at,
                              created_at, updated_at, version)
        values (?, ?, ?, ?, 'SYNC_CONNECTION', 'RUNNING', ?, 1, 5, 'worker-antigo', ?,
                ?, ?, 0)
        """,
        jobId,
        userId,
        integrationId,
        UUID.fromString(connectionId),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now));
    jdbcTemplate.update(
        "update pluggy_connection set status = 'SYNCING' where id = ?",
        UUID.fromString(connectionId));
    jdbcTemplate.update(
        "update pluggy_integration set status = 'SYNCING' where id = ?", integrationId);

    assertThat(syncJobService.recoverOrphanedJobs()).isGreaterThanOrEqualTo(1);

    assertThat(jobStatus()).isEqualTo("RETRY");
    assertThat(
            jdbcTemplate.queryForObject(
                "select last_error_code from sync_job where id = ?", String.class, jobId))
        .isEqualTo("INTERRUPTED");
    assertThat(connectionStatus()).isNotEqualTo("SYNCING");
    JsonNode latest = syncRuns().path("items").get(0);
    assertThat(latest.path("status").asString()).isEqualTo("FAILED");
    assertThat(latest.path("errorCode").asString()).isEqualTo("INTERRUPTED");
  }

  private void awaitJobNotRunning(Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (!"RUNNING".equals(jobStatus())) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("Job continuou RUNNING após o timeout.");
  }

  private String jobStatus() {
    return jdbcTemplate.queryForObject(
        "select status from sync_job where connection_id = ? order by created_at desc limit 1",
        String.class,
        UUID.fromString(connectionId));
  }

  private String connectionStatus() throws Exception {
    return json(alice
            .get("/api/v1/connections")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("items")
        .get(0)
        .path("status")
        .asString();
  }

  private JsonNode transactions() throws Exception {
    return json(
        alice
            .get("/api/v1/transactions")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode syncRuns() throws Exception {
    return json(
        alice
            .get("/api/v1/sync/runs?limit=20")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private UUID userId() {
    return jdbcTemplate.queryForObject(
        "select id from app_user where username_normalized = ?", UUID.class, ALICE);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
