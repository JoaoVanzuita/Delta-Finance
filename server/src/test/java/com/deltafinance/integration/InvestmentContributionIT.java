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
import java.time.LocalDate;
import java.util.LinkedHashMap;
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

/** Aportes, resgates, aplicações e conciliação de custos do perímetro de investimento. */
@AutoConfigureMockMvc
class InvestmentContributionIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.contributions";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-contributions";
  private static final String INVESTMENT_ITEM = "item-investment";
  private static final String BANK_ITEM = "item-bank";

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
    String today = LocalDate.now().toString();

    PLUGGY.addItem(INVESTMENT_ITEM, "XP", "UPDATED");
    PLUGGY.addAccount(
        INVESTMENT_ITEM,
        new AccountFixture(
            "acc-xp-invest", "BANK", "CHECKING_ACCOUNT", "XP Investimentos", "0.00", null));
    PLUGGY.setInvestmentResources(
        INVESTMENT_ITEM,
        List.of(Map.of("resourceId", "r-1", "type", "VARIABLE_INCOME", "status", "AVAILABLE")));
    PLUGGY.addInvestment(INVESTMENT_ITEM, investment(today));
    PLUGGY.addInvestmentTransaction("inv-1", buy(today));
    PLUGGY.addTransaction(
        "acc-xp-invest",
        new TransactionFixture(
            "tx-contribution",
            "acc-xp-invest",
            "TED recebida para investimento",
            "1010.00",
            today,
            "POSTED",
            null));
    PLUGGY.addTransaction(
        "acc-xp-invest",
        new TransactionFixture(
            "tx-settlement",
            "acc-xp-invest",
            "OPERAÇÕES EM BOLSA PR " + today + " NOTA Nº 1",
            "-1010.00",
            today,
            "POSTED",
            "Investments"));

    PLUGGY.addItem(BANK_ITEM, "Banco", "UPDATED");
    PLUGGY.addAccount(
        BANK_ITEM,
        new AccountFixture("acc-bank", "BANK", "CHECKING_ACCOUNT", "Conta", "0.00", null));
    PLUGGY.addTransaction(
        "acc-bank",
        new TransactionFixture(
            "tx-out",
            "acc-bank",
            "Transferência enviada para investimento",
            "-1010.00",
            today,
            "POSTED",
            null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", INVESTMENT_ITEM))
        .andExpect(status().isCreated());
    alice.post("/api/v1/connections", Map.of("itemId", BANK_ITEM)).andExpect(status().isCreated());
  }

  @Test
  void separaAporteDeAplicacaoEConciliaCustos() throws Exception {
    String investmentAccountId = accountIdByName("XP Investimentos");
    JsonNode marked =
        json(
            alice
                .put(
                    "/api/v1/accounts/" + investmentAccountId + "/investment",
                    Map.of("investmentAccount", true))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(marked.path("investmentAccount").asBoolean()).isTrue();

    JsonNode report =
        json(
            alice
                .get("/api/v1/investments/contributions")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(report.path("contributions").asString()).isEqualTo("1010.00");
    assertThat(report.path("withdrawals").asString()).isEqualTo("0.00");
    assertThat(report.path("netContributions").asString()).isEqualTo("1010.00");
    assertThat(report.path("applied").asString()).isEqualTo("1000.00");
    assertThat(report.path("fees").asString()).isEqualTo("10.00");
    assertThat(report.path("feeSource").asString()).isEqualTo("DERIVED");
    assertThat(report.path("idleCash").asString()).isEqualTo("0.00");
    assertThat(report.path("settlementDebits").asString()).isEqualTo("1010.00");
    assertThat(report.path("reconciliationDifference").asString()).isEqualTo("0.00");
    assertThat(report.path("portfolioValue").asString()).isEqualTo("1100.00");
    assertThat(report.path("cashBalance").asString()).isEqualTo("0.00");
    assertThat(report.path("result").asString()).isEqualTo("90.00");
    assertThat(report.path("resultPercentage").asString()).isEqualTo("8.91");
    assertThat(report.path("sources").get(0).path("perimeterSource").asString())
        .isEqualTo("ACCOUNT_FLAG");

    assertThat(report.path("flows").toString()).contains("CONTRIBUTION").contains("APPLICATION");

    String today = LocalDate.now().toString();
    JsonNode dashboard =
        json(
            alice
                .get("/api/v1/dashboard/summary?from=" + today + "&to=" + today)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(dashboard.path("investmentContributions").asString()).isEqualTo("1010.00");
    assertThat(dashboard.path("investmentWithdrawals").asString()).isEqualTo("0.00");
    assertThat(dashboard.path("netInvestmentContributions").asString()).isEqualTo("1010.00");
    assertThat(dashboard.path("investmentTrackingAvailable").asBoolean()).isTrue();
    // O crédito na conta marcada é aporte, não receita, mesmo sem conciliação confirmada.
    assertThat(dashboard.path("income").asString()).isEqualTo("0.00");

    JsonNode holding =
        json(
            alice
                .get("/api/v1/investments/holdings?status=ACTIVE")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(holding.path("items").get(0).path("costBasis").asString()).isEqualTo("1000.00");
    assertThat(holding.path("items").get(0).path("costBasisSource").asString())
        .isEqualTo("DERIVED");
  }

  @Test
  void cartaoNaoPodeSerMarcadoComoContaDeInvestimento() throws Exception {
    PLUGGY.addAccount(
        INVESTMENT_ITEM,
        new AccountFixture("acc-card", "CREDIT", "CREDIT_CARD", "Cartão", "0.00", null));
    alice
        .post("/api/v1/connections/" + connectionIdByName("XP") + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "contribution-card");
    String cardId = accountIdByName("Cartão");
    alice
        .put("/api/v1/accounts/" + cardId + "/investment", Map.of("investmentAccount", true))
        .andExpect(status().isConflict());
  }

  private String accountIdByName(String name) throws Exception {
    JsonNode accounts =
        json(
            alice
                .get("/api/v1/accounts")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode account : accounts.path("items")) {
      if (name.equals(account.path("alias").asString())) {
        return account.path("id").asString();
      }
    }
    throw new AssertionError("Conta não encontrada: " + name);
  }

  private String connectionIdByName(String name) throws Exception {
    JsonNode connections =
        json(
            alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode connection : connections.path("items")) {
      if (name.equals(connection.path("displayName").asString())
          || name.equals(connection.path("institutionName").asString())) {
        return connection.path("id").asString();
      }
    }
    throw new AssertionError("Conexão não encontrada: " + name);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }

  private static Map<String, Object> investment(String today) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", "inv-1");
    value.put("name", "FII Alfa");
    value.put("code", "AAAA11");
    value.put("type", "EQUITY");
    value.put("subtype", "STOCK");
    value.put("status", "ACTIVE");
    value.put("currencyCode", "BRL");
    value.put("balance", "1100.00");
    value.put("quantity", "10");
    value.put("value", "100.00");
    value.put("amount", "1000.00");
    value.put("date", today);
    value.put("updatedAt", "2026-10-27T12:00:00Z");
    return value;
  }

  private static Map<String, Object> buy(String today) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", "tx-buy");
    value.put("type", "BUY");
    value.put("movementType", "CREDIT");
    value.put("amount", "1000.00");
    value.put("netAmount", "1000.00");
    value.put("quantity", "10");
    value.put("value", "100.00");
    value.put("date", today);
    value.put("tradeDate", today);
    value.put("description", "Aplicação");
    value.put("expenses", Map.of());
    return value;
  }
}
