package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PostgresTestBase;
import java.util.LinkedHashMap;
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

/** Visão geral separada dos cartões de crédito, com totais e pendências de ciclo (T197). */
@AutoConfigureMockMvc
class CardOverviewIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.cartoes";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-cartoes";

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
  private String cardWithCycleId;
  private String cardWithoutCycleId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-cartoes", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-cartoes",
        new AccountFixture("acc-bank", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addAccount(
        "item-cartoes",
        new AccountFixture(
            "acc-card",
            "CREDIT",
            "CREDIT_CARD",
            "Cartão Alfa",
            "100.00",
            Map.of(
                "balanceCloseDate", "2026-10-05",
                "balanceDueDate", "2026-10-15",
                "creditLimit", "5000.00")));
    PLUGGY.addAccount(
        "item-cartoes",
        new AccountFixture(
            "acc-card-no-cycle", "CREDIT", "CREDIT_CARD", "Cartão sem ciclo", "0.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-cartoes"))
        .andExpect(status().isCreated());

    for (JsonNode account : accounts().path("items")) {
      if ("Cartão Alfa".equals(account.path("alias").asString())) {
        cardWithCycleId = account.path("id").asString();
      }
      if ("Cartão sem ciclo".equals(account.path("alias").asString())) {
        cardWithoutCycleId = account.path("id").asString();
      }
    }
    assertThat(cardWithCycleId).isNotBlank();
    assertThat(cardWithoutCycleId).isNotBlank();

    Map<String, Object> purchase = new LinkedHashMap<>();
    purchase.put("accountId", cardWithCycleId);
    purchase.put("type", "DEBIT");
    purchase.put("postedOn", "2026-10-03");
    purchase.put("amount", "100.00");
    purchase.put("description", "Compra no cartão");
    alice.post("/api/v1/transactions", purchase).andExpect(status().isCreated());
  }

  @Test
  void consolidaFaturaLimiteUsoEProximoVencimentoSemMisturarContas() throws Exception {
    JsonNode overview = overview();

    assertThat(overview.path("totalOutstanding").asString()).isEqualTo("100.00");
    assertThat(overview.path("totalLimit").asString()).isEqualTo("5000.00");
    assertThat(overview.path("totalUsed").asString()).isEqualTo("100.00");
    assertThat(overview.path("nextDueDate").asString()).isEqualTo("2026-10-15");
    assertThat(overview.path("items")).hasSize(2);

    JsonNode card = item(overview, cardWithCycleId);
    assertThat(card.path("hasCycle").asBoolean()).isTrue();
    assertThat(card.path("closingDay").asInt()).isEqualTo(5);
    assertThat(card.path("dueDay").asInt()).isEqualTo(15);
    assertThat(card.path("creditLimit").asString()).isEqualTo("5000.00");
    assertThat(card.path("usedAmount").asString()).isEqualTo("100.00");
    assertThat(card.path("outstanding").asString()).isEqualTo("100.00");
    assertThat(card.path("utilization").asString()).isEqualTo("2.00");
    assertThat(card.path("nextDueDate").asString()).isEqualTo("2026-10-15");
    assertThat(card.path("currentInvoice").path("total").asString()).isEqualTo("100.00");
    assertThat(card.path("currentInvoice").path("closingDate").asString()).isEqualTo("2026-10-05");
    assertThat(card.path("nextDueInvoice").path("dueDate").asString()).isEqualTo("2026-10-15");
    assertThat(card.path("unallocatedCount").asInt()).isZero();

    JsonNode withoutCycle = item(overview, cardWithoutCycleId);
    assertThat(withoutCycle.path("hasCycle").asBoolean()).isFalse();
    assertThat(withoutCycle.path("currentInvoice").isNull()).isTrue();
    assertThat(withoutCycle.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(withoutCycle.path("unallocatedCount").asInt()).isZero();
  }

  @Test
  void visaoGeralNaoExpoeCartoesDeOutroUsuario() throws Exception {
    AuthTestClient bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob.register("bob.cartoes", PASSWORD).login("bob.cartoes", PASSWORD);

    JsonNode overview =
        json(
            bob.get("/api/v1/cards/overview")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(overview.path("items")).isEmpty();
    assertThat(overview.path("totalOutstanding").asString()).isEqualTo("0.00");
  }

  private JsonNode item(JsonNode overview, String accountId) {
    for (JsonNode item : overview.path("items")) {
      if (accountId.equals(item.path("accountId").asString())) {
        return item;
      }
    }
    throw new AssertionError("Cartão não encontrado: " + accountId);
  }

  private JsonNode overview() throws Exception {
    return json(
        alice
            .get("/api/v1/cards/overview")
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
