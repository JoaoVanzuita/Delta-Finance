package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PostgresTestBase;
import java.time.Instant;
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

/** Item com consentimento expirado não vira conexão ativa e registra sync_run. */
@AutoConfigureMockMvc
class ConnectionStateIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.connection.state";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-connection-state";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
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

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-expired", "Banco Alfa", "UPDATED");
    PLUGGY.setItemConsentExpiresAt("item-expired", Instant.now().minusSeconds(3600));
    PLUGGY.addAccount(
        "item-expired",
        new AccountFixture(
            "acc-expired", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "100.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
  }

  @Test
  void consentimentoExpiradoSalvaEstadoDeErroENaoImportaContas() throws Exception {
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-expired"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("AUTHORIZATION_EXPIRED"))
        .andExpect(jsonPath("$.lastErrorCode").value("CONSENT_EXPIRED"));

    alice
        .get("/api/v1/integrations/pluggy")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AUTHORIZATION_EXPIRED"))
        .andExpect(jsonPath("$.lastErrorCode").value("CONSENT_EXPIRED"));

    JsonNode accounts =
        json(
            alice
                .get("/api/v1/accounts")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(accounts.path("items")).isEmpty();

    JsonNode runs =
        json(
            alice
                .get("/api/v1/sync/runs?limit=20")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(runs.path("items")).hasSize(1);
    assertThat(runs.path("items").get(0).path("status").asString()).isEqualTo("AUTH_ERROR");
    assertThat(runs.path("items").get(0).path("errorCode").asString()).isEqualTo("CONSENT_EXPIRED");
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
