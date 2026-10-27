package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 * Contrato e integração do dashboard e de {@code /reports/entries} (T090).
 *
 * <p>Cobre schema, filtros, 422 para intervalo amplo, pendentes sem total líquido, última
 * sincronização conforme filtro, saldo devedor de cartões, origem no drill-down, paginação e
 * isolamento entre dois usuários.
 */
@AutoConfigureMockMvc
class DashboardIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.dashboard";
  private static final String BOB = "bob.dashboard";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-dashboard";
  private static final String BOB_CLIENT = "client-bob-dashboard";

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
  private AuthTestClient bob;
  private String aliceBankId;
  private String aliceCardId;
  private String aliceCashId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();

    PLUGGY.registerClient(ALICE_CLIENT, "secret-alice");
    PLUGGY.registerClient(BOB_CLIENT, "secret-bob");
    PLUGGY.addItem("item-alice-dashboard", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice-dashboard",
        new AccountFixture(
            "acc-alice-bank", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addAccount(
        "item-alice-dashboard",
        new AccountFixture("acc-alice-card", "CREDIT", "CREDIT_CARD", "Cartão Alfa", "0.00", null));
    PLUGGY.addTransaction(
        "acc-alice-bank",
        new TransactionFixture(
            "tx-alice-market",
            "acc-alice-bank",
            "Mercado",
            "-100.00",
            "2026-10-05",
            "POSTED",
            "Mercado"));
    PLUGGY.addTransaction(
        "acc-alice-bank",
        new TransactionFixture(
            "tx-alice-pending",
            "acc-alice-bank",
            "Farmácia",
            "-30.00",
            "2026-10-06",
            "PENDING",
            "Saúde"));
    PLUGGY.addTransaction(
        "acc-alice-card",
        new TransactionFixture(
            "tx-alice-card",
            "acc-alice-card",
            "Compra cartão",
            "300.00",
            "2026-10-03",
            "POSTED",
            "Mercado"));

    PLUGGY.addItem("item-bob-dashboard", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-bob-dashboard",
        new AccountFixture(
            "acc-bob-bank", "BANK", "CHECKING_ACCOUNT", "Conta Beta", "500.00", null));
    PLUGGY.addTransaction(
        "acc-bob-bank",
        new TransactionFixture(
            "tx-bob-market",
            "acc-bob-bank",
            "Mercado Bob",
            "-50.00",
            "2026-10-05",
            "POSTED",
            "Mercado"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);

    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", ALICE_CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    bob.put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", BOB_CLIENT, "clientSecret", "secret-bob"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-dashboard"))
        .andExpect(status().isCreated());
    bob.post("/api/v1/connections", Map.of("itemId", "item-bob-dashboard"))
        .andExpect(status().isCreated());

    String aliceConnectionId =
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
        .post("/api/v1/connections/" + aliceConnectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("dashboard-it", "dashboard-it");

    for (JsonNode account : accounts(alice).path("items")) {
      if ("CREDIT".equals(account.path("type").asString())) {
        aliceCardId = account.path("id").asString();
      } else {
        aliceBankId = account.path("id").asString();
      }
    }
    alice
        .put(
            "/api/v1/cards/" + aliceCardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "5000.00"))
        .andExpect(status().isOk());

    aliceCashId = createManualAccount("CASH", "Carteira", "100.00");
    String categoryId = createCategory("Feira");
    createManualTransaction("CREDIT", aliceCashId, "1000.00", "Salário", "2026-10-02", null);
    createManualTransaction("DEBIT", aliceCashId, "200.00", "Feira", "2026-10-03", categoryId);
    createManualTransaction(
        "TRANSFER", aliceCashId, "50.00", "Para banco", "2026-10-04", null, aliceBankId);

    String invoiceId = invoices().path("items").get(0).path("id").asString();
    addInvoiceItem(invoiceId, "INTEREST", "EXPENSE_INCREASE", "Juros", "20.00", "2026-10-07");
    addInvoiceItem(invoiceId, "CREDIT", "EXPENSE_DECREASE", "Crédito", "10.00", "2026-10-08");
  }

  @Test
  void resumoTemResultadoSaldoPendentesSincronizacaoECartaoSeparados() throws Exception {
    JsonNode summary = summary(alice, "from=2026-10-01&to=2026-10-31&period=MONTH");

    assertThat(summary.path("periodStart").asString()).isEqualTo("2026-10-01");
    assertThat(summary.path("periodEnd").asString()).isEqualTo("2026-10-31");
    assertThat(summary.path("income").asString()).isEqualTo("1000.00");
    assertThat(summary.path("expenses").asString()).isEqualTo("610.00");
    assertThat(summary.path("periodResult").asString()).isEqualTo("390.00");

    assertThat(summary.path("currentBalance").asString()).isEqualTo("1850.00");
    assertThat(summary.path("currentBalanceReferenceAt").asString()).isNotBlank();
    assertThat(summary.path("cardOutstandingBalance").asString()).isEqualTo("310.00");
    assertThat(summary.path("lastSuccessfulSyncAt").asString()).isNotBlank();

    assertThat(summary.path("pendingCount").asInt()).isEqualTo(1);
    assertThat(summary.path("pendingIncome").asString()).isEqualTo("0.00");
    assertThat(summary.path("pendingExpenses").asString()).isEqualTo("30.00");
    assertThat(summary.path("pendingInternalMatches").asInt()).isZero();
    assertThat(summary.has("cashFlow")).isFalse();
    assertThat(summary.has("netPending")).isFalse();

    alice
        .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expenses").value("610.00"))
        .andExpect(jsonPath("$.periodResult").value("390.00"));
  }

  @Test
  void pagamentoDeFaturaComContaManualReduzSaldoAtualSemAlterarReferencia() throws Exception {
    String invoiceId = invoices().path("items").get(0).path("id").asString();
    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/payments",
            Map.of(
                "paymentAccountId", aliceCashId,
                "amount", "50.00",
                "paidOn", "2026-10-20"))
        .andExpect(status().isCreated());

    JsonNode cash = null;
    for (JsonNode account : accounts(alice).path("items")) {
      if (aliceCashId.equals(account.path("id").asString())) {
        cash = account;
      }
    }
    assertThat(cash).isNotNull();
    assertThat(cash.path("currentBalance").asString()).isEqualTo("800.00");
    assertThat(cash.path("referenceBalance").asString()).isEqualTo("100.00");
  }

  @Test
  void creditoDeCartaoReduzDespesaSemAumentarReceita() throws Exception {
    createManualTransaction(
        "CREDIT", aliceCardId, "50.00", "Crédito do cartão", "2026-10-09", null);
    invoices();

    JsonNode summary = summary(alice, "from=2026-10-01&to=2026-10-31&period=MONTH");
    assertThat(summary.path("income").asString()).isEqualTo("1000.00");
    // O crédito de 50 entrou depois do fechamento e vira crédito para o próximo ciclo.
    assertThat(summary.path("expenses").asString()).isEqualTo("600.00");
    // O crédito entrou depois do fechamento: vira crédito para o próximo ciclo, não reduz a
    // fatura já fechada.
    assertThat(summary.path("cardOutstandingBalance").asString()).isEqualTo("310.00");
  }

  @Test
  void compraParceladaContaUmaParcelaPorCicloNoDashboard() throws Exception {
    JsonNode cardTransactions =
        json(
            alice
                .get("/api/v1/transactions?limit=100&accountId=" + aliceCardId)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode purchase = cardTransactions.path("items").get(0);
    String transactionId = purchase.path("id").asString();
    String firstInvoiceId = purchase.path("invoiceId").asString();

    alice
        .post(
            "/api/v1/invoices/" + firstInvoiceId + "/items",
            Map.of(
                "kind", "INSTALLMENT",
                "reportingEffect", "NONE",
                "description", "Compra parcelada",
                "amount", "100.00",
                "occurredOn", "2026-10-03",
                "transactionId", transactionId,
                "installmentNumber", 1,
                "totalInstallments", 3))
        .andExpect(status().isCreated());

    JsonNode october = summary(alice, "from=2026-10-01&to=2026-10-31&period=MONTH");
    assertThat(october.path("income").asString()).isEqualTo("1000.00");
    assertThat(october.path("expenses").asString()).isEqualTo("410.00");
    assertThat(october.path("cardOutstandingBalance").asString()).isEqualTo("310.00");

    JsonNode november = summary(alice, "from=2026-11-01&to=2026-11-30&period=MONTH");
    assertThat(november.path("expenses").asString()).isEqualTo("100.00");
    JsonNode december = summary(alice, "from=2026-12-01&to=2026-12-31&period=MONTH");
    assertThat(december.path("expenses").asString()).isEqualTo("100.00");
  }

  @Test
  void filtrosAplicamContaOrigemCategoriaEFaixaDeValor() throws Exception {
    JsonNode cashOnly = summary(alice, "from=2026-10-01&to=2026-10-31&accountId=" + aliceCashId);
    assertThat(cashOnly.path("income").asString()).isEqualTo("1000.00");
    assertThat(cashOnly.path("expenses").asString()).isEqualTo("200.00");
    assertThat(cashOnly.path("currentBalance").asString()).isEqualTo("850.00");
    assertThat(cashOnly.path("cardOutstandingBalance").asString()).isEqualTo("0.00");
    assertThat(cashOnly.path("pendingCount").asInt()).isZero();
    assertThat(cashOnly.path("lastSuccessfulSyncAt").isNull()).isTrue();

    JsonNode manualOrigin = summary(alice, "from=2026-10-01&to=2026-10-31&origin=MANUAL");
    assertThat(manualOrigin.path("income").asString()).isEqualTo("1000.00");
    // Inclui os itens manuais da fatura (juros +20 e crédito -10).
    assertThat(manualOrigin.path("expenses").asString()).isEqualTo("210.00");

    JsonNode range = summary(alice, "from=2026-10-01&to=2026-10-31&minAmount=150&maxAmount=300");
    assertThat(range.path("income").asString()).isEqualTo("0.00");
    assertThat(range.path("expenses").asString()).isEqualTo("500.00");

    JsonNode byCategory =
        summary(alice, "from=2026-10-01&to=2026-10-31&categoryId=" + categoryId("Feira"));
    assertThat(byCategory.path("expenses").asString()).isEqualTo("200.00");
    assertThat(byCategory.path("income").asString()).isEqualTo("0.00");

    alice
        .get("/api/v1/dashboard/summary?from=2020-01-01&to=2026-12-31")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void filtroAceitaMultiplasContasECartoes() throws Exception {
    JsonNode cashAndBank =
        summary(
            alice,
            "from=2026-10-01&to=2026-10-31&accountId=" + aliceCashId + "&accountId=" + aliceBankId);
    assertThat(cashAndBank.path("income").asString()).isEqualTo("1000.00");
    assertThat(cashAndBank.path("expenses").asString()).isEqualTo("300.00");
    assertThat(cashAndBank.path("currentBalance").asString()).isEqualTo("1850.00");
    assertThat(cashAndBank.path("cardOutstandingBalance").asString()).isEqualTo("0.00");
    assertThat(cashAndBank.path("pendingCount").asInt()).isEqualTo(1);

    JsonNode cashAndCard =
        summary(
            alice,
            "from=2026-10-01&to=2026-10-31&accountId=" + aliceCashId + "&accountId=" + aliceCardId);
    assertThat(cashAndCard.path("expenses").asString()).isEqualTo("510.00");
    assertThat(cashAndCard.path("currentBalance").asString()).isEqualTo("850.00");
    assertThat(cashAndCard.path("cardOutstandingBalance").asString()).isEqualTo("310.00");

    JsonNode entries =
        json(
            alice
                .get(
                    "/api/v1/reports/entries?from=2026-10-01&to=2026-10-31&limit=100"
                        + "&accountId="
                        + aliceCashId
                        + "&accountId="
                        + aliceBankId)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    BigDecimal sum = BigDecimal.ZERO;
    for (JsonNode entry : entries.path("items")) {
      sum = sum.add(new BigDecimal(entry.path("amount").asString()));
    }
    assertThat(sum.setScale(2)).isEqualByComparingTo("700.00");
  }

  @Test
  void categoriasSerieTemporalEDrillDownBatemComOsTotais() throws Exception {
    JsonNode categories =
        json(
            alice
                .get("/api/v1/dashboard/categories?from=2026-10-01&to=2026-10-31&period=MONTH")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    BigDecimal categoryTotal = BigDecimal.ZERO;
    for (JsonNode category : categories) {
      categoryTotal = categoryTotal.add(new BigDecimal(category.path("amount").asString()));
    }
    assertThat(categoryTotal.setScale(2)).isEqualByComparingTo("610.00");
    assertThat(findCategory(categories, "Feira").path("amount").asString()).isEqualTo("200.00");

    JsonNode series =
        json(
            alice
                .get("/api/v1/dashboard/timeseries?from=2026-10-01&to=2026-10-31&period=MONTH")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(series).hasSize(31);
    BigDecimal seriesIncome = BigDecimal.ZERO;
    BigDecimal seriesExpenses = BigDecimal.ZERO;
    for (JsonNode bucket : series) {
      seriesIncome = seriesIncome.add(new BigDecimal(bucket.path("income").asString()));
      seriesExpenses = seriesExpenses.add(new BigDecimal(bucket.path("expenses").asString()));
    }
    assertThat(seriesIncome.setScale(2)).isEqualByComparingTo("1000.00");
    assertThat(seriesExpenses.setScale(2)).isEqualByComparingTo("610.00");

    JsonNode entries = entries(alice, "from=2026-10-01&to=2026-10-31&limit=100");
    assertThat(entries.path("items")).hasSize(6);
    BigDecimal entrySum = BigDecimal.ZERO;
    boolean sawInvoiceItem = false;
    for (JsonNode entry : entries.path("items")) {
      entrySum = entrySum.add(new BigDecimal(entry.path("amount").asString()));
      if ("INVOICE_ITEM".equals(entry.path("source").asString())) {
        sawInvoiceItem = true;
        assertThat(entry.path("transactionId").isNull()).isTrue();
        assertThat(entry.path("origin").asString()).isEqualTo("MANUAL");
      }
    }
    assertThat(sawInvoiceItem).isTrue();
    assertThat(entrySum.setScale(2)).isEqualByComparingTo("390.00");

    JsonNode firstPage = entries(alice, "from=2026-10-01&to=2026-10-31&limit=2");
    assertThat(firstPage.path("items")).hasSize(2);
    assertThat(firstPage.path("hasMore").asBoolean()).isTrue();
    List<String> seen = new ArrayList<>();
    firstPage.path("items").forEach(item -> seen.add(item.path("id").asString()));
    String cursor = firstPage.path("nextCursor").asString();
    while (cursor != null && !cursor.isBlank()) {
      JsonNode page =
          entries(
              alice,
              "from=2026-10-01&to=2026-10-31&limit=2&cursor="
                  + java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8));
      page.path("items").forEach(item -> seen.add(item.path("id").asString()));
      cursor = page.path("nextCursor").asString(null);
    }
    assertThat(seen).hasSize(6).doesNotHaveDuplicates();
  }

  @Test
  void relatoriosSaoIsoladosEntreUsuarios() throws Exception {
    JsonNode bobSummary = summary(bob, "from=2026-10-01&to=2026-10-31");
    assertThat(bobSummary.path("expenses").asString()).isEqualTo("50.00");
    assertThat(bobSummary.path("income").asString()).isEqualTo("0.00");

    JsonNode bobEntries = entries(bob, "from=2026-10-01&to=2026-10-31&limit=100");
    assertThat(bobEntries.path("items")).hasSize(1);
    assertThat(bobEntries.toString()).contains("Mercado Bob").doesNotContain("Compra cartão");

    alice
        .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31&accountId=" + bobBankId())
        .andExpect(status().isNotFound());
  }

  private String bobBankId() throws Exception {
    return accounts(bob).path("items").get(0).path("id").asString();
  }

  private String createManualAccount(String type, String alias, String balance) throws Exception {
    JsonNode account =
        json(
            alice
                .post(
                    "/api/v1/accounts",
                    Map.of(
                        "type", type,
                        "alias", alias,
                        "referenceBalance", balance,
                        "referenceDate", "2026-10-01T12:00:00Z"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return account.path("id").asString();
  }

  private String createCategory(String name) throws Exception {
    JsonNode category =
        json(
            alice
                .post("/api/v1/categories", Map.of("name", name, "kind", "EXPENSE"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return category.path("id").asString();
  }

  private String categoryId(String name) throws Exception {
    JsonNode categories =
        json(
            alice
                .get("/api/v1/categories")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode category : categories.path("items")) {
      if (name.equals(category.path("name").asString())) {
        return category.path("id").asString();
      }
    }
    throw new AssertionError("Categoria não encontrada: " + name);
  }

  private void createManualTransaction(
      String type,
      String accountId,
      String amount,
      String description,
      String postedOn,
      String categoryId)
      throws Exception {
    createManualTransaction(type, accountId, amount, description, postedOn, categoryId, null);
  }

  private void createManualTransaction(
      String type,
      String accountId,
      String amount,
      String description,
      String postedOn,
      String categoryId,
      String transferAccountId)
      throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", accountId);
    request.put("type", type);
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", description);
    if (categoryId != null) {
      request.put("categoryId", categoryId);
    }
    if (transferAccountId != null) {
      request.put("transferAccountId", transferAccountId);
    }
    alice.post("/api/v1/transactions", request).andExpect(status().isCreated());
  }

  private void addInvoiceItem(
      String invoiceId,
      String kind,
      String reportingEffect,
      String description,
      String amount,
      String occurredOn)
      throws Exception {
    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", kind,
                "reportingEffect", reportingEffect,
                "description", description,
                "amount", amount,
                "occurredOn", occurredOn))
        .andExpect(status().isCreated());
  }

  private JsonNode invoices() throws Exception {
    return json(
        alice
            .get("/api/v1/cards/" + aliceCardId + "/invoices")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode accounts(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/accounts")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode summary(AuthTestClient client, String query) throws Exception {
    return json(
        client
            .get("/api/v1/dashboard/summary?" + query)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode entries(AuthTestClient client, String query) throws Exception {
    return json(
        client
            .get("/api/v1/reports/entries?" + query)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static JsonNode findCategory(JsonNode categories, String name) {
    for (JsonNode category : categories) {
      if (name.equals(category.path("categoryName").asString())) {
        return category;
      }
    }
    throw new AssertionError("Categoria não encontrada no dashboard: " + name);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
