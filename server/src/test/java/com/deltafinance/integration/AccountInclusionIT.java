package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Inclusão explícita da conta no consolidado (M1).
 *
 * <p>A exclusão afeta apenas dashboard, patrimônio e resumo de contas; a conta continua visível,
 * com histórico e filtros próprios.
 */
@AutoConfigureMockMvc
class AccountInclusionIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.inclusion";
  private static final String BOB = "bob.inclusion";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-inclusion";
  private static final String BOB_CLIENT = "client-bob-inclusion";

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

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(ALICE_CLIENT, "secret-alice");
    PLUGGY.registerClient(BOB_CLIENT, "secret-bob");
    PLUGGY.addItem("item-inclusion", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-inclusion",
        new AccountFixture(
            "acc-inclusion", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));

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
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-inclusion"))
        .andExpect(status().isCreated());
  }

  @Test
  void contaExcluidaSaiDoConsolidadoMasMantemHistoricoEFiltros() throws Exception {
    String imported = accountId("Conta Alfa");
    String manual = createManualAccount("Carteira", "200.00");

    // Padrão: ambas as contas compõem o consolidado.
    assertThat(account(imported).path("includeInTotal").asBoolean()).isTrue();
    assertThat(account(manual).path("includeInTotal").asBoolean()).isTrue();
    assertThat(summary().path("currentBalance").asString()).isEqualTo("1200.00");
    assertThat(wealth().path("grossAssets").asString()).isEqualTo("1200.00");

    JsonNode updated =
        json(
            alice
                .put("/api/v1/accounts/" + manual + "/inclusion", Map.of("includeInTotal", false))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(updated.path("includeInTotal").asBoolean()).isFalse();

    // Consolidado reflete a exclusão em dashboard e patrimônio.
    assertThat(summary().path("currentBalance").asString()).isEqualTo("1000.00");
    assertThat(wealth().path("grossAssets").asString()).isEqualTo("1000.00");
    boolean excludedListed = false;
    for (JsonNode item : wealth().path("items")) {
      if ("Carteira".equals(item.path("description").asString())) {
        excludedListed = true;
        assertThat(item.path("included").asBoolean()).isFalse();
        assertThat(item.path("exclusionReason").asString()).contains("consolidado");
      }
    }
    assertThat(excludedListed).isTrue();

    // A conta continua visível e com saldo próprio.
    assertThat(account(manual).path("currentBalance").asString()).isEqualTo("200.00");
    assertThat(accounts().path("items")).hasSize(2);

    // Reincluir restaura o consolidado.
    alice
        .put("/api/v1/accounts/" + manual + "/inclusion", Map.of("includeInTotal", true))
        .andExpect(status().isOk());
    assertThat(summary().path("currentBalance").asString()).isEqualTo("1200.00");
  }

  @Test
  void exclusaoDeContaImportadaPersisteAposSincronizacao() throws Exception {
    String imported = accountId("Conta Alfa");
    alice
        .put("/api/v1/accounts/" + imported + "/inclusion", Map.of("includeInTotal", false))
        .andExpect(status().isOk());

    alice
        .post("/api/v1/connections/" + connectionId() + "/sync", null)
        .andExpect(status().isAccepted());

    // A sincronização não reescreve a escolha do usuário.
    assertThat(account(imported).path("includeInTotal").asBoolean()).isFalse();
  }

  @Test
  void isolamentoDaInclusaoEntreUsuarios() throws Exception {
    String aliceAccount = accountId("Conta Alfa");
    bob.put("/api/v1/accounts/" + aliceAccount + "/inclusion", Map.of("includeInTotal", false))
        .andExpect(status().isNotFound());
    assertThat(account(aliceAccount).path("includeInTotal").asBoolean()).isTrue();
  }

  private String createManualAccount(String alias, String balance) throws Exception {
    return json(alice
            .post(
                "/api/v1/accounts",
                Map.of(
                    "type",
                    "CASH",
                    "alias",
                    alias,
                    "referenceBalance",
                    balance,
                    "referenceDate",
                    "2026-10-23T12:00:00Z",
                    "currency",
                    "BRL"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
  }

  private String accountId(String alias) throws Exception {
    for (JsonNode account : accounts().path("items")) {
      if (alias.equals(account.path("alias").asString())) {
        return account.path("id").asString();
      }
    }
    throw new AssertionError("Conta não encontrada: " + alias);
  }

  private String connectionId() throws Exception {
    return json(alice
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

  private JsonNode account(String accountId) throws Exception {
    return json(
        alice
            .get("/api/v1/accounts/" + accountId)
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

  private JsonNode summary() throws Exception {
    return json(
        alice
            .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode wealth() throws Exception {
    return json(
        alice
            .get("/api/v1/wealth/overview")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
