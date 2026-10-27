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
import java.math.BigDecimal;
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
 * Reconciliação final de dashboard e drill-down (T186).
 *
 * <p>Cobre viradas de semana, mês e ano em America/Sao_Paulo, filtros inclusivos, pendentes fora
 * dos totais, item de fatura sem transação no drill-down e soma das categorias igual às despesas.
 */
@AutoConfigureMockMvc
class FinalDashboardIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.final.dashboard";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-final-dashboard";

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
  private String bankId;
  private String cardId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-final-dashboard", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-final-dashboard",
        new AccountFixture(
            "acc-final-dashboard-bank", "BANK", "CHECKING_ACCOUNT", "Conta", "1000.00", null));
    PLUGGY.addAccount(
        "item-final-dashboard",
        new AccountFixture(
            "acc-final-dashboard-card", "CREDIT", "CREDIT_CARD", "Cartão", "0.00", null));
    PLUGGY.addTransaction(
        "acc-final-dashboard-card",
        new TransactionFixture(
            "tx-final-dashboard-card",
            "acc-final-dashboard-card",
            "Compra no cartão",
            "200.00",
            "2026-12-29",
            "POSTED",
            "Compras"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-final-dashboard"))
        .andExpect(status().isCreated());
    String connectionId =
        json(alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("items")
            .get(0)
            .path("id")
            .asString();
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("final-dashboard", "final-dashboard");

    for (JsonNode account : accounts().path("items")) {
      if ("CREDIT".equals(account.path("type").asString())) {
        cardId = account.path("id").asString();
      } else {
        bankId = account.path("id").asString();
      }
    }
    alice
        .put(
            "/api/v1/cards/" + cardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "5000.00"))
        .andExpect(status().isOk());

    createTransaction("CREDIT", bankId, "1000.00", "Salário dezembro", "2026-12-28", false);
    createTransaction("DEBIT", bankId, "100.00", "Feira dezembro", "2026-12-31", false);
    createTransaction("DEBIT", bankId, "50.00", "Feira janeiro", "2027-01-01", false);
    createTransaction("DEBIT", bankId, "25.00", "Pendente janeiro", "2027-01-02", true);

    String invoiceId =
        json(alice
                .get("/api/v1/cards/" + cardId + "/invoices")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("items")
            .get(0)
            .path("id")
            .asString();
    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "INTEREST",
                "reportingEffect", "EXPENSE_INCREASE",
                "description", "Juros sem transação",
                "amount", "20.00",
                "occurredOn", "2026-12-30"))
        .andExpect(status().isCreated());
  }

  @Test
  void viradasDePeriodoPendentesEDrilldownBatemComOsTotais() throws Exception {
    JsonNode week = summary("from=2026-12-28&to=2027-01-03");
    assertThat(week.path("periodStart").asString()).isEqualTo("2026-12-28");
    assertThat(week.path("periodEnd").asString()).isEqualTo("2027-01-03");
    assertThat(week.path("income").asString()).isEqualTo("1000.00");
    assertThat(week.path("expenses").asString()).isEqualTo("370.00");
    assertThat(week.path("periodResult").asString()).isEqualTo("630.00");
    assertThat(week.path("pendingCount").asInt()).isEqualTo(1);
    assertThat(week.path("pendingExpenses").asString()).isEqualTo("25.00");
    assertThat(week.has("cashFlow")).isFalse();
    assertThat(week.path("currentBalance").asString()).isEqualTo("1000.00");
    assertThat(week.path("cardOutstandingBalance").asString()).isEqualTo("220.00");

    JsonNode year = summary("from=2026-01-01&to=2026-12-31");
    assertThat(year.path("income").asString()).isEqualTo("1000.00");
    assertThat(year.path("expenses").asString()).isEqualTo("320.00");
    assertThat(year.path("pendingCount").asInt()).isZero();

    JsonNode categories = categories("from=2026-01-01&to=2026-12-31");
    BigDecimal categoryTotal = BigDecimal.ZERO;
    for (JsonNode category : categories) {
      categoryTotal = categoryTotal.add(new BigDecimal(category.path("amount").asString()));
    }
    assertThat(categoryTotal.setScale(2)).isEqualByComparingTo("320.00");

    JsonNode entries = entries("from=2026-01-01&to=2026-12-31");
    BigDecimal entrySum = BigDecimal.ZERO;
    boolean interestWithoutTransaction = false;
    for (JsonNode entry : entries.path("items")) {
      entrySum = entrySum.add(new BigDecimal(entry.path("amount").asString()));
      if ("Juros sem transação".equals(entry.path("description").asString())) {
        interestWithoutTransaction = true;
        assertThat(entry.path("source").asString()).isEqualTo("INVOICE_ITEM");
        assertThat(entry.path("transactionId").isNull()).isTrue();
      }
    }
    assertThat(entrySum.setScale(2)).isEqualByComparingTo("680.00");
    assertThat(interestWithoutTransaction).isTrue();

    JsonNode singleDay = summary("from=2026-12-31&to=2026-12-31");
    assertThat(singleDay.path("expenses").asString()).isEqualTo("100.00");
  }

  private void createTransaction(
      String type,
      String accountId,
      String amount,
      String description,
      String postedOn,
      boolean pending)
      throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", accountId);
    request.put("type", type);
    request.put("amount", amount);
    request.put("description", description);
    request.put("postedOn", postedOn);
    JsonNode created =
        json(
            alice
                .post("/api/v1/transactions", request)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    if (pending) {
      jdbcTemplate.update(
          "update transaction set status = 'PENDING', updated_at = now() where id = ?",
          java.util.UUID.fromString(created.path("id").asString()));
    }
  }

  private JsonNode summary(String query) throws Exception {
    return json(
        alice
            .get("/api/v1/dashboard/summary?" + query)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode categories(String query) throws Exception {
    return json(
        alice
            .get("/api/v1/dashboard/categories?" + query)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode entries(String query) throws Exception {
    return json(
        alice
            .get("/api/v1/reports/entries?" + query + "&limit=100")
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
