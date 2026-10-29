package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.time.Instant;
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

/**
 * Ciclo de vida da conta (T140).
 *
 * <p>Cobre desconexão com preservação de dados, interrupção de jobs, revogação de sessões, exclusão
 * de conta, tombstone, remoção dos dados ativos e isolamento entre dois usuários.
 */
@AutoConfigureMockMvc
class AccountLifecycleIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.lifecycle";
  private static final String BOB = "bob.lifecycle";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-lifecycle";
  private static final String BOB_CLIENT = "client-bob-lifecycle";

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
  @Autowired private PluggyRateLimiter rateLimiter;

  private AuthTestClient alice;
  private AuthTestClient bob;
  private UUID aliceId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(ALICE_CLIENT, "secret-alice-lifecycle");
    PLUGGY.registerClient(BOB_CLIENT, "secret-bob-lifecycle");
    PLUGGY.addItem("item-alice-lifecycle", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice-lifecycle",
        new AccountFixture(
            "acc-alice-lifecycle", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-alice-lifecycle",
        new TransactionFixture(
            "tx-alice-lifecycle",
            "acc-alice-lifecycle",
            "Mercado Alice",
            "-100.00",
            "2026-10-05",
            "POSTED",
            "Mercado"));
    PLUGGY.addItem("item-bob-lifecycle", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-bob-lifecycle",
        new AccountFixture(
            "acc-bob-lifecycle", "BANK", "CHECKING_ACCOUNT", "Conta Beta", "500.00", null));
    PLUGGY.addTransaction(
        "acc-bob-lifecycle",
        new TransactionFixture(
            "tx-bob-lifecycle",
            "acc-bob-lifecycle",
            "Mercado Bob",
            "-50.00",
            "2026-10-05",
            "POSTED",
            "Mercado"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", ALICE_CLIENT, "clientSecret", "secret-alice-lifecycle"))
        .andExpect(status().isOk());
    bob.put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", BOB_CLIENT, "clientSecret", "secret-bob-lifecycle"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-lifecycle"))
        .andExpect(status().isCreated());
    bob.post("/api/v1/connections", Map.of("itemId", "item-bob-lifecycle"))
        .andExpect(status().isCreated());

    createManualAccount(alice, "Carteira Alice");
    createManualAccount(bob, "Carteira Bob");
    aliceId =
        UUID.fromString(
            json(alice
                    .get("/api/v1/auth/me")
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
                .path("id")
                .asString());
  }

  @Test
  void desconexaoPreservaHistoricoInterrompeJobsEAuditaSemSegredos() throws Exception {
    String connectionId = firstConnectionId(alice);
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    assertThat(activeJobs(aliceId)).isGreaterThanOrEqualTo(1);

    alice
        .delete("/api/v1/integrations/pluggy?deleteImportedData=false")
        .andExpect(status().isNoContent());

    JsonNode integration =
        json(
            alice
                .get("/api/v1/integrations/pluggy")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(integration.path("status").asString()).isEqualTo("INCOMPLETE");
    assertThat(integration.path("clientIdMasked").isNull()).isTrue();

    JsonNode connections =
        json(
            alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(connections.path("items").get(0).path("status").asString())
        .isEqualTo("ITEM_UNAVAILABLE");

    assertThat(accounts(alice).path("items")).hasSize(2);
    assertThat(transactions(alice).path("items")).hasSize(1);
    assertThat(activeJobs(aliceId)).isZero();

    String metadata =
        jdbcTemplate.queryForObject(
            "select metadata::text from audit_event where event_type = 'DISCONNECTED'",
            String.class);
    assertThat(metadata).contains("deleteImportedData").doesNotContain("secret-alice-lifecycle");

    assertThat(accounts(bob).path("items")).hasSize(2);
    bob.get("/api/v1/auth/me").andExpect(status().isOk());
  }

  @Test
  void exclusaoConfirmaSenhaELiberaAcessoComTombstoneSemAfetarOutroUsuario() throws Exception {
    alice
        .post("/api/v1/me/delete", Map.of("password", "senha errada", "confirmation", "EXCLUIR"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("password"));

    alice
        .post("/api/v1/me/delete", Map.of("password", PASSWORD, "confirmation", "APAGAR"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("confirmation"));

    createSecondaryModuleData();

    alice
        .post("/api/v1/me/delete", Map.of("password", PASSWORD, "confirmation", "EXCLUIR"))
        .andExpect(status().isAccepted());

    alice.get("/api/v1/auth/me").andExpect(status().isUnauthorized());

    assertThat(count("financial_account")).isZero();
    assertThat(count("transaction")).isZero();
    assertThat(count("category")).isZero();
    assertThat(count("pluggy_integration")).isZero();
    assertThat(count("pluggy_connection")).isZero();
    assertThat(count("sync_job")).isZero();
    assertThat(count("sync_run")).isZero();
    assertThat(count("manual_asset_liability")).isZero();
    assertThat(count("financial_goal")).isZero();
    assertThat(count("goal_allocation")).isZero();
    assertThat(count("goal_progress_snapshot")).isZero();

    Map<String, Object> deletedUser =
        jdbcTemplate.queryForMap("select status, deleted_at from app_user where id = ?", aliceId);
    assertThat(deletedUser.get("status")).isEqualTo("DELETED");
    assertThat(deletedUser.get("deleted_at")).isNotNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_event where user_id = ? and event_type in"
                    + " ('ACCOUNT_DELETION_REQUESTED', 'ACCOUNT_DELETED')",
                Integer.class,
                aliceId))
        .isEqualTo(2);

    assertThat(accounts(bob).path("items")).hasSize(2);
    assertThat(transactions(bob).path("items")).hasSize(1);
    bob.get("/api/v1/auth/me").andExpect(status().isOk());

    AuthTestClient recreated = new AuthTestClient(mockMvc, jsonMapper).csrf();
    recreated
        .post("/api/v1/auth/register", Map.of("username", ALICE, "password", PASSWORD))
        .andExpect(status().isConflict());
  }

  private void createSecondaryModuleData() throws Exception {
    alice
        .post(
            "/api/v1/wealth/manual-items",
            Map.of(
                "kind", "ASSET",
                "type", "IMOVEL",
                "description", "Apartamento Alice",
                "referenceValue", "100000.00",
                "referenceDate", "2026-10-01"))
        .andExpect(status().isCreated());

    Map<String, Object> allocation =
        Map.of(
            "sourceType", "MANUAL",
            "allocationMode", "FIXED_AMOUNT",
            "allocatedAmount", "100.00");
    Map<String, Object> goal =
        Map.of(
            "name", "Meta Alice",
            "type", "TRAVEL",
            "targetAmount", "1000.00",
            "progressRule", "ALLOCATIONS",
            "allocations", java.util.List.of(allocation));
    alice.post("/api/v1/goals", goal).andExpect(status().isCreated());

    assertThat(count("manual_asset_liability")).isEqualTo(1);
    assertThat(count("financial_goal")).isEqualTo(1);
    assertThat(count("goal_allocation")).isEqualTo(1);
    assertThat(count("goal_progress_snapshot")).isGreaterThanOrEqualTo(1);
  }

  private void createManualAccount(AuthTestClient client, String alias) throws Exception {
    client
        .post(
            "/api/v1/accounts",
            Map.of(
                "type",
                "CASH",
                "alias",
                alias,
                "referenceBalance",
                "100.00",
                "referenceDate",
                Instant.parse("2026-10-01T12:00:00Z").toString()))
        .andExpect(status().isCreated());
  }

  private String firstConnectionId(AuthTestClient client) throws Exception {
    return json(client
            .get("/api/v1/connections")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("items")
        .get(0)
        .path("id")
        .asString();
  }

  private long activeJobs(UUID userId) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from sync_job where user_id = ? and status in"
                + " ('QUEUED', 'RUNNING', 'RETRY')",
            Long.class,
            userId);
    return count == null ? 0 : count;
  }

  private int count(String table) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where user_id = ?", Integer.class, aliceId);
    return count == null ? 0 : count;
  }

  private JsonNode accounts(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/accounts")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode transactions(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/transactions?limit=100")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
