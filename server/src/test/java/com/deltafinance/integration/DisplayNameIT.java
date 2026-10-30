package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
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

/** Nomes de exibição locais sem alterar os nomes originais recebidos do Pluggy. */
@AutoConfigureMockMvc
class DisplayNameIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.display.name";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-display-name";

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
  private String accountId;
  private String cardId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-display-name", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-display-name",
        new AccountFixture(
            "acc-display-name", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addAccount(
        "item-display-name",
        new AccountFixture(
            "acc-card-display", "CREDIT", "CREDIT_CARD", "Cartão Alfa", "0.00", null));
    PLUGGY.addItem("item-display-name-2", "Banco Alfa 2", "UPDATED");
    PLUGGY.addAccount(
        "item-display-name-2",
        new AccountFixture(
            "acc-display-name", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    connectionId =
        json(alice
                .post("/api/v1/connections", Map.of("itemId", "item-display-name"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("id")
            .asString();
    accountId = accountIdByType("BANK");
    cardId = accountIdByType("CREDIT");
  }

  @Test
  void renomeiaContaEConexaoEPreservaNomesOriginaisAposSincronizacao() throws Exception {
    JsonNode renamedAccount =
        put("/api/v1/accounts/" + accountId + "/display-name", "Conta da Casa");
    assertThat(renamedAccount.path("alias").asString()).isEqualTo("Conta da Casa");
    assertThat(renamedAccount.path("providerName").asString()).isEqualTo("Conta Alfa");
    assertThat(renamedAccount.path("nameCustomized").asBoolean()).isTrue();

    JsonNode renamedConnection =
        put("/api/v1/connections/" + connectionId + "/display-name", "Banco da família");
    assertThat(renamedConnection.path("displayName").asString()).isEqualTo("Banco da família");
    assertThat(renamedConnection.path("institutionName").asString()).isEqualTo("Banco Alfa");
    assertThat(renamedConnection.path("nameCustomized").asBoolean()).isTrue();

    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "display-name");

    JsonNode accountAfterSync = json(account());
    assertThat(accountAfterSync.path("alias").asString()).isEqualTo("Conta da Casa");
    assertThat(accountAfterSync.path("providerName").asString()).isEqualTo("Conta Alfa");
    assertThat(accountAfterSync.path("nameCustomized").asBoolean()).isTrue();

    JsonNode connectionAfterSync = connection();
    assertThat(connectionAfterSync.path("displayName").asString()).isEqualTo("Banco da família");
    assertThat(connectionAfterSync.path("institutionName").asString()).isEqualTo("Banco Alfa");
    assertThat(connectionAfterSync.path("nameCustomized").asBoolean()).isTrue();
  }

  @Test
  void renomeiaCartaoImportadoEPropagaParaVisaoGeral() throws Exception {
    JsonNode renamed = put("/api/v1/accounts/" + cardId + "/display-name", "Cartão da casa");
    assertThat(renamed.path("alias").asString()).isEqualTo("Cartão da casa");
    assertThat(renamed.path("providerName").asString()).isEqualTo("Cartão Alfa");
    assertThat(renamed.path("nameCustomized").asBoolean()).isTrue();

    JsonNode overview =
        json(
            alice
                .get("/api/v1/cards/overview")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode card = null;
    for (JsonNode item : overview.path("items")) {
      if (cardId.equals(item.path("accountId").asString())) {
        card = item;
      }
    }
    assertThat(card).isNotNull();
    assertThat(card.path("alias").asString()).isEqualTo("Cartão da casa");
  }

  @Test
  void trocaDeItemIdPreservaNomePersonalizado() throws Exception {
    put("/api/v1/connections/" + connectionId + "/display-name", "Banco da família");

    alice
        .patch("/api/v1/connections/" + connectionId, Map.of("itemId", "item-display-name-2"))
        .andExpect(status().isOk());

    JsonNode connection = connection();
    assertThat(connection.path("displayName").asString()).isEqualTo("Banco da família");
    assertThat(connection.path("institutionName").asString()).isEqualTo("Banco Alfa 2");
    assertThat(connection.path("nameCustomized").asBoolean()).isTrue();
  }

  @Test
  void outroUsuarioNaoRenomeiaContaNemConexao() throws Exception {
    AuthTestClient bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob.register("bob.display.name", PASSWORD).login("bob.display.name", PASSWORD);

    bob.put(
            "/api/v1/accounts/" + accountId + "/display-name",
            Map.of("displayName", "Conta do Bob"))
        .andExpect(status().isNotFound());
    bob.delete("/api/v1/accounts/" + accountId + "/display-name").andExpect(status().isNotFound());
    bob.put(
            "/api/v1/connections/" + connectionId + "/display-name",
            Map.of("displayName", "Conexão do Bob"))
        .andExpect(status().isNotFound());
    bob.delete("/api/v1/connections/" + connectionId + "/display-name")
        .andExpect(status().isNotFound());

    JsonNode account = json(account());
    assertThat(account.path("alias").asString()).isEqualTo("Conta Alfa");
    assertThat(account.path("nameCustomized").asBoolean()).isFalse();
  }

  @Test
  void restauraNomesOriginais() throws Exception {
    put("/api/v1/accounts/" + accountId + "/display-name", "Conta da Casa");
    put("/api/v1/connections/" + connectionId + "/display-name", "Banco da família");

    JsonNode account =
        json(
            alice
                .delete("/api/v1/accounts/" + accountId + "/display-name")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(account.path("alias").asString()).isEqualTo("Conta Alfa");
    assertThat(account.path("providerName").asString()).isEqualTo("Conta Alfa");
    assertThat(account.path("nameCustomized").asBoolean()).isFalse();

    JsonNode connection =
        json(
            alice
                .delete("/api/v1/connections/" + connectionId + "/display-name")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(connection.path("displayName").asString()).isEqualTo("Banco Alfa");
    assertThat(connection.path("institutionName").asString()).isEqualTo("Banco Alfa");
    assertThat(connection.path("nameCustomized").asBoolean()).isFalse();
  }

  @Test
  void contaManualContinuaSendoRenomeadaPeloFormularioDeEdicao() throws Exception {
    String manualId =
        json(alice
                .post(
                    "/api/v1/accounts",
                    Map.of(
                        "type",
                        "CASH",
                        "alias",
                        "Carteira",
                        "referenceBalance",
                        "10.00",
                        "referenceDate",
                        "2026-10-01T12:00:00Z"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("id")
            .asString();

    alice
        .put(
            "/api/v1/accounts/" + manualId + "/display-name",
            Map.of("displayName", "Carteira da casa"))
        .andExpect(status().isConflict());

    JsonNode updated =
        json(
            alice
                .patch("/api/v1/accounts/" + manualId, Map.of("alias", "Carteira da casa"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(updated.path("alias").asString()).isEqualTo("Carteira da casa");
    assertThat(updated.path("providerName").isNull()).isTrue();
    assertThat(updated.path("nameCustomized").asBoolean()).isFalse();
  }

  @Test
  void validaNomeDeExibicao() throws Exception {
    alice
        .put("/api/v1/accounts/" + accountId + "/display-name", Map.of("displayName", "   "))
        .andExpect(status().isUnprocessableEntity());
    alice
        .put(
            "/api/v1/accounts/" + accountId + "/display-name",
            Map.of("displayName", "x".repeat(121)))
        .andExpect(status().isUnprocessableEntity());
    alice
        .put("/api/v1/connections/" + connectionId + "/display-name", Map.of("displayName", "   "))
        .andExpect(status().isUnprocessableEntity());
  }

  private JsonNode put(String path, String displayName) throws Exception {
    return json(
        alice
            .put(path, Map.of("displayName", displayName))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String account() throws Exception {
    return alice
        .get("/api/v1/accounts/" + accountId)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private JsonNode connection() throws Exception {
    return json(alice
            .get("/api/v1/connections")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("items")
        .get(0);
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

  private String accountIdByType(String type) throws Exception {
    for (JsonNode account : accounts().path("items")) {
      if (type.equals(account.path("type").asString())) {
        return account.path("id").asString();
      }
    }
    throw new AssertionError("Conta do tipo " + type + " não encontrada no setup");
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
