package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.util.List;
import java.util.Map;
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

/** Correções do usuário prevalecem sobre sincronizações posteriores (T105). */
@AutoConfigureMockMvc
class TransactionCorrectionIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.correcao";
  private static final String BOB = "bob.correcao";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-correcao";
  private static final String BOB_CLIENT = "client-bob-correcao";

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
  private String connectionId;
  private String transactionId;

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
            "tx-1", "acc-alice", "Mercado", "-100.00", "2026-10-20", "POSTED", "Mercado"));
    PLUGGY.addItem("item-bob", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-bob",
        new AccountFixture("acc-bob", "BANK", "CHECKING_ACCOUNT", "Conta Beta", "500.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", ALICE_CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    bob.put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", BOB_CLIENT, "clientSecret", "secret-bob"))
        .andExpect(status().isOk());
    String connectionBody =
        alice
            .post("/api/v1/connections", Map.of("itemId", "item-alice"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    connectionId = jsonMapper.readTree(connectionBody).path("id").asString();
    bob.post("/api/v1/connections", Map.of("itemId", "item-bob")).andExpect(status().isCreated());

    transactionId = transactions(alice).path("items").get(0).path("id").asString();
  }

  @Test
  void correcaoPersisteAposSincronizacaoEPreservaValoresDaOrigem() throws Exception {
    String categoryId = createCategory("Casa");

    alice
        .patch(
            "/api/v1/transactions/" + transactionId,
            Map.of(
                "description", "Mercado corrigido",
                "amount", "-90.00",
                "postedOn", "2026-10-19",
                "categoryId", categoryId,
                "notes", "Compra revisada"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Mercado corrigido"))
        .andExpect(jsonPath("$.amount").value("-90.00"))
        .andExpect(jsonPath("$.postedOn").value("2026-10-19"))
        .andExpect(jsonPath("$.categoryName").value("Casa"))
        .andExpect(jsonPath("$.notes").value("Compra revisada"))
        .andExpect(jsonPath("$.editedFields").isArray());

    assertThat(
            count(
                "select count(*) from transaction_correction where transaction_id = ?",
                java.util.UUID.fromString(transactionId)))
        .isEqualTo(5);
    assertThat(count("select count(*) from audit_event where event_type = 'TRANSACTION_CORRECTED'"))
        .isEqualTo(1);

    // A origem muda todos os campos corrigidos em uma sincronização posterior.
    PLUGGY.setTransactions(
        "acc-alice",
        List.of(
            new TransactionFixture(
                "tx-1",
                "acc-alice",
                "Mercado origem",
                "-120.00",
                "2026-10-22",
                "POSTED",
                "Outra")));
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "corr");

    JsonNode corrected = transactions(alice).path("items").get(0);
    assertThat(corrected.path("description").asString()).isEqualTo("Mercado corrigido");
    assertThat(corrected.path("amount").asString()).isEqualTo("-90.00");
    assertThat(corrected.path("postedOn").asString()).isEqualTo("2026-10-19");
    assertThat(corrected.path("categoryName").asString()).isEqualTo("Casa");
    assertThat(corrected.path("notes").asString()).isEqualTo("Compra revisada");
    assertThat(corrected.path("editedFields").toString())
        .contains("description", "amount", "postedOn", "category", "notes");

    assertThat(
            count(
                "select count(*) from transaction_correction where transaction_id = ?",
                java.util.UUID.fromString(transactionId)))
        .isEqualTo(5);
    assertThat(
            count(
                "select count(*) from audit_event where event_type = 'TRANSACTION_SOURCE_PRESERVED'"))
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void correcaoSemCamposECruzadaSaoRejeitadas() throws Exception {
    alice
        .patch("/api/v1/transactions/" + transactionId, Map.of())
        .andExpect(status().isUnprocessableEntity());

    bob.patch("/api/v1/transactions/" + transactionId, Map.of("description", "Invasão"))
        .andExpect(status().isNotFound());
    assertThat(transactions(alice).path("items").get(0).path("description").asString())
        .isEqualTo("Mercado");
  }

  private String createCategory(String name) throws Exception {
    String body =
        alice
            .post("/api/v1/categories", Map.of("name", name, "kind", "EXPENSE"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(body).path("id").asString();
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

  private long count(String sql, Object... args) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }
}
