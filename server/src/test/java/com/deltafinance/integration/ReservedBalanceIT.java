package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PostgresTestBase;
import java.time.LocalDate;
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

/** Saldos reservados: sync, nome local, tratamento no patrimônio e remoção. */
@AutoConfigureMockMvc
class ReservedBalanceIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.reserved";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-reserved";
  private static final String ITEM = "item-reserved";

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
  @Autowired private SyncJobService syncJobService;

  private AuthTestClient alice;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "Mercado Pago", "UPDATED");
    PLUGGY.addAccount(
        ITEM,
        new AccountFixture(
            "acc-mp",
            "BANK",
            "CHECKING_ACCOUNT",
            "Mercado Pago",
            "759.81",
            null,
            "BRL",
            bankData(true)));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice.post("/api/v1/connections", Map.of("itemId", ITEM)).andExpect(status().isCreated());
    alice
        .post("/api/v1/connections/" + connectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "reserved-initial");
  }

  @Test
  void sincronizaRenomeiaTrataNoPatrimonioERemove() throws Exception {
    String accountId = accountId();
    JsonNode account = account(accountId);
    assertThat(account.path("hasReservedBalance").asBoolean()).isTrue();
    assertThat(account.path("reservedBalanceTotal").asString()).isEqualTo("9000.00");
    assertThat(account.path("reservedBalanceTreatment").asString()).isEqualTo("UNKNOWN");

    JsonNode reserved = reserved(accountId);
    assertThat(reserved.path("items")).hasSize(1);
    JsonNode item = reserved.path("items").get(0);
    assertThat(item.path("name").asString()).isEqualTo("Cofrinho Vai Brasil");
    assertThat(item.path("providerName").asString()).isEqualTo("Cofrinho Vai Brasil");
    assertThat(item.path("nameCustomized").asBoolean()).isFalse();
    assertThat(item.path("amount").asString()).isEqualTo("9000.00");
    assertThat(item.path("currency").asString()).isEqualTo("BRL");
    assertThat(item.path("amounts")).hasSize(1);
    assertThat(item.path("amounts").get(0).path("indexer").asString()).isEqualTo("CDI");
    assertThat(item.path("amounts").get(0).path("rateType").asString()).isEqualTo("EXPONENCIAL");
    String reservedId = item.path("id").asString();

    // UNKNOWN: a reserva aparece, mas não é somada ao patrimônio.
    assertThat(wealth().path("grossAssets").asString()).isEqualTo("759.81");

    JsonNode renamed =
        json(
            alice
                .put(
                    "/api/v1/reserved-balances/" + reservedId + "/display-name",
                    Map.of("displayName", "Reserva de emergência"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(renamed.path("name").asString()).isEqualTo("Reserva de emergência");
    assertThat(renamed.path("providerName").asString()).isEqualTo("Cofrinho Vai Brasil");
    assertThat(renamed.path("nameCustomized").asBoolean()).isTrue();

    JsonNode connectionTreated =
        json(
            alice
                .put(
                    "/api/v1/connections/" + connectionId() + "/reserved-balance-treatment",
                    Map.of("treatment", "SEPARATE_ASSET"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(connectionTreated.path("reservedBalanceTreatment").asString())
        .isEqualTo("SEPARATE_ASSET");

    // A conta sem override herda o padrão da conexão.
    JsonNode inherited = account(accountId);
    assertThat(inherited.path("reservedBalanceTreatment").asString()).isEqualTo("SEPARATE_ASSET");
    assertThat(inherited.path("reservedBalanceTreatmentSource").asString()).isEqualTo("CONNECTION");

    JsonNode wealth = wealth();
    assertThat(wealth.path("grossAssets").asString()).isEqualTo("9759.81");
    JsonNode reservedItem = null;
    for (JsonNode wealthItem : wealth.path("items")) {
      if ("RESERVED_BALANCE".equals(wealthItem.path("sourceType").asString())) {
        reservedItem = wealthItem;
      }
    }
    assertThat(reservedItem).isNotNull();
    assertThat(reservedItem.path("description").asString()).isEqualTo("Reserva de emergência");
    assertThat(reservedItem.path("value").asString()).isEqualTo("9000.00");

    JsonNode overridden =
        json(
            alice
                .put(
                    "/api/v1/accounts/" + accountId + "/reserved-balance-treatment",
                    Map.of("treatment", "INCLUDED_IN_BALANCE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(overridden.path("reservedBalanceTreatment").asString())
        .isEqualTo("INCLUDED_IN_BALANCE");
    assertThat(overridden.path("reservedBalanceTreatmentSource").asString()).isEqualTo("ACCOUNT");
    assertThat(wealth().path("grossAssets").asString()).isEqualTo("759.81");

    JsonNode cleared =
        json(
            alice
                .put(
                    "/api/v1/accounts/" + accountId + "/reserved-balance-treatment",
                    Map.of("treatment", "UNKNOWN"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(cleared.path("reservedBalanceTreatment").asString()).isEqualTo("SEPARATE_ASSET");
    assertThat(cleared.path("reservedBalanceTreatmentSource").asString()).isEqualTo("CONNECTION");
    assertThat(wealth().path("grossAssets").asString()).isEqualTo("9759.81");

    alice
        .post("/api/v1/wealth/history", Map.of("referenceDate", LocalDate.now().toString()))
        .andExpect(status().isCreated());
    JsonNode history =
        json(
            alice
                .get("/api/v1/wealth/reserved-balances/" + reservedId + "/history")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(history.path("items")).hasSize(1);
    assertThat(history.path("items").get(0).path("value").asString()).isEqualTo("9000.00");
    assertThat(history.path("items").get(0).path("currency").asString()).isEqualTo("BRL");
    assertThat(history.path("items").get(0).path("included").asBoolean()).isTrue();

    JsonNode reset =
        json(
            alice
                .delete("/api/v1/reserved-balances/" + reservedId + "/display-name")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(reset.path("name").asString()).isEqualTo("Cofrinho Vai Brasil");
    assertThat(reset.path("nameCustomized").asBoolean()).isFalse();

    PLUGGY.setAccounts(
        ITEM,
        List.of(
            new AccountFixture(
                "acc-mp",
                "BANK",
                "CHECKING_ACCOUNT",
                "Mercado Pago",
                "759.81",
                null,
                "BRL",
                bankData(false))));
    alice
        .post("/api/v1/connections/" + connectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "reserved-remove");

    assertThat(account(accountId).path("hasReservedBalance").asBoolean()).isFalse();
    assertThat(account(accountId).path("reservedBalanceTotal").asString()).isEqualTo("0.00");
    assertThat(reserved(accountId).path("items")).isEmpty();
    assertThat(wealth().path("grossAssets").asString()).isEqualTo("759.81");
  }

  private static Map<String, Object> bankData(boolean hasReservedBalance) {
    return Map.of(
        "hasReservedBalance",
        hasReservedBalance,
        "reservedBalances",
        hasReservedBalance
            ? List.of(
                Map.of(
                    "name",
                    "Cofrinho Vai Brasil",
                    "identification",
                    "rb-1",
                    "availableAmounts",
                    List.of(
                        Map.of(
                            "amount",
                            9000.00,
                            "currencyCode",
                            "BRL",
                            "remuneration",
                            Map.of("indexer", "CDI", "rateType", "EXPONENCIAL")))))
            : List.of());
  }

  private String accountId() throws Exception {
    for (JsonNode account : accounts().path("items")) {
      if ("Mercado Pago".equals(account.path("alias").asString())) {
        return account.path("id").asString();
      }
    }
    throw new AssertionError("Conta não encontrada");
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

  private JsonNode reserved(String accountId) throws Exception {
    return json(
        alice
            .get("/api/v1/accounts/" + accountId + "/reserved-balances")
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

  private String connectionId() throws Exception {
    JsonNode connections =
        json(
            alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode connection : connections.path("items")) {
      if ("Mercado Pago".equals(connection.path("displayName").asString())
          || "Mercado Pago".equals(connection.path("institutionName").asString())) {
        return connection.path("id").asString();
      }
    }
    throw new AssertionError("Conexão não encontrada");
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
