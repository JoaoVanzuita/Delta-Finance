package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
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

/** Fallback do endpoint dedicado de saldos reservados para itens Open Finance. */
@AutoConfigureMockMvc
class ReservedBalanceFallbackIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.reserved.fallback";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-reserved-fallback";
  private static final String ITEM = "item-reserved-fallback";
  private static final String ACCOUNT = "acc-reserved-fallback";

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
    PLUGGY.addItem(ITEM, "Banco Open Finance", "UPDATED");
    PLUGGY.addAccount(
        ITEM,
        new AccountFixture(
            ACCOUNT,
            "BANK",
            "CHECKING_ACCOUNT",
            "Conta Open Finance",
            "1000.00",
            null,
            "BRL",
            Map.of("hasReservedBalance", true, "reservedBalances", List.of())));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
  }

  @Test
  void usaEndpointDedicadoQuandoInlineVemVazioEmItemOpenFinance() throws Exception {
    PLUGGY.setItemOpenFinance(ITEM, true);
    PLUGGY.setReservedBalances(ACCOUNT, List.of(reservation("Caixinha Open Finance", "500.00")));
    connect();

    JsonNode account = account().path("items").get(0);
    assertThat(account.path("hasReservedBalance").asBoolean()).isTrue();
    assertThat(account.path("reservedBalanceTotal").asString()).isEqualTo("500.00");
    JsonNode reserved = reserved();
    assertThat(reserved.path("items")).hasSize(1);
    assertThat(reserved.path("items").get(0).path("name").asString())
        .isEqualTo("Caixinha Open Finance");
    assertThat(reserved.path("items").get(0).path("amount").asString()).isEqualTo("500.00");
    assertThat(
            PLUGGY.requests().stream()
                .anyMatch(
                    request ->
                        request.getRequestUrl() != null
                            && request
                                .getRequestUrl()
                                .encodedPath()
                                .endsWith("/reserved-balances")))
        .isTrue();
  }

  @Test
  void naoChamaEndpointDedicadoEmItemMeuPluggy() throws Exception {
    PLUGGY.setReservedBalances(ACCOUNT, List.of(reservation("Não deve aparecer", "500.00")));
    connect();

    assertThat(account().path("items").get(0).path("hasReservedBalance").asBoolean()).isTrue();
    assertThat(account().path("items").get(0).path("reservedBalanceTotal").asString())
        .isEqualTo("0.00");
    assertThat(reserved().path("items")).isEmpty();
    assertThat(
            PLUGGY.requests().stream()
                .noneMatch(
                    request ->
                        request.getRequestUrl() != null
                            && request
                                .getRequestUrl()
                                .encodedPath()
                                .endsWith("/reserved-balances")))
        .isTrue();
  }

  @Test
  void falhaDoEndpointDedicadoNaoInterrompeSincronizacao() throws Exception {
    PLUGGY.setItemOpenFinance(ITEM, true);
    PLUGGY.setReservedBalances(ACCOUNT, List.of(reservation("Caixinha Open Finance", "500.00")));
    PLUGGY.failPath("/accounts/" + ACCOUNT + "/reserved-balances", 403, 10);
    connect();

    assertThat(account().path("items").get(0).path("hasReservedBalance").asBoolean()).isTrue();
    assertThat(account().path("items").get(0).path("reservedBalanceTotal").asString())
        .isEqualTo("0.00");
    assertThat(reserved().path("items")).isEmpty();
  }

  private void connect() throws Exception {
    alice.post("/api/v1/connections", Map.of("itemId", ITEM)).andExpect(status().isCreated());
    alice
        .post("/api/v1/connections/" + connectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "reserved-fallback");
  }

  private static Map<String, Object> reservation(String name, String amount) {
    return Map.of(
        "name",
        name,
        "identification",
        "rb-" + name,
        "availableAmounts",
        List.of(Map.of("amount", Double.valueOf(amount), "currencyCode", "BRL")));
  }

  private JsonNode account() throws Exception {
    return json(
        alice
            .get("/api/v1/accounts")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode reserved() throws Exception {
    JsonNode accounts = account();
    String accountId = accounts.path("items").get(0).path("id").asString();
    return json(
        alice
            .get("/api/v1/accounts/" + accountId + "/reserved-balances")
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
    return connections.path("items").get(0).path("id").asString();
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
