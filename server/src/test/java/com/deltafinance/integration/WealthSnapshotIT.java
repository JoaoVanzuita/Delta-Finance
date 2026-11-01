package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
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

/** Histórico patrimonial e de investimentos derivado de snapshots mensais. */
@AutoConfigureMockMvc
class WealthSnapshotIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.snapshot";
  private static final String BOB = "bob.snapshot";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-snapshot";
  private static final String ITEM = "item-snapshot";

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
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "MeuPluggy", "UPDATED");
    PLUGGY.addAccount(
        ITEM,
        new AccountFixture("acc-bank", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.setInvestmentResources(
        ITEM,
        List.of(
            Map.of("resourceId", "r-1", "type", "VARIABLE_INCOME", "status", "AVAILABLE"),
            Map.of("resourceId", "r-2", "type", "FIXED_INCOME", "status", "AVAILABLE")));
    PLUGGY.addInvestment(
        ITEM,
        investment("inv-fii", "FII Alfa", "AAAA11", "EQUITY", "REAL_ESTATE_FUND", "1000.00", "10"));
    PLUGGY.addInvestment(
        ITEM, investment("inv-cdb", "CDB Alfa", "CDB-1", "FIXED_INCOME", "CDB", "500.00", "1"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice.post("/api/v1/connections", Map.of("itemId", ITEM)).andExpect(status().isCreated());
  }

  @Test
  void capturaListaDetalhaEIsolaSnapshots() throws Exception {
    JsonNode created =
        json(
            alice
                .post("/api/v1/wealth/history", Map.of())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(created.path("grossAssets").asString()).isEqualTo("2500.00");
    assertThat(created.path("liabilities").asString()).isEqualTo("0.00");
    assertThat(created.path("netWorth").asString()).isEqualTo("2500.00");
    assertThat(created.path("completeness").asString()).isEqualTo("COMPLETE");
    assertThat(created.path("itemCount").asInt()).isEqualTo(3);
    assertThat(created.path("includedItemCount").asInt()).isEqualTo(3);
    assertThat(created.path("items")).hasSize(3);

    JsonNode fii = item(created, "INVESTMENT", "FII");
    assertThat(fii.path("value").asString()).isEqualTo("1000.00");
    assertThat(fii.path("currency").asString()).isEqualTo("BRL");
    JsonNode cdb = item(created, "INVESTMENT", "RENDA_FIXA");
    assertThat(cdb.path("value").asString()).isEqualTo("500.00");

    String id = created.path("id").asString();
    JsonNode history =
        json(
            alice
                .get("/api/v1/wealth/history")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(history).hasSize(1);
    assertThat(history.get(0).path("id").asString()).isEqualTo(id);

    JsonNode detail =
        json(
            alice
                .get("/api/v1/wealth/history/" + id)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(detail.path("items")).hasSize(3);

    JsonNode investments =
        json(
            alice
                .get("/api/v1/investments/history")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(investments).hasSize(1);
    assertThat(investments.get(0).path("totalValue").asString()).isEqualTo("1500.00");
    assertThat(investments.get(0).path("allocation")).hasSize(2);
    assertThat(investments.get(0).path("allocation").get(0).path("assetClass").asString())
        .isEqualTo("FII");
    assertThat(investments.get(0).path("allocation").get(0).path("percentage").asString())
        .isEqualTo("66.67");

    alice.post("/api/v1/wealth/history", Map.of()).andExpect(status().isConflict());

    bob.get("/api/v1/wealth/history/" + id).andExpect(status().isNotFound());
    bob.post("/api/v1/wealth/history", Map.of()).andExpect(status().isUnprocessableEntity());
  }

  @Test
  void rejeitaDataFutura() throws Exception {
    alice
        .post(
            "/api/v1/wealth/history",
            Map.of("referenceDate", LocalDate.now().plusDays(1).toString()))
        .andExpect(status().isUnprocessableEntity());
    alice
        .post(
            "/api/v1/wealth/history",
            Map.of("referenceDate", LocalDate.now().minusDays(1).toString()))
        .andExpect(status().isUnprocessableEntity());
  }

  private static JsonNode item(JsonNode snapshot, String sourceType, String assetClass) {
    for (JsonNode item : snapshot.path("items")) {
      if (sourceType.equals(item.path("sourceType").asString())
          && assetClass.equals(item.path("assetClass").asString())) {
        return item;
      }
    }
    throw new AssertionError("Item não encontrado: " + sourceType + "/" + assetClass);
  }

  private static Map<String, Object> investment(
      String id,
      String name,
      String code,
      String type,
      String subtype,
      String balance,
      String quantity) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", id);
    value.put("name", name);
    value.put("code", code);
    value.put("type", type);
    value.put("subtype", subtype);
    value.put("status", "ACTIVE");
    value.put("currencyCode", "BRL");
    value.put("balance", balance);
    value.put("quantity", quantity);
    value.put("value", "100.00");
    value.put("amount", balance);
    value.put("date", LocalDate.now().toString());
    value.put("updatedAt", "2026-10-27T12:00:00Z");
    return value;
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
