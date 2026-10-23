package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.integrations.infrastructure.ApiKeyCache;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** Segredos write-only, ausência de Connect Token e isolamento do cache de API Key. */
@AutoConfigureMockMvc
class SecretHandlingIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.familia";
  private static final String BOB = "bob.familia";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice";
  private static final String ALICE_SECRET = "secret-alice";
  private static final String BOB_CLIENT = "client-bob";
  private static final String BOB_SECRET = "secret-bob";

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
  @Autowired private ApiKeyCache apiKeyCache;
  @Autowired private PluggyIntegrationRepository integrationRepository;

  private AuthTestClient alice;
  private AuthTestClient bob;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    PLUGGY.registerClient(ALICE_CLIENT, ALICE_SECRET);
    PLUGGY.registerClient(BOB_CLIENT, BOB_SECRET);
    PLUGGY.addItem("item-alice", "Banco Alfa", "UPDATED");
    PLUGGY.addItem("item-bob", "Banco Gama", "UPDATED");
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-a", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "100.00", null));
    PLUGGY.addAccount(
        "item-bob",
        new AccountFixture("acc-b", "BANK", "CHECKING_ACCOUNT", "Conta Gama", "50.00", null));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-a", "acc-a", "Padaria", "-10.00", "2026-10-20", "POSTED", "Alimentação"));
    PLUGGY.addTransaction(
        "acc-b",
        new TransactionFixture(
            "tx-b", "acc-b", "Café", "-7.00", "2026-10-20", "POSTED", "Alimentação"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
  }

  @Test
  void respostasNaoExpoemSecretApiKeyOuConnectToken() throws Exception {
    configure(alice, ALICE_CLIENT, ALICE_SECRET);
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice"))
        .andExpect(status().isCreated());

    String integration =
        body(alice.get("/api/v1/integrations/pluggy").andExpect(status().isOk()).andReturn());
    String connections =
        body(alice.get("/api/v1/connections").andExpect(status().isOk()).andReturn());
    String accounts = body(alice.get("/api/v1/accounts").andExpect(status().isOk()).andReturn());
    String transactions =
        body(alice.get("/api/v1/transactions").andExpect(status().isOk()).andReturn());

    for (String payload : new String[] {integration, connections, accounts, transactions}) {
      assertThat(payload)
          .doesNotContain(ALICE_SECRET)
          .doesNotContain("apiKey")
          .doesNotContain("key-" + ALICE_CLIENT)
          .doesNotContain("connect_token")
          .doesNotContain("connectToken");
    }
    assertThat(connections).doesNotContain("item-alice");
    assertThat(PLUGGY.requests())
        .noneMatch(
            request -> request.getPath() != null && request.getPath().contains("connect_token"));
  }

  @Test
  void cacheDeApiKeyEhIsoladoPorUsuarioEIntegracao() throws Exception {
    configure(alice, ALICE_CLIENT, ALICE_SECRET);
    configure(bob, BOB_CLIENT, BOB_SECRET);
    alice.post("/api/v1/connections", Map.of("itemId", "item-alice"));
    bob.post("/api/v1/connections", Map.of("itemId", "item-bob"));

    UUID aliceId = userId(ALICE);
    UUID bobId = userId(BOB);
    UUID aliceIntegration = integrationRepository.findMeuPluggy(aliceId).orElseThrow().getId();
    UUID bobIntegration = integrationRepository.findMeuPluggy(bobId).orElseThrow().getId();

    assertThat(apiKeyCache.get(aliceId, aliceIntegration)).contains("key-" + ALICE_CLIENT);
    assertThat(apiKeyCache.get(bobId, bobIntegration)).contains("key-" + BOB_CLIENT);
    assertThat(apiKeyCache.get(bobId, aliceIntegration)).isEmpty();
    assertThat(apiKeyCache.get(aliceId, bobIntegration)).isEmpty();
  }

  @Test
  void atualizacaoInvalidaMantemConfiguracaoAnteriorEAcessoAosDados() throws Exception {
    configure(alice, ALICE_CLIENT, ALICE_SECRET);
    alice.post("/api/v1/connections", Map.of("itemId", "item-alice"));

    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", "client-invalido", "clientSecret", "segredo-invalido"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_FAILED"));

    alice
        .get("/api/v1/integrations/pluggy")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientIdMasked").value("clie…lice"));
    alice
        .get("/api/v1/accounts")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void apiKeyNaoEhPersistidaNoBanco() throws Exception {
    configure(alice, ALICE_CLIENT, ALICE_SECRET);
    alice.post("/api/v1/connections", Map.of("itemId", "item-alice"));

    Integer matches =
        jdbcTemplate.queryForObject(
            "select count(*) from pluggy_integration where encode(client_secret_enc, 'escape') like ?",
            Integer.class,
            "%key-%");
    assertThat(matches).isZero();
  }

  private void configure(AuthTestClient client, String clientId, String secret) throws Exception {
    client
        .put("/api/v1/integrations/pluggy", Map.of("clientId", clientId, "clientSecret", secret))
        .andExpect(status().isOk());
  }

  private String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private UUID userId(String username) {
    return jdbcTemplate.queryForObject(
        "select id from app_user where username_normalized = ?", UUID.class, username);
  }
}
