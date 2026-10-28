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

/** Proventos manuais, custo informado e resultado estimado de investimentos. */
@AutoConfigureMockMvc
class InvestmentIncomeIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.investincome";
  private static final String BOB = "bob.investincome";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-investincome";
  private static final String ITEM = "item-investincome";

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
  private String holdingId;
  private String manualPositionId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "MeuPluggy", "UPDATED");
    PLUGGY.setInvestmentResources(
        ITEM,
        List.of(Map.of("resourceId", "r-1", "type", "VARIABLE_INCOME", "status", "AVAILABLE")));
    PLUGGY.addInvestment(ITEM, investment());
    PLUGGY.addInvestmentTransaction(
        "inv-1",
        transaction(
            "tx-interest-provider",
            "INTEREST",
            "12.50",
            LocalDate.now().minusDays(2).toString(),
            "Rendimento do provedor"));

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
    holdingId = holdings.path("items").get(0).path("id").asString();

    JsonNode created =
        json(
            alice
                .post(
                    "/api/v1/investments/positions",
                    Map.of(
                        "broker", "XP",
                        "assetName", "Posição manual",
                        "ticker", "MANU11",
                        "quantity", "1",
                        "referencePrice", "100.00",
                        "referenceDate", LocalDate.now().toString()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    manualPositionId = created.path("id").asString();
  }

  @Test
  void criaProventosManuaisECombinaComProvedorNoResumo() throws Exception {
    String providerIncome =
        createIncome("PROVIDER", holdingId, "DIVIDEND", "7.50", "Dividendo manual");
    String manualIncome =
        createIncome("MANUAL", manualPositionId, "RENT", "5.00", "Aluguel manual");

    JsonNode income = income();
    assertThat(income.path("total").asString()).isEqualTo("25.00");
    assertThat(income.path("source").asString()).isEqualTo("MIXED");
    assertThat(income.path("items")).hasSize(3);
    assertThat(manualItem(income, providerIncome).path("editable").asBoolean()).isTrue();
    assertThat(manualItem(income, providerIncome).path("source").asString()).isEqualTo("MANUAL");
    assertThat(manualItem(income, manualIncome).path("targetType").asString()).isEqualTo("MANUAL");

    JsonNode overview =
        json(
            alice
                .get("/api/v1/investments/overview")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(overview.path("incomeLast12Months").asString()).isEqualTo("25.00");
    assertThat(overview.path("incomeSource").asString()).isEqualTo("MIXED");
    assertThat(overview.path("estimatedProfit").asString()).isEqualTo("220.00");
    assertThat(overview.path("estimatedProfitCoverageCount").asInt()).isEqualTo(1);

    JsonNode holding = holding();
    assertThat(holding.path("costBasis").asString()).isEqualTo("800.00");
    assertThat(holding.path("costBasisSource").asString()).isEqualTo("PROVIDER");
    assertThat(holding.path("incomeTotal").asString()).isEqualTo("20.00");
    assertThat(holding.path("estimatedProfit").asString()).isEqualTo("220.00");
    assertThat(holding.path("estimatedReturnPercentage").asString()).isEqualTo("27.50");
  }

  @Test
  void custoInformadoSobrepoeCustoDoProvedorECalculaResultado() throws Exception {
    alice
        .patch(
            "/api/v1/investments/holdings/" + holdingId + "/cost-basis",
            Map.of("costBasis", "900.00"))
        .andExpect(status().isNoContent());

    JsonNode holding = holding();
    assertThat(holding.path("manualCostBasis").asString()).isEqualTo("900.00");
    assertThat(holding.path("costBasis").asString()).isEqualTo("900.00");
    assertThat(holding.path("costBasisSource").asString()).isEqualTo("MANUAL");
    assertThat(holding.path("estimatedProfit").asString()).isEqualTo("112.50");
    assertThat(holding.path("estimatedReturnPercentage").asString()).isEqualTo("12.50");

    alice
        .patch("/api/v1/investments/holdings/" + holdingId + "/cost-basis", Map.of("costBasis", ""))
        .andExpect(status().isNoContent());
    assertThat(holding().path("costBasisSource").asString()).isEqualTo("PROVIDER");
  }

  @Test
  void classificacaoInformadaSobrepoeProvedor() throws Exception {
    alice
        .patch(
            "/api/v1/investments/holdings/" + holdingId + "/asset-class",
            Map.of("assetClass", "ACAO"))
        .andExpect(status().isNoContent());

    JsonNode holding = holding();
    assertThat(holding.path("assetClass").asString()).isEqualTo("ACAO");
    assertThat(holding.path("classificationSource").asString()).isEqualTo("USER");

    JsonNode overview =
        json(
            alice
                .get("/api/v1/investments/overview")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(overview.path("allocation").get(0).path("assetClass").asString()).isEqualTo("ACAO");
  }

  @Test
  void editaEExcluiProventoManual() throws Exception {
    String id = createIncome("PROVIDER", holdingId, "DIVIDEND", "7.50", "Dividendo");
    alice
        .patch(
            "/api/v1/investments/income/" + id,
            Map.of(
                "providerInvestmentId", holdingId,
                "incomeType", "JCP",
                "incomeDate", LocalDate.now().minusDays(1).toString(),
                "amount", "8.00",
                "description", "JCP corrigido"))
        .andExpect(status().isOk());

    JsonNode updated = manualItem(income(), id);
    assertThat(updated.path("incomeType").asString()).isEqualTo("JCP");
    assertThat(updated.path("amount").asString()).isEqualTo("8.00");

    alice.delete("/api/v1/investments/income/" + id).andExpect(status().isNoContent());
    assertThat(income().path("items")).hasSize(1);
  }

  @Test
  void validaDestinoValorDataEIsolamento() throws Exception {
    alice
        .post(
            "/api/v1/investments/income",
            Map.of(
                "providerInvestmentId",
                holdingId,
                "investmentPositionId",
                manualPositionId,
                "incomeType",
                "DIVIDEND",
                "incomeDate",
                LocalDate.now().toString(),
                "amount",
                "1.00"))
        .andExpect(status().isUnprocessableEntity());
    alice
        .post(
            "/api/v1/investments/income",
            Map.of(
                "providerInvestmentId",
                holdingId,
                "incomeType",
                "DIVIDEND",
                "incomeDate",
                LocalDate.now().plusDays(1).toString(),
                "amount",
                "1.00"))
        .andExpect(status().isUnprocessableEntity());
    alice
        .post(
            "/api/v1/investments/income",
            Map.of(
                "providerInvestmentId",
                holdingId,
                "incomeType",
                "DIVIDEND",
                "incomeDate",
                LocalDate.now().toString(),
                "amount",
                "0"))
        .andExpect(status().isUnprocessableEntity());

    String id = createIncome("PROVIDER", holdingId, "DIVIDEND", "1.00", null);
    AuthTestClient bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    bob.delete("/api/v1/investments/income/" + id).andExpect(status().isNotFound());
    bob.patch(
            "/api/v1/investments/holdings/" + holdingId + "/cost-basis",
            Map.of("costBasis", "1.00"))
        .andExpect(status().isNotFound());
  }

  private String createIncome(
      String targetType, String targetId, String type, String amount, String description)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    if ("PROVIDER".equals(targetType)) {
      body.put("providerInvestmentId", targetId);
    } else {
      body.put("investmentPositionId", targetId);
    }
    body.put("incomeType", type);
    body.put("incomeDate", LocalDate.now().minusDays(1).toString());
    body.put("amount", amount);
    if (description != null) {
      body.put("description", description);
    }
    return json(alice
            .post("/api/v1/investments/income", body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
  }

  private JsonNode income() throws Exception {
    return json(
        alice
            .get("/api/v1/investments/income")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode holding() throws Exception {
    return json(
        alice
            .get("/api/v1/investments/holdings/" + holdingId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static JsonNode manualItem(JsonNode income, String id) {
    for (JsonNode item : income.path("items")) {
      if (id.equals(item.path("id").asString())) {
        return item;
      }
    }
    throw new AssertionError("Provento manual não encontrado: " + id);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }

  private static Map<String, Object> investment() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", "inv-1");
    value.put("name", "FII Alfa");
    value.put("code", "AAAA11");
    value.put("type", "EQUITY");
    value.put("subtype", "STOCK");
    value.put("status", "ACTIVE");
    value.put("currencyCode", "BRL");
    value.put("balance", "1000.00");
    value.put("quantity", "10");
    value.put("value", "100.00");
    value.put("amount", "1000.00");
    value.put("amountOriginal", "800.00");
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
