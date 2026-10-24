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
 * contagem, snapshots datados, lacunas, dados desatualizados, histórico insuficiente e isolamento.
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
        cardId = account.path("id").asString();
      }
      if ("BANK".equals(account.path("type").asString())) {
        bankId = account.path("id").asString();
      }
    }
    assertThat(cardId).isNotBlank();
    assertThat(bankId).isNotBlank();

    alice
        .put(
            "/api/v1/cards/" + cardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "5000.00"))
        .andExpect(status().isOk());
    createCardTransaction("-100.00", "2026-10-03");
    alice.get("/api/v1/cards/" + cardId + "/invoices").andExpect(status().isOk());

    createInvestment(2, "50.00");
  }

  @Test
  void consolidaAtivosEPassivosSemDuplicarComprasEFaturas() throws Exception {
    createManualItem("ASSET", "INVESTMENT", "Tesouro reserva", "500.00", freshDate(), null);
    createManualItem("LIABILITY", "LOAN", "Empréstimo", "200.00", freshDate(), null);

    JsonNode overview = overview(alice);
    assertThat(overview.path("grossAssets").asString()).isEqualTo("1600.00");
    assertThat(overview.path("liabilities").asString()).isEqualTo("300.00");
    assertThat(overview.path("netWorth").asString()).isEqualTo("1300.00");
    assertThat(overview.path("hasData").asBoolean()).isTrue();
    assertThat(overview.path("completeness").asString()).isEqualTo("COMPLETE");
    assertThat(overview.path("referenceDate").asString()).isEqualTo(today().toString());

    JsonNode items = overview.path("items");
    assertThat(items).hasSize(5);
    assertThat(find(items, "ACCOUNT", bankId).path("value").asString()).isEqualTo("1000.00");
    assertThat(find(items, "CARD", cardId).path("value").asString()).isEqualTo("100.00");
    assertThat(find(items, "INVESTMENT", null).path("value").asString()).isEqualTo("100.00");
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
    assertThat(assetSum.toPlainString()).isEqualTo("1600.00");
    assertThat(liabilitySum.toPlainString()).isEqualTo("300.00");
    assertThat(items.toString()).doesNotContain("1100.00");
  }

  @Test
  void registraSnapshotDatadoEAtualizaNoMesmoDia() throws Exception {
    createManualItem("ASSET", "INVESTMENT", "Reserva", "500.00", freshDate(), null);
    createManualItem("LIABILITY", "LOAN", "Empréstimo", "200.00", freshDate(), null);

    JsonNode first = createSnapshot("Fechamento do dia");
    assertThat(first.path("referenceDate").asString()).isEqualTo(today().toString());
    assertThat(first.path("grossAssets").asString()).isEqualTo("1600.00");
    assertThat(first.path("liabilities").asString()).isEqualTo("300.00");
    assertThat(first.path("netWorth").asString()).isEqualTo("1300.00");
    assertThat(first.path("completeness").asString()).isEqualTo("COMPLETE");
    assertThat(first.path("items")).hasSize(5);

    createManualItem("ASSET", "CASH", "Reserva extra", "100.00", freshDate(), null);
    JsonNode updated = createSnapshot("Atualizado");
    assertThat(updated.path("id").asString()).isEqualTo(first.path("id").asString());
    assertThat(updated.path("netWorth").asString()).isEqualTo("1400.00");
    assertThat(updated.path("items")).hasSize(6);

    JsonNode snapshots = snapshots(alice);
    assertThat(snapshots.path("items")).hasSize(1);
    assertThat(snapshots.path("items").get(0).path("netWorth").asString()).isEqualTo("1400.00");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_event where event_type = 'WEALTH_SNAPSHOT_CREATED'",
                Long.class))
        .isEqualTo(2L);
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
    assertThat(overview.path("grossAssets").asString()).isEqualTo("1600.00");
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
  void isolaItensESnapshotsEntreUsuarios() throws Exception {
    String aliceItem =
        createManualItem("ASSET", "OTHER", "Ativo Alice", "100.00", freshDate(), null)
            .path("id")
            .asString();
    createSnapshot(null);

    assertThat(snapshots(bob).path("items")).isEmpty();
    assertThat(manualItems(bob).path("items")).isEmpty();
    assertThat(overview(bob).path("items")).isEmpty();

    bob.get("/api/v1/wealth/manual-items/" + aliceItem).andExpect(status().isNotFound());
    bob.patch(
            "/api/v1/wealth/manual-items/" + aliceItem,
            manualRequest("ASSET", "OTHER", "Invasão", "1.00", freshDate(), null))
        .andExpect(status().isNotFound());
    bob.delete("/api/v1/wealth/manual-items/" + aliceItem).andExpect(status().isNotFound());

    assertThat(manualItems(alice).path("items")).hasSize(1);
    assertThat(snapshots(alice).path("items")).hasSize(1);
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

  private void createInvestment(int quantity, String price) throws Exception {
    alice
        .post(
            "/api/v1/investments/positions",
            Map.of(
                "broker",
                "XP",
                "assetName",
                "Fundo imobiliário",
                "ticker",
                "HGLG11",
                "quantity",
                String.valueOf(quantity),
                "referencePrice",
                price,
                "referenceDate",
                today().toString()))
        .andExpect(status().isCreated());
  }

  private void createCardTransaction(String amount, String postedOn) throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", cardId);
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

  private JsonNode createSnapshot(String notes) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    if (notes != null) {
      body.put("notes", notes);
    }
    return json(
        alice
            .post("/api/v1/wealth/snapshots", body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode snapshots(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/wealth/snapshots")
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
