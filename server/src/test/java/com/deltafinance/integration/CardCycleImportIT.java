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

/**
 * Importação de cartões: sem creditData o ciclo não é inventado; com dados, o provedor prevalece.
 */
@AutoConfigureMockMvc
class CardCycleImportIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.cardcycle";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-cardcycle";

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
  private String cardWithoutCycleId;
  private String cardWithCycleId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-cardcycle", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-cardcycle",
        new AccountFixture(
            "acc-no-cycle", "CREDIT", "CREDIT_CARD", "Cartão sem ciclo", "0.00", null));
    PLUGGY.addAccount(
        "item-cardcycle",
        new AccountFixture(
            "acc-with-cycle",
            "CREDIT",
            "CREDIT_CARD",
            "Cartão com ciclo",
            "0.00",
            Map.of(
                "balanceCloseDate", "2026-10-10",
                "balanceDueDate", "2026-10-20",
                "creditLimit", "2000.00")));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-cardcycle"))
        .andExpect(status().isCreated());

    for (JsonNode account : accounts().path("items")) {
      if ("Cartão sem ciclo".equals(account.path("alias").asString())) {
        cardWithoutCycleId = account.path("id").asString();
        assertThat(account.path("cardProfile").isNull()).isTrue();
      }
      if ("Cartão com ciclo".equals(account.path("alias").asString())) {
        cardWithCycleId = account.path("id").asString();
        assertThat(account.path("cardProfile").path("closingDay").asInt()).isEqualTo(10);
        assertThat(account.path("cardProfile").path("dueDay").asInt()).isEqualTo(20);
        assertThat(account.path("cardProfile").path("creditLimit").asString()).isEqualTo("2000.00");
      }
    }
    assertThat(cardWithoutCycleId).isNotBlank();
    assertThat(cardWithCycleId).isNotBlank();
  }

  @Test
  void cartaoSemCreditDataFicaSemCicloENaoGeraFaturasAteConfigurar() throws Exception {
    Map<String, Object> transaction = new LinkedHashMap<>();
    transaction.put("accountId", cardWithoutCycleId);
    transaction.put("type", "DEBIT");
    transaction.put("postedOn", "2026-10-03");
    transaction.put("amount", "40.00");
    transaction.put("description", "Compra sem ciclo");
    alice.post("/api/v1/transactions", transaction).andExpect(status().isCreated());

    assertThat(invoices(cardWithoutCycleId).path("items")).isEmpty();
    JsonNode listed = transactions(cardWithoutCycleId);
    assertThat(listed.path("items")).hasSize(1);
    assertThat(listed.path("items").get(0).path("invoiceId").isNull()).isTrue();

    alice
        .put(
            "/api/v1/cards/" + cardWithoutCycleId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "1000.00"))
        .andExpect(status().isOk());

    JsonNode invoices = invoices(cardWithoutCycleId);
    assertThat(invoices.path("items")).hasSize(1);
    assertThat(invoices.path("items").get(0).path("total").asString()).isEqualTo("40.00");
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

  private JsonNode transactions(String accountId) throws Exception {
    return json(
        alice
            .get("/api/v1/transactions?limit=100&accountId=" + accountId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode invoices(String cardAccountId) throws Exception {
    return json(
        alice
            .get("/api/v1/cards/" + cardAccountId + "/invoices")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
