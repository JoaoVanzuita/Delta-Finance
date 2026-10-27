package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
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

/** Limite de conexões exposto pelo backend e `connectionStatus` por conta (C15). */
@AutoConfigureMockMvc
class ConnectionLimitIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.limite";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-limite";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("delta.pluggy.base-url", PLUGGY::baseUrl);
    registry.add("delta.pluggy.connection-limit", () -> "1");
  }

  @AfterAll
  static void stopStub() {
    PLUGGY.stop();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private PluggyRateLimiter rateLimiter;

  private AuthTestClient alice;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-limite-a", "Banco Alfa", "UPDATED");
    PLUGGY.addItem("item-limite-b", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-limite-a",
        new AccountFixture("acc-limite-a", "BANK", "CHECKING_ACCOUNT", "Conta A", "100.00", null));
    PLUGGY.addAccount(
        "item-limite-b",
        new AccountFixture("acc-limite-b", "BANK", "CHECKING_ACCOUNT", "Conta B", "200.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
  }

  @Test
  void limiteEDataVemDoBackendEConexaoIndisponivelLiberaVaga() throws Exception {
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INCOMPLETE"))
        .andExpect(jsonPath("$.connectionLimit").value(1))
        .andExpect(jsonPath("$.connectionLimitCheckedAt").isNotEmpty());

    String first = addConnection("item-limite-a");
    JsonNode integration = integration();
    assertThat(integration.path("connectionCount").asInt()).isEqualTo(1);
    assertThat(integration.path("connectionLimit").asInt()).isEqualTo(1);

    JsonNode account = accounts().path("items").get(0);
    assertThat(account.path("status").asString()).isEqualTo("ACTIVE");
    assertThat(account.path("connectionStatus").asString()).isEqualTo("SYNCED");

    alice.delete("/api/v1/connections/" + first).andExpect(status().isNoContent());
    assertThat(integration().path("connectionCount").asInt()).isZero();
    assertThat(integration().path("status").asString()).isEqualTo("INCOMPLETE");

    String second = addConnection("item-limite-b");
    assertThat(second).isNotBlank();
    assertThat(integration().path("connectionCount").asInt()).isEqualTo(1);

    alice
        .post("/api/v1/connections", Map.of("itemId", "item-limite-a"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONNECTION_LIMIT_REACHED"));
  }

  private String addConnection(String itemId) throws Exception {
    return json(alice
            .post("/api/v1/connections", Map.of("itemId", itemId))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
  }

  private JsonNode integration() throws Exception {
    return json(
        alice
            .get("/api/v1/integrations/pluggy")
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

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
