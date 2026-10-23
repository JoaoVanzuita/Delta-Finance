package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
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

/** Ciclo de vida da API Key e distinção autorização × consentimento (T194). */
@AutoConfigureMockMvc
class CredentialLifecycleIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.credenciais";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-credenciais";

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
    PLUGGY.addItem("item-alice", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-alice", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-1", "acc-alice", "Padaria", "-20.00", "2026-10-20", "POSTED", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
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
    connectionId = jsonMapper.readTree(body).path("id").asString();
  }

  @Test
  void apiKeyExpiradaERenovadaNoServidorSemExporAChave() throws Exception {
    PLUGGY.rotateKeys(true);
    PLUGGY.invalidateApiKeys();

    syncAndProcess();
    JsonNode run = syncRuns().path("items").get(0);
    assertThat(run.path("status").asString()).isEqualTo("SUCCESS");
    assertThat(PLUGGY.authCalls()).isGreaterThanOrEqualTo(2);
    assertThat(PLUGGY.dataApiKeys()).hasSizeGreaterThanOrEqualTo(2);

    String statusBody =
        alice.get("/api/v1/sync/status").andReturn().getResponse().getContentAsString();
    assertThat(statusBody).doesNotContain("key-" + CLIENT).doesNotContain("apiKey");
    assertThat(transactions().path("items")).hasSize(1);
  }

  @Test
  void autorizacaoDaAplicacaoEDistintaDoConsentimentoBancario() throws Exception {
    syncAndProcess();

    PLUGGY.setItemErrorCode("item-alice", "USER_AUTHORIZATION_EXPIRED");
    syncAndProcess();
    JsonNode applicationRun = syncRuns().path("items").get(0);
    assertThat(applicationRun.path("status").asString()).isEqualTo("AUTH_ERROR");
    assertThat(applicationRun.path("errorCode").asString()).isEqualTo("USER_AUTHORIZATION_EXPIRED");
    assertThat(transactions().path("items")).hasSize(1);

    PLUGGY.setItemErrorCode("item-alice", null);
    PLUGGY.setItemConsentExpiresAt("item-alice", Instant.now().minusSeconds(3600));
    syncAndProcess();
    JsonNode consentRun = syncRuns().path("items").get(0);
    assertThat(consentRun.path("status").asString()).isEqualTo("AUTH_ERROR");
    assertThat(consentRun.path("errorCode").asString()).isEqualTo("CONSENT_EXPIRED");
    assertThat(transactions().path("items")).hasSize(1);

    JsonNode connections = connections();
    assertThat(connections.path("items").get(0).path("status").asString())
        .isEqualTo("AUTHORIZATION_EXPIRED");
    assertThat(connections.path("items").get(0).path("lastErrorCode").asString())
        .isEqualTo("CONSENT_EXPIRED");
    assertThat(connections.path("items").get(0).path("consentExpiresAt").asString()).isNotBlank();
  }

  private void syncAndProcess() throws Exception {
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "corr");
  }

  private JsonNode syncRuns() throws Exception {
    return json(
        alice
            .get("/api/v1/sync/runs")
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

  private JsonNode connections() throws Exception {
    return json(
        alice
            .get("/api/v1/connections")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
