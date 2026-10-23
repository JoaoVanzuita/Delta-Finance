package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

/** Ciclos, itens, pagamentos, crédito e faturas sem duplicação de compras (T122). */
@AutoConfigureMockMvc
class InvoiceIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.faturas";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-faturas";

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
  private String cardId;
  private String bankId;

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
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
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
    configureCycle(5, 15, "5000.00");
  }

  @Test
  void configuraCicloELimiteComValidacao() throws Exception {
    JsonNode profile = updateProfile(5, 15, "5000.00");
    assertThat(profile.path("closingDay").asInt()).isEqualTo(5);
    assertThat(profile.path("dueDay").asInt()).isEqualTo(15);
    assertThat(profile.path("creditLimit").asString()).isEqualTo("5000.00");
    assertThat(profile.path("creditBalance").asString()).isEqualTo("0.00");

    alice
        .put("/api/v1/cards/" + cardId + "/profile", Map.of("closingDay", 0, "dueDay", 15))
        .andExpect(status().isUnprocessableEntity());
    alice
        .put("/api/v1/cards/" + cardId + "/profile", Map.of("closingDay", 5, "dueDay", 32))
        .andExpect(status().isUnprocessableEntity());
    alice
        .put(
            "/api/v1/cards/" + cardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "-1.00"))
        .andExpect(status().isUnprocessableEntity());
    alice
        .put("/api/v1/cards/" + bankId + "/profile", Map.of("closingDay", 5, "dueDay", 15))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void compraEPagamentoNaoDuplicamDespesas() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-10-03");
    JsonNode list = invoices();
    assertThat(list.path("items")).hasSize(1);
    JsonNode invoice = list.path("items").get(0);
    assertThat(invoice.path("total").asString()).isEqualTo("100.00");
    assertThat(invoice.path("status").asString()).isEqualTo("CLOSED");
    assertThat(invoice.path("outstanding").asString()).isEqualTo("100.00");

    JsonNode detail = invoiceDetail(invoice.path("id").asString());
    assertThat(detail.path("items")).hasSize(1);
    JsonNode item = detail.path("items").get(0);
    assertThat(item.path("kind").asString()).isEqualTo("PURCHASE");
    assertThat(item.path("reportingEffect").asString()).isEqualTo("NONE");
    assertThat(item.path("transactionId").asString()).isNotBlank();
    assertThat(item.path("amount").asString()).isEqualTo("100.00");

    int transactionsBefore = transactions().path("items").size();
    alice
        .post(
            "/api/v1/invoices/" + invoice.path("id").asString() + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "40.00", "paidOn", "2026-10-20"))
        .andExpect(status().isCreated());

    detail = invoiceDetail(invoice.path("id").asString());
    assertThat(detail.path("status").asString()).isEqualTo("PARTIALLY_PAID");
    assertThat(detail.path("paymentsTotal").asString()).isEqualTo("40.00");
    assertThat(detail.path("outstanding").asString()).isEqualTo("60.00");

    alice
        .post(
            "/api/v1/invoices/" + invoice.path("id").asString() + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "60.00", "paidOn", "2026-10-21"))
        .andExpect(status().isCreated());
    detail = invoiceDetail(invoice.path("id").asString());
    assertThat(detail.path("status").asString()).isEqualTo("PAID");
    assertThat(detail.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(transactions().path("items").size()).isEqualTo(transactionsBefore);
  }

  @Test
  void itensManuaisAplicamEfeitoHistoricoERemocao() throws Exception {
    createCardTransaction("DEBIT", "-200.00", "2026-10-03");
    String invoiceId = invoices().path("items").get(0).path("id").asString();

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "INTEREST",
                "reportingEffect", "EXPENSE_INCREASE",
                "description", "Juros",
                "amount", "20.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("MANUAL"));

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "MANUAL_ADJUSTMENT",
                "reportingEffect", "EXPENSE_DECREASE",
                "description", "Ajuste",
                "amount", "50.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isCreated());

    assertThat(invoiceDetail(invoiceId).path("total").asString()).isEqualTo("170.00");

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "INTEREST",
                "description", "Sem efeito",
                "amount", "1.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isUnprocessableEntity());
    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "PURCHASE",
                "reportingEffect", "NONE",
                "description", "Sem transação",
                "amount", "10.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isUnprocessableEntity());

    JsonNode detail = invoiceDetail(invoiceId);
    JsonNode interest = findItem(detail.path("items"), "INTEREST");
    alice
        .patch(
            "/api/v1/invoice-items/" + interest.path("id").asString(),
            Map.of(
                "kind", "INTEREST",
                "reportingEffect", "EXPENSE_INCREASE",
                "description", "Juros revisado",
                "amount", "25.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctionCount").value(2));
    assertThat(invoiceDetail(invoiceId).path("total").asString()).isEqualTo("175.00");

    JsonNode corrections =
        json(
            alice
                .get("/api/v1/invoice-items/" + interest.path("id").asString() + "/corrections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(corrections).hasSize(2);
    assertThat(corrections.toString()).contains("Juros revisado", "20.00", "25.00");

    JsonNode adjustment = findItem(invoiceDetail(invoiceId).path("items"), "MANUAL_ADJUSTMENT");
    alice
        .delete("/api/v1/invoice-items/" + adjustment.path("id").asString())
        .andExpect(status().isNoContent());
    detail = invoiceDetail(invoiceId);
    assertThat(detail.path("total").asString()).isEqualTo("225.00");
    assertThat(detail.path("hasAdjustments").asBoolean()).isTrue();
    assertThat(detail.path("adjustmentCount").asInt()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void creditoExcedenteECarryForward() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-10-03");
    String firstInvoice = invoices().path("items").get(0).path("id").asString();
    alice
        .post(
            "/api/v1/invoices/" + firstInvoice + "/items",
            Map.of(
                "kind", "CREDIT",
                "reportingEffect", "EXPENSE_DECREASE",
                "description", "Crédito",
                "amount", "150.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isCreated());

    JsonNode first = invoiceDetail(firstInvoice);
    assertThat(first.path("total").asString()).isEqualTo("0.00");
    assertThat(first.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(cardCreditBalance()).isEqualTo("50.00");

    createCardTransaction("DEBIT", "-30.00", "2026-10-10");
    JsonNode list = invoices();
    JsonNode second = null;
    for (JsonNode invoice : list.path("items")) {
      if (!invoice.path("id").asString().equals(firstInvoice)) {
        second = invoice;
      }
    }
    assertThat(second).isNotNull();
    JsonNode secondDetail = invoiceDetail(second.path("id").asString());
    assertThat(secondDetail.path("total").asString()).isEqualTo("30.00");
    assertThat(secondDetail.path("appliedCredit").asString()).isEqualTo("30.00");
    assertThat(secondDetail.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(cardCreditBalance()).isEqualTo("20.00");
  }

  @Test
  void posFechamentoVaiParaCicloSeguinteEMudancaDeCicloNaoReescreve() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-10-03");
    JsonNode first = invoices().path("items").get(0);
    String firstId = first.path("id").asString();
    assertThat(first.path("closingDate").asString()).isEqualTo("2026-10-05");

    // Chega depois do fechamento, mas com data dentro do ciclo já fechado.
    createCardTransaction("DEBIT", "-20.00", "2026-10-04");
    JsonNode list = invoices();
    assertThat(list.path("items")).hasSize(2);
    JsonNode second = null;
    for (JsonNode invoice : list.path("items")) {
      if (!invoice.path("id").asString().equals(firstId)) {
        second = invoice;
      }
    }
    assertThat(second).isNotNull();
    assertThat(second.path("cycleStart").asString()).isEqualTo("2026-10-06");

    configureCycle(10, 20, "5000.00");
    createCardTransaction("DEBIT", "-30.00", "2026-10-08");
    invoices();
    JsonNode firstAfter = invoiceDetail(firstId);
    assertThat(firstAfter.path("closingDate").asString()).isEqualTo("2026-10-05");
    assertThat(firstAfter.path("total").asString()).isEqualTo("100.00");
    assertThat(firstAfter.path("items")).hasSize(1);

    JsonNode future = null;
    for (JsonNode invoice : invoices().path("items")) {
      if ("2026-10-10".equals(invoice.path("closingDate").asString())) {
        future = invoice;
      }
    }
    assertThat(future).isNotNull();
  }

  @Test
  void cartaoSemCicloNaoGeraFaturas() throws Exception {
    createCardTransaction("DEBIT", "-40.00", "2026-10-03");
    jdbcTemplate.update(
        "delete from card_profile where account_id = ?", java.util.UUID.fromString(cardId));

    assertThat(invoices().path("items")).isEmpty();
    JsonNode transaction = transactions().path("items").get(0);
    assertThat(transaction.path("invoiceId").isNull()).isTrue();
  }

  private JsonNode updateProfile(int closing, int due, String limit) throws Exception {
    return json(
        alice
            .put(
                "/api/v1/cards/" + cardId + "/profile",
                Map.of("closingDay", closing, "dueDay", due, "creditLimit", limit))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void configureCycle(int closing, int due, String limit) throws Exception {
    updateProfile(closing, due, limit);
  }

  private void createCardTransaction(String type, String amount, String postedOn) throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", cardId);
    request.put("type", type);
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", "Compra " + amount);
    alice.post("/api/v1/transactions", request).andExpect(status().isCreated());
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

  private JsonNode invoiceDetail(String invoiceId) throws Exception {
    return json(
        alice
            .get("/api/v1/invoices/" + invoiceId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String cardCreditBalance() throws Exception {
    return json(alice
            .get("/api/v1/accounts/" + cardId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("cardProfile")
        .path("creditBalance")
        .asString();
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

  private JsonNode transactions() throws Exception {
    return json(
        alice
            .get("/api/v1/transactions?limit=100")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static JsonNode findItem(JsonNode items, String kind) {
    for (JsonNode item : items) {
      if (kind.equals(item.path("kind").asString())) {
        return item;
      }
    }
    throw new AssertionError("Item não encontrado: " + kind);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
