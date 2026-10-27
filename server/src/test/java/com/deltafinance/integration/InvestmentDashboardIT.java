package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
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

/** Leitura dos investimentos importados: resumo, holdings, cobertura e proventos. */
@AutoConfigureMockMvc
class InvestmentDashboardIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.investdashboard";
  private static final String BOB = "bob.investdashboard";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-investdashboard";
  private static final String ITEM = "item-investdashboard";

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
  private String providerHoldingId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "MeuPluggy", "UPDATED");
    PLUGGY.setInvestmentResources(
        ITEM,
        List.of(
            Map.of("resourceId", "r-1", "type", "VARIABLE_INCOME", "status", "AVAILABLE"),
            Map.of("resourceId", "r-2", "type", "VARIABLE_INCOME", "status", "AVAILABLE")));
    PLUGGY.addInvestment(
        ITEM, investment("inv-1", "FII Alfa", "AAAA11", "ACTIVE", "1000.00", "10"));
    PLUGGY.addInvestment(ITEM, investment("inv-2", "FII Beta", "BBBB11", "ACTIVE", "500.00", "5"));
    PLUGGY.addInvestment(
        ITEM, investment("inv-3", "FII Gama", "CCCC11", "TOTAL_WITHDRAWAL", "0", "0"));
    PLUGGY.addInvestmentTransaction(
        "inv-1",
        transaction(
            "tx-interest-dashboard",
            "INTEREST",
            "12.50",
            LocalDate.now().minusDays(1).toString(),
            "Rendimento"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice.post("/api/v1/connections", Map.of("itemId", ITEM)).andExpect(status().isCreated());

    JsonNode holdings =
        json(
            alice
                .get("/api/v1/investments/holdings")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    providerHoldingId = holdings.path("items").get(0).path("id").asString();

    alice
        .post(
            "/api/v1/investments/positions",
            Map.of(
                "broker", "XP",
                "assetName", "Posição manual AAAA",
                "ticker", "AAAA11",
                "quantity", "1",
                "referencePrice", "100.00",
                "referenceDate", LocalDate.now().toString()))
        .andExpect(status().isCreated());
  }

  @Test
  void resumePosicoesAlocacaoCoberturaEDuplicidade() throws Exception {
    JsonNode overview =
        json(
            alice
                .get("/api/v1/investments/overview")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(overview.path("providerValue").asString()).isEqualTo("1500.00");
    assertThat(overview.path("manualValue").asString()).isEqualTo("100.00");
    assertThat(overview.path("totalValue").asString()).isEqualTo("1600.00");
    assertThat(overview.path("activeCount").asInt()).isEqualTo(2);
    assertThat(overview.path("closedCount").asInt()).isEqualTo(1);
    assertThat(overview.path("manualCount").asInt()).isEqualTo(1);
    assertThat(overview.path("stale").asBoolean()).isFalse();
    assertThat(overview.path("profitReported").isNull()).isTrue();
    assertThat(overview.path("profitCoverageCount").asInt()).isZero();
    assertThat(overview.path("incomeLast12Months").asString()).isEqualTo("12.50");
    assertThat(overview.path("incomeSource").asString()).isEqualTo("PROVIDER");

    JsonNode allocation = overview.path("allocation");
    assertThat(allocation).hasSize(1);
    assertThat(allocation.get(0).path("assetClass").asString()).isEqualTo("FII");
    assertThat(allocation.get(0).path("value").asString()).isEqualTo("1500.00");
    assertThat(allocation.get(0).path("percentage").asString()).isEqualTo("100.00");

    JsonNode coverage = overview.path("coverage");
    assertThat(coverage).hasSize(1);
    assertThat(coverage.get(0).path("scope").asString()).isEqualTo("AVAILABLE");

    JsonNode duplicates = overview.path("duplicates");
    assertThat(duplicates).hasSize(1);
    assertThat(duplicates.get(0).path("code").asString()).isEqualTo("AAAA11");
  }

  @Test
  void filtraHoldingsPorSituacaoEClasseEExpoeDetalhe() throws Exception {
    JsonNode active =
        json(
            alice
                .get("/api/v1/investments/holdings?status=ACTIVE")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(active.path("items")).hasSize(2);
    assertThat(active.path("items").get(0).path("assetClass").asString()).isEqualTo("FII");

    JsonNode closed =
        json(
            alice
                .get("/api/v1/investments/holdings?status=CLOSED")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(closed.path("items")).hasSize(1);
    assertThat(closed.path("items").get(0).path("status").asString()).isEqualTo("CLOSED");

    JsonNode byClass =
        json(
            alice
                .get("/api/v1/investments/holdings?assetClass=FII")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(byClass.path("items")).hasSize(3);

    JsonNode detail =
        json(
            alice
                .get("/api/v1/investments/holdings/" + providerHoldingId)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(detail.path("name").asString()).isNotBlank();
    assertThat(detail.path("balance").asString()).isIn("1000.00", "500.00");
    assertThat(detail.path("classificationSource").asString()).isEqualTo("CODE_HEURISTIC");
  }

  @Test
  void listaProventosPorMesEItem() throws Exception {
    JsonNode income =
        json(
            alice
                .get("/api/v1/investments/income")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(income.path("total").asString()).isEqualTo("12.50");
    assertThat(income.path("source").asString()).isEqualTo("PROVIDER");
    assertThat(income.path("items")).hasSize(1);
    assertThat(income.path("items").get(0).path("investmentName").asString()).isEqualTo("FII Alfa");
    assertThat(income.path("items").get(0).path("amount").asString()).isEqualTo("12.50");
    assertThat(income.path("byMonth")).hasSize(1);
    assertThat(income.path("byMonth").get(0).path("amount").asString()).isEqualTo("12.50");
  }

  @Test
  void listaCoberturaPorConexao() throws Exception {
    JsonNode sources =
        json(
            alice
                .get("/api/v1/investments/sources")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(sources.path("items")).hasSize(1);
    assertThat(sources.path("items").get(0).path("scope").asString()).isEqualTo("AVAILABLE");
    assertThat(sources.path("items").get(0).path("resourceCount").asInt()).isEqualTo(2);
  }

  @Test
  void naoExpoePosicaoDeOutroUsuario() throws Exception {
    AuthTestClient bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    bob.get("/api/v1/investments/holdings/" + providerHoldingId).andExpect(status().isNotFound());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }

  private static Map<String, Object> investment(
      String id, String name, String code, String status, String balance, String quantity) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", id);
    value.put("name", name);
    value.put("code", code);
    value.put("type", "EQUITY");
    value.put("subtype", "STOCK");
    value.put("status", status);
    value.put("currencyCode", "BRL");
    value.put("balance", balance);
    value.put("quantity", quantity);
    value.put("value", quantity.equals("0") ? "0" : "100.00");
    value.put("amount", balance);
    value.put("date", LocalDate.now().toString());
    value.put("updatedAt", "2026-10-27T12:00:00Z");
    return value;
  }

  private static Map<String, Object> transaction(
      String id, String type, String amount, String date, String description) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", id);
    value.put("type", type);
    value.put("movementType", "CREDIT");
    value.put("amount", amount);
    value.put("quantity", "0");
    value.put("value", amount);
    value.put("date", date);
    value.put("tradeDate", date);
    value.put("description", description);
    value.put("expenses", Map.of());
    return value;
  }
}
