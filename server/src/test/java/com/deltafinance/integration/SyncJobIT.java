package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.connections.domain.SyncJobType;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** Fila idempotente, claim concorrente e isolamento de falhas entre usuários (T071). */
@AutoConfigureMockMvc
class SyncJobIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.fila";
  private static final String BOB = "bob.fila";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-fila";
  private static final String BOB_CLIENT = "client-bob-fila";

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
  @Autowired private PluggyRateLimiter rateLimiter;

  private AuthTestClient alice;
  private AuthTestClient bob;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(ALICE_CLIENT, "secret-alice");
    PLUGGY.registerClient(BOB_CLIENT, "secret-bob");
    PLUGGY.addItem("item-alice", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-alice", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-1", "acc-alice", "Padaria", "-20.00", "2026-10-01", "POSTED", null));
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-2", "acc-alice", "Mercado", "-30.00", "2026-10-02", "POSTED", null));
    PLUGGY.addItem("item-bob", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-bob",
        new AccountFixture("acc-bob", "BANK", "CHECKING_ACCOUNT", "Conta Beta", "500.00", null));
    PLUGGY.addTransaction(
        "acc-bob",
        new TransactionFixture("bob-1", "acc-bob", "Café", "-10.00", "2026-10-03", "POSTED", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
  }

  @Test
  void enfileiramentoRepetidoDevolveMesmoJobEProcessaUmaVez() throws Exception {
    String connectionId = configure(alice, ALICE_CLIENT, "secret-alice", "item-alice");

    String first = syncAccepted(alice, connectionId);
    String second = syncAccepted(alice, connectionId);
    assertThat(second).isEqualTo(first);

    assertThat(syncJobService.processNext("worker-1", "corr-1")).isPresent();
    assertThat(syncJobService.processNext("worker-1", "corr-1")).isEmpty();

    assertThat(transactions(alice).path("items").size()).isEqualTo(2);

    // Depois de concluído, uma nova consulta cria um novo job e continua idempotente nos dados.
    String third = syncAccepted(alice, connectionId);
    assertThat(third).isNotEqualTo(first);
    assertThat(syncJobService.processNext("worker-1", "corr-2")).isPresent();
    assertThat(transactions(alice).path("items").size()).isEqualTo(2);
    assertThat(countRuns()).isEqualTo(2);
  }

  @Test
  void doisWorkersNaoProcessamOMesmoJob() throws Exception {
    String connectionId = configure(alice, ALICE_CLIENT, "secret-alice", "item-alice");
    syncAccepted(alice, connectionId);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Optional<UUID>>> tasks =
          List.of(
              () -> syncJobService.processNext("worker-a", "corr-a"),
              () -> syncJobService.processNext("worker-b", "corr-b"));
      List<Future<Optional<UUID>>> futures = executor.invokeAll(tasks);
      List<Optional<UUID>> results = new ArrayList<>();
      for (Future<Optional<UUID>> future : futures) {
        results.add(future.get());
      }
      assertThat(results.stream().filter(Optional::isPresent)).hasSize(1);
    } finally {
      executor.shutdownNow();
    }

    assertThat(transactions(alice).path("items").size()).isEqualTo(2);
    assertThat(countRuns()).isEqualTo(1);
  }

  @Test
  void falhaDeUmaConexaoNaoBloqueiaAsDemais() throws Exception {
    String aliceConnection = configure(alice, ALICE_CLIENT, "secret-alice", "item-alice");
    String bobConnection = configure(bob, BOB_CLIENT, "secret-bob", "item-bob");
    PLUGGY.setItemStatus("item-alice", "LOGIN_ERROR");
    var aliceJob =
        syncJobService.enqueueConnection(
            userId(ALICE), UUID.fromString(aliceConnection), SyncJobType.SYNC_CONNECTION, null);
    var bobJob =
        syncJobService.enqueueConnection(
            userId(BOB), UUID.fromString(bobConnection), SyncJobType.SYNC_CONNECTION, null);
    assertThat(aliceJob.getId()).isNotEqualTo(bobJob.getId());

    int processed = syncJobService.runDue("worker-1", "corr-1", 10);
    assertThat(processed).isEqualTo(2);

    JsonNode aliceRuns = syncRuns(alice);
    JsonNode bobRuns = syncRuns(bob);
    assertThat(aliceRuns.path("items").get(0).path("status").asString()).isEqualTo("AUTH_ERROR");
    assertThat(bobRuns.path("items").get(0).path("status").asString()).isEqualTo("SUCCESS");
    assertThat(transactions(bob).path("items").size()).isEqualTo(1);
    // O histórico já importado de Alice permanece acessível apesar da falha de autorização.
    assertThat(transactions(alice).path("items").size()).isEqualTo(2);
  }

  @Test
  void rateLimitReagendaSemPerderDados() throws Exception {
    String connectionId = configure(alice, ALICE_CLIENT, "secret-alice", "item-alice");
    syncAccepted(alice, connectionId);
    PLUGGY.failPath("/items/", 429, 1);

    assertThat(syncJobService.processNext("worker-1", "corr-rate")).isPresent();
    JsonNode runs = syncRuns(alice);
    assertThat(runs.path("items").get(0).path("status").asString()).isEqualTo("RATE_LIMITED");
    // Job permanece ativo para retentativa; dados anteriores continuam disponíveis.
    assertThat(syncJobService.pendingCount(userId(ALICE))).isEqualTo(1);
  }

  private String configure(AuthTestClient client, String clientId, String secret, String itemId)
      throws Exception {
    client
        .put("/api/v1/integrations/pluggy", Map.of("clientId", clientId, "clientSecret", secret))
        .andExpect(status().isOk());
    String body =
        client
            .post("/api/v1/connections", Map.of("itemId", itemId))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(body).path("id").asString();
  }

  private String syncAccepted(AuthTestClient client, String connectionId) throws Exception {
    String body =
        client
            .post("/api/v1/connections/" + connectionId + "/sync", null)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(body).path("jobId").asString();
  }

  private JsonNode transactions(AuthTestClient client) throws Exception {
    return jsonMapper.readTree(
        client
            .get("/api/v1/transactions")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode syncRuns(AuthTestClient client) throws Exception {
    return jsonMapper.readTree(
        client
            .get("/api/v1/sync/runs")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private long countRuns() {
    Long value = jdbcTemplate.queryForObject("select count(*) from sync_run", Long.class);
    return value == null ? 0 : value;
  }

  private UUID userId(String username) {
    return jdbcTemplate.queryForObject(
        "select id from app_user where username_normalized = ?", UUID.class, username);
  }
}
