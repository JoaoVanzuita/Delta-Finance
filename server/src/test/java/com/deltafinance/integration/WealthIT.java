package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PostgresTestBase;
import java.time.LocalDate;
import java.time.ZoneId;
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

/**
 * Patrimônio consolidado (T155).
 *
 * <p>Cobre ativos/passivos manuais, consolidação de contas/cartões/investimentos sem dupla
 * contagem, lacunas, dados desatualizados, estimativa e isolamento.
 */
@AutoConfigureMockMvc
class WealthIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.patrimonio";
  private static final String BOB = "bob.patrimonio";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-patrimonio";
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

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
  private String bankId;
  private String cardId;
  private String usdCardId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-alice", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-bank", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-card", "CREDIT", "CREDIT_CARD", "Cartão Alfa", "0.00", null));
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture(
            "acc-card-usd", "CREDIT", "CREDIT_CARD", "Cartão Dólar", "0.00", null, "USD"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);

    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice"))
        .andExpect(status().isCreated());

    for (JsonNode account : accounts().path("items")) {
      if ("CREDIT".equals(account.path("type").asString())) {
        String alias = account.path("alias").asString();
        if ("Cartão Dólar".equals(alias)) {
          usdCardId = account.path("id").asString();
        } else if ("Cartão Alfa".equals(alias)) {
          cardId = account.path("id").asString();
        }
      }
      if ("BANK".equals(account.path("type").asString())) {
        bankId = account.path("id").asString();
      }
    }
    assertThat(cardId).isNotBlank();
    assertThat(usdCardId).isNotBlank();
    assertThat(bankId).isNotBlank();

    alice
        .put(
            "/api/v1/cards/" + cardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "5000.00"))
        .andExpect(status().isOk());
    alice
        .put(
            "/api/v1/cards/" + usdCardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "1000.00"))
        .andExpect(status().isOk());
    createCardTransaction("-100.00", "2026-10-03");
    createCardTransaction(usdCardId, "-50.00", "2026-10-03");
    alice.get("/api/v1/cards/" + cardId + "/invoices").andExpect(status().isOk());
    alice.get("/api/v1/cards/" + usdCardId + "/invoices").andExpect(status().isOk());
  }

  @Test
  void consolidaAtivosEPassivosSemDuplicarComprasEFaturas() throws Exception {
    createManualItem("ASSET", "INVESTMENT", "Tesouro reserva", "500.00", freshDate(), null);
    createManualItem("LIABILITY", "LOAN", "Empréstimo", "200.00", freshDate(), null);

    JsonNode overview = overview(alice);
    assertThat(overview.path("grossAssets").asString()).isEqualTo("1500.00");
    assertThat(overview.path("liabilities").asString()).isEqualTo("300.00");
    assertThat(overview.path("netWorth").asString()).isEqualTo("1200.00");
    assertThat(overview.path("hasData").asBoolean()).isTrue();
    assertThat(overview.path("completeness").asString()).isEqualTo("COMPLETE");
    assertThat(overview.path("referenceDate").asString()).isEqualTo(today().toString());

    JsonNode items = overview.path("items");
    assertThat(items).hasSize(5);
    assertThat(find(items, "ACCOUNT", bankId).path("value").asString()).isEqualTo("1000.00");
    assertThat(find(items, "CARD", cardId).path("value").asString()).isEqualTo("100.00");
    assertThat(find(items, "CARD", usdCardId).path("included").asBoolean()).isFalse();
    assertThat(find(items, "CARD", usdCardId).path("exclusionReason").asString())
        .contains("Moeda diferente de BRL");
    assertThat(find(items, "MANUAL_ASSET", null).path("value").asString()).isEqualTo("500.00");
    assertThat(find(items, "MANUAL_LIABILITY", null).path("value").asString()).isEqualTo("200.00");

    java.math.BigDecimal assetSum = java.math.BigDecimal.ZERO;
    java.math.BigDecimal liabilitySum = java.math.BigDecimal.ZERO;
    for (JsonNode item : items) {
      if (!item.path("included").asBoolean()) {
        continue;
      }
      String type = item.path("sourceType").asString();
      java.math.BigDecimal value = new java.math.BigDecimal(item.path("value").asString());
      if ("CARD".equals(type) || "MANUAL_LIABILITY".equals(type)) {
        liabilitySum = liabilitySum.add(value);
      } else {
        assetSum = assetSum.add(value);
      }
    }
    assertThat(assetSum.toPlainString()).isEqualTo("1500.00");
    assertThat(liabilitySum.toPlainString()).isEqualTo("300.00");
    assertThat(items.toString()).doesNotContain("1100.00");
  }

  @Test
  void naoConsolidaCartaoEmMoedaEstrangeira() throws Exception {
    JsonNode overview = overview(alice);
    assertThat(overview.path("grossAssets").asString()).isEqualTo("1000.00");
    assertThat(overview.path("liabilities").asString()).isEqualTo("100.00");
    assertThat(overview.path("netWorth").asString()).isEqualTo("900.00");

    JsonNode usdCard = find(overview.path("items"), "CARD", usdCardId);
    assertThat(usdCard.path("included").asBoolean()).isFalse();
    assertThat(usdCard.path("exclusionReason").asString())
        .isEqualTo("Moeda diferente de BRL não compõe o passivo consolidado.");

    JsonNode summary =
        json(
            alice
                .get(
                    "/api/v1/dashboard/summary?from="
                        + today().withDayOfMonth(1)
                        + "&to="
                        + today())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(summary.path("cardOutstandingBalance").asString()).isEqualTo("100.00");
  }

  @Test
  void incluiInvestimentosImportadosAtivosNoPatrimonio() throws Exception {
    PLUGGY.addItem("item-invest", "MeuPluggy", "UPDATED");
    PLUGGY.setInvestmentResources(
        "item-invest",
        List.of(Map.of("resourceId", "r-1", "type", "VARIABLE_INCOME", "status", "AVAILABLE")));
    PLUGGY.addInvestment(
        "item-invest", investment("inv-open", "FII Aberto", "HGLG11", "ACTIVE", "200.00"));
    PLUGGY.addInvestment(
        "item-invest", investment("inv-closed", "FII Fechado", "CCCC11", "TOTAL_WITHDRAWAL", "0"));
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-invest"))
        .andExpect(status().isCreated());

    JsonNode overview = overview(alice);
    assertThat(overview.path("grossAssets").asString()).isEqualTo("1200.00");
    assertThat(overview.path("liabilities").asString()).isEqualTo("100.00");
    assertThat(overview.path("netWorth").asString()).isEqualTo("1100.00");

    boolean providerFound = false;
    boolean closedFound = false;
    for (JsonNode item : overview.path("items")) {
      String description = item.path("description").asString();
      if ("FII Fechado (CCCC11)".equals(description)) {
        closedFound = true;
      }
      if ("FII Aberto (HGLG11)".equals(description)) {
        providerFound = true;
        assertThat(item.path("origin").asString()).isEqualTo("PROVIDER");
        assertThat(item.path("value").asString()).isEqualTo("200.00");
        assertThat(item.path("included").asBoolean()).isTrue();
      }
    }
    assertThat(providerFound).isTrue();
    assertThat(closedFound).isFalse();
  }

  private static Map<String, Object> investment(
      String id, String name, String code, String status, String balance) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", id);
    value.put("name", name);
    value.put("code", code);
    value.put("type", "EQUITY");
    value.put("subtype", "STOCK");
    value.put("status", status);
    value.put("currencyCode", "BRL");
    value.put("balance", balance);
    value.put("quantity", "2");
    value.put("value", "100.00");
    value.put("amount", balance);
    value.put("date", today().toString());
    value.put("updatedAt", "2026-10-27T12:00:00Z");
    return value;
  }

  @Test
  void sinalizaLacunasEPrecedenciaDeCompletudeParcial() throws Exception {
    createManualItem("ASSET", "INVESTMENT", "Ativo antigo", "500.00", today().minusDays(60), null);

    PLUGGY.addItem("item-sem-saldo", "Banco Sem Saldo", "UPDATED");
    PLUGGY.addAccount(
        "item-sem-saldo",
        new AccountFixture(
            "acc-sem-saldo", "BANK", "CHECKING_ACCOUNT", "Conta sem saldo", null, null));
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-sem-saldo"))
        .andExpect(status().isCreated());

    JsonNode overview = overview(alice);
    assertThat(overview.path("completeness").asString()).isEqualTo("PARTIAL");

    boolean gapFound = false;
    boolean staleFound = false;
    for (JsonNode item : overview.path("items")) {
      if (!item.path("included").asBoolean()
          && item.path("exclusionReason").asString().contains("Saldo não informado")) {
        gapFound = true;
      }
      if (item.path("stale").asBoolean()) {
        staleFound = true;
      }
    }
    assertThat(gapFound).isTrue();
    assertThat(staleFound).isTrue();
    assertThat(overview.path("grossAssets").asString()).isEqualTo("1500.00");
  }

  @Test
  void marcaComoEstimadoQuandoSoHaReferenciasAntigas() throws Exception {
    JsonNode before = overview(bob);
    assertThat(before.path("hasData").asBoolean()).isFalse();
    assertThat(before.path("completeness").asString()).isEqualTo("PARTIAL");

    createManualItem(bob, "ASSET", "IMOVEL", "Imóvel", "800000.00", today().minusDays(90), null);
    createManualItem(
        bob, "LIABILITY", "FINANCING", "Financiamento", "300000.00", today().minusDays(90), null);

    JsonNode overview = overview(bob);
    assertThat(overview.path("grossAssets").asString()).isEqualTo("800000.00");
    assertThat(overview.path("liabilities").asString()).isEqualTo("300000.00");
    assertThat(overview.path("netWorth").asString()).isEqualTo("500000.00");
    assertThat(overview.path("completeness").asString()).isEqualTo("ESTIMATED");
    boolean stale = false;
    for (JsonNode item : overview.path("items")) {
      stale = stale || item.path("stale").asBoolean();
    }
    assertThat(stale).isTrue();
  }

  @Test
  void isolaItensManuaisEntreUsuarios() throws Exception {
    String aliceItem =
        createManualItem("ASSET", "OTHER", "Ativo Alice", "100.00", freshDate(), null)
            .path("id")
            .asString();

    assertThat(manualItems(bob).path("items")).isEmpty();
    assertThat(overview(bob).path("items")).isEmpty();

    bob.get("/api/v1/wealth/manual-items/" + aliceItem).andExpect(status().isNotFound());
    bob.patch(
            "/api/v1/wealth/manual-items/" + aliceItem,
            manualRequest("ASSET", "OTHER", "Invasão", "1.00", freshDate(), null))
        .andExpect(status().isNotFound());
    bob.delete("/api/v1/wealth/manual-items/" + aliceItem).andExpect(status().isNotFound());

    assertThat(manualItems(alice).path("items")).hasSize(1);
  }

  @Test
  void validaEAtualizaItensManuais() throws Exception {
    JsonNode created =
        createManualItem("ASSET", "VEHICLE", "Carro", "50000.00", freshDate(), "Observação");
    assertThat(created.path("kind").asString()).isEqualTo("ASSET");
    assertThat(created.path("referenceValue").asString()).isEqualTo("50000.00");

    String id = created.path("id").asString();
    JsonNode updated =
        json(
            alice
                .patch(
                    "/api/v1/wealth/manual-items/" + id,
                    manualRequest(
                        "LIABILITY",
                        "FINANCING",
                        "Financiamento do carro",
                        "30000.00",
                        freshDate(),
                        null))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(updated.path("kind").asString()).isEqualTo("LIABILITY");
    assertThat(updated.path("referenceValue").asString()).isEqualTo("30000.00");

    alice.delete("/api/v1/wealth/manual-items/" + id).andExpect(status().isNoContent());
    alice.get("/api/v1/wealth/manual-items/" + id).andExpect(status().isNotFound());

    alice
        .post(
            "/api/v1/wealth/manual-items",
            manualRequest("INVALID", "OTHER", "X", "1.00", freshDate(), null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("kind"));
    alice
        .post(
            "/api/v1/wealth/manual-items",
            manualRequest("ASSET", "OTHER", "X", "-1.00", freshDate(), null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("referenceValue"));
    alice
        .post(
            "/api/v1/wealth/manual-items", manualRequest("ASSET", "OTHER", "X", "1.00", null, null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("referenceDate"));
  }

  private void createCardTransaction(String amount, String postedOn) throws Exception {
    createCardTransaction(cardId, amount, postedOn);
  }

  private void createCardTransaction(String accountId, String amount, String postedOn)
      throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", accountId);
    request.put("type", "DEBIT");
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", "Compra " + amount);
    alice.post("/api/v1/transactions", request).andExpect(status().isCreated());
  }

  private JsonNode overview(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/wealth/overview")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode createManualItem(
      String kind, String type, String description, String value, LocalDate date, String notes)
      throws Exception {
    return createManualItem(alice, kind, type, description, value, date, notes);
  }

  private JsonNode createManualItem(
      AuthTestClient client,
      String kind,
      String type,
      String description,
      String value,
      LocalDate date,
      String notes)
      throws Exception {
    return json(
        client
            .post(
                "/api/v1/wealth/manual-items",
                manualRequest(kind, type, description, value, date, notes))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode manualItems(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/wealth/manual-items")
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

  private JsonNode find(JsonNode items, String sourceType, String sourceId) {
    for (JsonNode item : items) {
      if (!sourceType.equals(item.path("sourceType").asString())) {
        continue;
      }
      if (sourceId == null || sourceId.equals(item.path("sourceId").asString())) {
        return item;
      }
    }
    throw new AssertionError("Item não encontrado: " + sourceType + " " + sourceId);
  }

  private static Map<String, Object> manualRequest(
      String kind, String type, String description, String value, LocalDate date, String notes) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("kind", kind);
    body.put("type", type);
    body.put("description", description);
    body.put("referenceValue", value);
    if (date != null) {
      body.put("referenceDate", date.toString());
    }
    if (notes != null) {
      body.put("notes", notes);
    }
    return body;
  }

  private static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  private static LocalDate freshDate() {
    return today().minusDays(1);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
