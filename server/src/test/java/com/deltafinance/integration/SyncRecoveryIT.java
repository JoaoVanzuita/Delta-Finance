package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** Falhas, lista vazia, SOURCE_REMOVED/RESTORE e PENDING→POSTED sem duplicação (T072). */
@AutoConfigureMockMvc
class SyncRecoveryIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.recuperacao";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-recuperacao";

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
  @Autowired private PluggyAuthClient authClient;
  @Autowired private PluggyIntegrationRepository integrationRepository;
  @Autowired private PluggyRateLimiter rateLimiter;

  private AuthTestClient alice;
  private String connectionId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-alice", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-alice", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-1", "acc-alice", "Padaria", "-20.00", "2026-10-20", "POSTED", null));
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-2", "acc-alice", "Mercado", "-30.00", "2026-10-21", "POSTED", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    connectionId = configure();
  }

  @Test
  void listaVaziaNaoRemoveHistorico() throws Exception {
    syncAndProcess();
    PLUGGY.setTransactions("acc-alice", List.of());
    syncAndProcess();

    JsonNode runs = syncRuns();
    assertThat(runs.path("items").get(0).path("status").asString()).isEqualTo("EMPTY");
    JsonNode transactions = transactions();
    assertThat(transactions.path("items").size()).isEqualTo(2);
    for (JsonNode item : transactions.path("items")) {
      assertThat(item.path("status").asString()).isEqualTo("POSTED");
    }
  }

  @Test
  void ausenciaConclusivaMarcaSourceRemovedEConfirmaPreservandoLinha() throws Exception {
    syncAndProcess();
    String removedId = transactionIdByDescription("Mercado");

    PLUGGY.removeTransaction("acc-alice", "tx-2");
    syncAndProcess();

    JsonNode transactions = transactions();
    assertThat(transactions.path("items").size()).isEqualTo(2);
    JsonNode removed = findById(transactions, removedId);
    assertThat(removed.path("status").asString()).isEqualTo("SOURCE_REMOVED");

    alice
        .post(
            "/api/v1/transactions/" + removedId + "/source-removal",
            Map.of("action", "CONFIRM_REMOVAL", "reason", "Não reconheço a compra"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SOURCE_REMOVED"));

    // Confirmação não apaga a linha nem duplica o histórico.
    assertThat(transactions().path("items").size()).isEqualTo(2);
  }

  @Test
  void restoreValidaOrigemERetornaAosTotaisSemDuplicar() throws Exception {
    syncAndProcess();
    String removedId = transactionIdByDescription("Mercado");

    PLUGGY.removeTransaction("acc-alice", "tx-2");
    syncAndProcess();
    assertThat(findById(transactions(), removedId).path("status").asString())
        .isEqualTo("SOURCE_REMOVED");

    // Ainda ausente na origem: restauração é recusada.
    alice
        .post("/api/v1/transactions/" + removedId + "/source-removal", Map.of("action", "RESTORE"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SOURCE_REMOVAL_CONFLICT"));

    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-2", "acc-alice", "Mercado", "-30.00", "2026-10-21", "POSTED", null));
    alice
        .post("/api/v1/transactions/" + removedId + "/source-removal", Map.of("action", "RESTORE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("POSTED"));

    JsonNode transactions = transactions();
    assertThat(transactions.path("items").size()).isEqualTo(2);
    assertThat(findById(transactions, removedId).path("status").asString()).isEqualTo("POSTED");
  }

  @Test
  void falhaDoProvedorPreservaDadosERetenta() throws Exception {
    syncAndProcess();
    PLUGGY.failPath("/v2/transactions", 500, 1);

    syncAndProcess();
    JsonNode failed = syncRuns().path("items").get(0);
    assertThat(failed.path("status").asString()).isEqualTo("FAILED");
    assertThat(failed.path("errorCode").asString()).isNotEmpty();
    assertThat(transactions().path("items").size()).isEqualTo(2);

    // Retry posterior recupera sem duplicar nem perder.
    jdbcTemplate.update("update sync_job set run_after = now() where status = 'RETRY'");
    syncAndProcess();
    assertThat(transactions().path("items").size()).isEqualTo(2);
  }

  @Test
  void credenciaisRecusadasNaoApagamHistorico() throws Exception {
    syncAndProcess();
    var integration = integrationRepository.findMeuPluggy(userId()).orElseThrow();
    authClient.invalidate(integration);
    PLUGGY.registerClient(CLIENT, "segredo-trocado");

    syncAndProcess();
    JsonNode run = syncRuns().path("items").get(0);
    assertThat(run.path("status").asString()).isEqualTo("AUTH_ERROR");
    assertThat(run.path("errorCode").asString()).isEqualTo("CREDENTIAL_FAILURE");
    assertThat(transactions().path("items").size()).isEqualTo(2);

    JsonNode status =
        jsonMapper.readTree(
            alice
                .get("/api/v1/sync/status")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(status.path("integrations").get(0).path("status").asString())
        .isEqualTo("CREDENTIAL_FAILURE");
    assertThat(status.path("connections").get(0).path("status").asString())
        .isEqualTo("CREDENTIAL_FAILURE");
  }

  @Test
  void itemInexistenteMarcaItemIndisponivelSemRetentar() throws Exception {
    syncAndProcess();
    PLUGGY.failPath("/items/", 404, 1);

    syncAndProcess();

    JsonNode run = syncRuns().path("items").get(0);
    assertThat(run.path("status").asString()).isEqualTo("FAILED");
    assertThat(run.path("errorCode").asString()).isEqualTo("ITEM_UNAVAILABLE");

    JsonNode status =
        jsonMapper.readTree(
            alice
                .get("/api/v1/sync/status")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(status.path("connections").get(0).path("status").asString())
        .isEqualTo("ITEM_UNAVAILABLE");
    assertThat(syncJobService.pendingCount(userId())).isZero();
    assertThat(transactions().path("items").size()).isEqualTo(2);
  }

  @Test
  void pendingViraPostedNoMesmoRegistro() throws Exception {
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-3", "acc-alice", "Farmácia", "-15.00", "2026-10-22", "PENDING", null));
    syncAndProcess();
    String transactionId = transactionIdByDescription("Farmácia");
    assertThat(transactionId).isNotBlank();

    PLUGGY.updateTransactionStatus("acc-alice", "tx-3", "POSTED");
    syncAndProcess();

    JsonNode transactions = transactions();
    assertThat(transactions.path("items").size()).isEqualTo(3);
    assertThat(findById(transactions, transactionId).path("status").asString()).isEqualTo("POSTED");
  }

  @Test
  void remocaoDeItemInterrompeNovasSincronizacoesEPreservaHistorico() throws Exception {
    alice.delete("/api/v1/connections/" + connectionId).andExpect(status().isNoContent());

    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONNECTION_UNAVAILABLE"));
    assertThat(transactions().path("items").size()).isEqualTo(2);
  }

  private String configure() throws Exception {
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    String body =
        alice
            .post("/api/v1/connections", Map.of("itemId", "item-alice"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(body).path("id").asString();
  }

  private void syncAndProcess() throws Exception {
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    java.util.Optional<UUID> processed = syncJobService.processNext("worker", "corr");
    // Um job em retry pode não estar vencido; nesse caso ele já está agendado.
    assertThat(processed.isPresent() || syncJobService.pendingCount(userId()) > 0).isTrue();
  }

  private JsonNode transactions() throws Exception {
    return jsonMapper.readTree(
        alice
            .get("/api/v1/transactions")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode syncRuns() throws Exception {
    return jsonMapper.readTree(
        alice
            .get("/api/v1/sync/runs")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String transactionIdByDescription(String description) throws Exception {
    for (JsonNode item : transactions().path("items")) {
      if (description.equals(item.path("description").asString())) {
        return item.path("id").asString();
      }
    }
    return "";
  }

  private static JsonNode findById(JsonNode page, String id) {
    for (JsonNode item : page.path("items")) {
      if (id.equals(item.path("id").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + id);
  }

  private UUID userId() {
    return jdbcTemplate.queryForObject(
        "select id from app_user where username_normalized = ?", UUID.class, ALICE);
  }
}
