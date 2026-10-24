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

/** Troca de itemId preserva IDs locais e não duplica histórico (T073). */
@AutoConfigureMockMvc
class ItemIdChangeIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.itemid";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-itemid";

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
  private String connectionId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-old-0001", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-old-0001",
        new AccountFixture("acc-1", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-1",
        new TransactionFixture("tx-1", "acc-1", "Padaria", "-20.00", "2026-10-20", "POSTED", null));
    PLUGGY.addTransaction(
        "acc-1",
        new TransactionFixture("tx-2", "acc-1", "Mercado", "-30.00", "2026-10-21", "POSTED", null));

    // Após nova autorização o itemId muda, mas contas e transações mantêm os IDs externos.
    PLUGGY.addItem("item-new-0002", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-new-0002",
        new AccountFixture("acc-1", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1200.00", null));
    PLUGGY.addTransaction(
        "acc-1",
        new TransactionFixture("tx-1", "acc-1", "Padaria", "-20.00", "2026-10-20", "POSTED", null));
    PLUGGY.addTransaction(
        "acc-1",
        new TransactionFixture("tx-2", "acc-1", "Mercado", "-30.00", "2026-10-21", "POSTED", null));
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    String body =
        alice
            .post("/api/v1/connections", Map.of("itemId", "item-old-0001"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    connectionId = jsonMapper.readTree(body).path("id").asString();
  }

  @Test
  void trocaDeItemIdMantemConexaoContasETransacoesSemDuplicar() throws Exception {
    String accountBefore = accounts().path("items").get(0).path("id").asString();
    String padariaBefore = transactionId("Padaria");
    PLUGGY.addTransaction(
        "acc-1",
        new TransactionFixture(
            "tx-3", "acc-1", "Farmácia", "-15.00", "2026-10-22", "POSTED", null));

    alice
        .patch("/api/v1/connections/" + connectionId, Map.of("itemId", "item-new-0002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(connectionId))
        .andExpect(jsonPath("$.itemIdMasked").value("item…0002"));

    JsonNode connections = connections();
    assertThat(connections.path("items")).hasSize(1);
    assertThat(connections.path("items").get(0).path("id").asString()).isEqualTo(connectionId);
    assertThat(connections.toString())
        .doesNotContain("item-new-0002")
        .doesNotContain("item-old-0001");

    JsonNode accounts = accounts();
    assertThat(accounts.path("items")).hasSize(1);
    assertThat(accounts.path("items").get(0).path("id").asString()).isEqualTo(accountBefore);

    JsonNode transactions = transactions();
    assertThat(transactions.path("items")).hasSize(3);
    assertThat(transactionId("Padaria")).isEqualTo(padariaBefore);
    assertThat(transactions.toString()).contains("Farmácia");

    // PATCH repetido e sincronização subsequente continuam idempotentes.
    alice
        .patch("/api/v1/connections/" + connectionId, Map.of("itemId", "item-new-0002"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "corr");
    assertThat(transactions().path("items")).hasSize(3);
  }

  @Test
  void itemInvalidoNaoAlteraAConfiguracaoAnterior() throws Exception {
    String before = connections().path("items").get(0).path("itemIdMasked").asString();

    alice
        .patch("/api/v1/connections/" + connectionId, Map.of("itemId", "item-inexistente"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("itemId"));

    JsonNode connections = connections();
    assertThat(connections.path("items")).hasSize(1);
    assertThat(connections.path("items").get(0).path("itemIdMasked").asString()).isEqualTo(before);
    assertThat(transactions().path("items")).hasSize(2);
  }

  private JsonNode connections() throws Exception {
    return json(
        alice
            .get("/api/v1/connections")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode accounts() throws Exception {
    return json(
        alice
            .get("/api/v1/accounts")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
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

  private String transactionId(String description) throws Exception {
    for (JsonNode item : transactions().path("items")) {
      if (description.equals(item.path("description").asString())) {
        return item.path("id").asString();
      }
    }
    return "";
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
