package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
 * Regras financeiras do dashboard (T091).
 *
 * <p>Prova que resultado do período e saldo atual são distintos, que apenas contas ativas compõem o
 * saldo, que cartões ficam em passivo separado, que compra/pagamento de fatura não duplicam
 * despesas, que pendentes não entram nos totais e que a soma das categorias e do drill-down
 * corresponde aos indicadores.
 */
@AutoConfigureMockMvc
class FinancialRulesIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.regras";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-regras";

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
  private String activeBankId;
  private String activeCashId;
  private String activeWalletId;
  private String inactiveAccountId;
  private String importedBankId;
  private String cardId;
  private String invoiceId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();

    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-regras", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-regras",
        new AccountFixture(
            "acc-regras-bank", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addAccount(
        "item-regras",
        new AccountFixture(
            "acc-regras-card", "CREDIT", "CREDIT_CARD", "Cartão Alfa", "0.00", null));
    PLUGGY.addTransaction(
        "acc-regras-card",
        new TransactionFixture(
            "tx-regras-card",
            "acc-regras-card",
            "Compra cartão",
            "100.00",
            "2026-10-03",
            "POSTED",
            "Compras"));
    PLUGGY.addTransaction(
        "acc-regras-bank",
        new TransactionFixture(
            "tx-regras-pending",
            "acc-regras-bank",
            "Pendente",
            "-30.00",
            "2026-10-04",
            "PENDING",
            "Outros"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-regras"))
        .andExpect(status().isCreated());

    for (JsonNode account : accounts().path("items")) {
      if ("CREDIT".equals(account.path("type").asString())) {
        cardId = account.path("id").asString();
      } else {
        importedBankId = account.path("id").asString();
      }
    }
    alice
        .put(
            "/api/v1/cards/" + cardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "5000.00"))
        .andExpect(status().isOk());
    invoiceId = invoices().path("items").get(0).path("id").asString();
    addInvoiceItem("INTEREST", "EXPENSE_INCREASE", "Juros", "20.00", "2026-10-07");
    addInvoiceItem("CREDIT", "EXPENSE_DECREASE", "Crédito", "10.00", "2026-10-08");

    activeBankId = createManualAccount("BANK", "Conta Casa", "100.00", "BRL");
    activeCashId = createManualAccount("CASH", "Carteira", "50.00", "BRL");
    activeWalletId = createManualAccount("WALLET", "Carteira digital", "25.00", "BRL");
    inactiveAccountId = createManualAccount("BANK", "Conta antiga", "999.00", "BRL");
    createManualTransaction("DEBIT", inactiveAccountId, "40.00", "Despesa antiga", "2026-10-02");
    alice
        .patch("/api/v1/accounts/" + inactiveAccountId, Map.of("status", "INACTIVE"))
        .andExpect(status().isOk());

    String closedId = createManualAccount("BANK", "Conta encerrada", "888.00", "BRL");
    String unavailableId = createManualAccount("BANK", "Conta indisponível", "777.00", "BRL");
    jdbcTemplate.update(
        "update financial_account set status = 'CLOSED' where id = ?", UUID.fromString(closedId));
    jdbcTemplate.update(
        "update financial_account set status = 'UNAVAILABLE' where id = ?",
        UUID.fromString(unavailableId));
  }

  @Test
  void resultadoDoPeriodoSaldoAtualCategoriasEDrillDownSaoConsistentes() throws Exception {
    createManualTransaction("CREDIT", activeBankId, "500.00", "Salário", "2026-10-02");
    createManualTransaction("DEBIT", activeCashId, "100.00", "Feira", "2026-10-03");
    String original =
        createManualTransaction("DEBIT", activeBankId, "80.00", "Compra", "2026-10-04");
    createManualTransaction(
        "TRANSFER", activeCashId, "50.00", "Para banco", "2026-10-05", activeBankId);
    refund(original, "30.00", "2026-10-05");

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31&period=MONTH");
    assertThat(summary.path("income").asString()).isEqualTo("500.00");
    // 40 (conta inativa, histórico preservado) + 100 + 80 - 30 + 100 (cartão) + 20 - 10.
    assertThat(summary.path("expenses").asString()).isEqualTo("300.00");
    assertThat(summary.path("periodResult").asString()).isEqualTo("200.00");
    assertThat(summary.path("currentBalance").asString()).isEqualTo("1525.00");
    assertThat(summary.path("cardOutstandingBalance").asString()).isEqualTo("110.00");
    assertThat(summary.path("pendingCount").asInt()).isEqualTo(1);
    assertThat(summary.path("pendingExpenses").asString()).isEqualTo("30.00");
    assertThat(summary.has("cashFlow")).isFalse();
    assertThat(summary.has("flowResult")).isFalse();

    JsonNode categories =
        json(
            alice
                .get("/api/v1/dashboard/categories?from=2026-10-01&to=2026-10-31")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    BigDecimal categoryTotal = BigDecimal.ZERO;
    for (JsonNode category : categories) {
      categoryTotal = categoryTotal.add(new BigDecimal(category.path("amount").asString()));
    }
    assertThat(categoryTotal.setScale(2)).isEqualByComparingTo("300.00");

    JsonNode entries = entries("from=2026-10-01&to=2026-10-31&limit=100");
    assertThat(entries.path("items")).hasSize(8);
    BigDecimal entrySum = BigDecimal.ZERO;
    for (JsonNode entry : entries.path("items")) {
      entrySum = entrySum.add(new BigDecimal(entry.path("amount").asString()));
    }
    assertThat(entrySum.setScale(2)).isEqualByComparingTo("200.00");
  }

  @Test
  void pagamentoDeFaturaNaoDuplicaDespesaEReduzSaldoDevedor() throws Exception {
    JsonNode before = summary("from=2026-10-01&to=2026-10-31");
    assertThat(before.path("expenses").asString()).isEqualTo("150.00");
    assertThat(before.path("cardOutstandingBalance").asString()).isEqualTo("110.00");

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/payments",
            Map.of("paymentAccountId", importedBankId, "amount", "50.00", "paidOn", "2026-10-20"))
        .andExpect(status().isCreated());

    JsonNode after = summary("from=2026-10-01&to=2026-10-31");
    assertThat(after.path("expenses").asString()).isEqualTo("150.00");
    assertThat(after.path("cardOutstandingBalance").asString()).isEqualTo("60.00");
    assertThat(after.path("periodResult").asString()).isEqualTo("-150.00");
  }

  @Test
  void transacaoPendenteEntraUmaUnicaVezAposEfetivacao() throws Exception {
    JsonNode pending =
        json(
            alice
                .get("/api/v1/transactions?status=PENDING")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(pending.path("items")).hasSize(1);
    String pendingId = pending.path("items").get(0).path("id").asString();

    JsonNode before = summary("from=2026-10-01&to=2026-10-31");
    assertThat(before.path("expenses").asString()).isEqualTo("150.00");
    assertThat(before.path("pendingCount").asInt()).isEqualTo(1);

    jdbcTemplate.update(
        "update transaction set status = 'POSTED', updated_at = now() where id = ?",
        UUID.fromString(pendingId));
    invoices();

    JsonNode after = summary("from=2026-10-01&to=2026-10-31");
    assertThat(after.path("expenses").asString()).isEqualTo("180.00");
    assertThat(after.path("pendingCount").asInt()).isZero();

    JsonNode entries = entries("from=2026-10-01&to=2026-10-31&limit=100");
    long matching =
        java.util.stream.StreamSupport.stream(entries.path("items").spliterator(), false)
            .filter(item -> pendingId.equals(item.path("id").asString()))
            .count();
    assertThat(matching).isEqualTo(1);
  }

  @Test
  void moedaDiferenteDeBrlNaoEntraNosTotaisConsolidados() throws Exception {
    String usd = createManualAccount("BANK", "Conta dólar", "500.00", "USD");
    createManualTransaction("CREDIT", usd, "900.00", "Receita em dólar", "2026-10-06");

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31");
    assertThat(summary.path("income").asString()).isEqualTo("0.00");
    assertThat(summary.path("expenses").asString()).isEqualTo("150.00");
    assertThat(summary.path("currentBalance").asString()).isEqualTo("1175.00");
  }

  private String createManualAccount(String type, String alias, String balance, String currency)
      throws Exception {
    JsonNode account =
        json(
            alice
                .post(
                    "/api/v1/accounts",
                    Map.of(
                        "type", type,
                        "alias", alias,
                        "referenceBalance", balance,
                        "referenceDate", "2026-10-01T12:00:00Z",
                        "currency", currency))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return account.path("id").asString();
  }

  private String createManualTransaction(
      String type, String accountId, String amount, String description, String postedOn)
      throws Exception {
    return createManualTransaction(type, accountId, amount, description, postedOn, null);
  }

  private String createManualTransaction(
      String type,
      String accountId,
      String amount,
      String description,
      String postedOn,
      String transferAccountId)
      throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", accountId);
    request.put("type", type);
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", description);
    if (transferAccountId != null) {
      request.put("transferAccountId", transferAccountId);
    }
    JsonNode transaction =
        json(
            alice
                .post("/api/v1/transactions", request)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return transaction.path("id").asString();
  }

  private void refund(String originalId, String amount, String postedOn) throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", activeBankId);
    request.put("type", "REFUND");
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", "Estorno");
    request.put("relatedTransactionId", originalId);
    alice.post("/api/v1/transactions", request).andExpect(status().isCreated());
  }

  private void addInvoiceItem(
      String kind, String reportingEffect, String description, String amount, String occurredOn)
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

  private JsonNode accounts() throws Exception {
    return json(
        alice
            .get("/api/v1/accounts")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode invoices() throws Exception {
    return json(
        alice
            .get("/api/v1/cards/" + cardId + "/invoices")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
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

  private JsonNode entries(String query) throws Exception {
    return json(
        alice
            .get("/api/v1/reports/entries?" + query)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
