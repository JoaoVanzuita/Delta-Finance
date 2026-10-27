package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
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
 * Consistência final de faturas e parcelas (T185).
 *
 * <p>Combina compras em ciclos distintos, juros, crédito, pagamento parcial, correção de item e
 * prova que totais, saldo devedor e dashboard permanecem coerentes sem duplicar compras.
 */
@AutoConfigureMockMvc
class FinalInvoiceIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.final.faturas";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-final-faturas";

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
    PLUGGY.addItem("item-final-faturas", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-final-faturas",
        new AccountFixture("acc-final-bank", "BANK", "CHECKING_ACCOUNT", "Conta", "1000.00", null));
    PLUGGY.addAccount(
        "item-final-faturas",
        new AccountFixture("acc-final-card", "CREDIT", "CREDIT_CARD", "Cartão", "0.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-final-faturas"))
        .andExpect(status().isCreated());
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
  }

  @Test
  void faturasParcelasPagamentosECorrecoesFechamSemDuplicarCompras() throws Exception {
    createCardTransaction("-100.00", "2026-10-03");
    createCardTransaction("-50.00", "2026-10-08");

    JsonNode list = invoices();
    assertThat(list.path("items")).hasSize(2);
    String firstId = null;
    String secondId = null;
    for (JsonNode invoice : list.path("items")) {
      if ("2026-10-05".equals(invoice.path("closingDate").asString())) {
        firstId = invoice.path("id").asString();
      } else {
        secondId = invoice.path("id").asString();
      }
    }
    assertThat(firstId).isNotNull();
    assertThat(secondId).isNotNull();

    JsonNode interest =
        json(
            alice
                .post(
                    "/api/v1/invoices/" + firstId + "/items",
                    Map.of(
                        "kind", "INTEREST",
                        "reportingEffect", "EXPENSE_INCREASE",
                        "description", "Juros do ciclo",
                        "amount", "20.00",
                        "occurredOn", "2026-10-05"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    alice
        .post(
            "/api/v1/invoices/" + firstId + "/items",
            Map.of(
                "kind", "CREDIT",
                "reportingEffect", "EXPENSE_DECREASE",
                "description", "Crédito do ciclo",
                "amount", "30.00",
                "occurredOn", "2026-10-05"))
        .andExpect(status().isCreated());

    JsonNode first = invoiceDetail(firstId);
    assertThat(first.path("total").asString()).isEqualTo("90.00");
    assertThat(first.path("outstanding").asString()).isEqualTo("90.00");
    assertThat(first.path("items")).hasSize(3);

    alice
        .patch(
            "/api/v1/invoice-items/" + interest.path("id").asString(),
            Map.of(
                "kind", "INTEREST",
                "reportingEffect", "EXPENSE_INCREASE",
                "description", "Juros corrigidos",
                "amount", "25.00",
                "occurredOn", "2026-10-05"))
        .andExpect(status().isOk());
    JsonNode corrections =
        json(
            alice
                .get("/api/v1/invoice-items/" + interest.path("id").asString() + "/corrections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(corrections).hasSizeGreaterThanOrEqualTo(1);

    first = invoiceDetail(firstId);
    assertThat(first.path("total").asString()).isEqualTo("95.00");

    alice
        .post(
            "/api/v1/invoices/" + firstId + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "40.00", "paidOn", "2026-10-20"))
        .andExpect(status().isCreated());
    first = invoiceDetail(firstId);
    assertThat(first.path("status").asString()).isEqualTo("OVERDUE");
    assertThat(first.path("paymentsTotal").asString()).isEqualTo("40.00");
    assertThat(first.path("outstanding").asString()).isEqualTo("55.00");

    JsonNode second = invoiceDetail(secondId);
    assertThat(second.path("total").asString()).isEqualTo("50.00");
    assertThat(second.path("outstanding").asString()).isEqualTo("50.00");

    JsonNode summary = summary();
    // 100 + 50 compras + 25 juros - 30 crédito; pagamento não é despesa.
    assertThat(summary.path("expenses").asString()).isEqualTo("145.00");
    assertThat(summary.path("cardOutstandingBalance").asString()).isEqualTo("105.00");

    BigDecimal detailSum = BigDecimal.ZERO;
    for (JsonNode item : first.path("items")) {
      if ("EXPENSE_DECREASE".equals(item.path("reportingEffect").asString())) {
        detailSum = detailSum.subtract(new BigDecimal(item.path("amount").asString()));
      } else if (!"NONE".equals(item.path("reportingEffect").asString())) {
        detailSum = detailSum.add(new BigDecimal(item.path("amount").asString()));
      } else {
        detailSum = detailSum.add(new BigDecimal(item.path("amount").asString()));
      }
    }
    assertThat(detailSum.setScale(2)).isEqualByComparingTo("95.00");
  }

  @Test
  void itemManualComTotalDeParcelasUmCompoeOsRelatorios() throws Exception {
    createCardTransaction("-10.00", "2026-10-03");
    String firstId = firstInvoiceId();
    alice
        .post(
            "/api/v1/invoices/" + firstId + "/items",
            Map.of(
                "kind",
                "INTEREST",
                "reportingEffect",
                "EXPENSE_INCREASE",
                "description",
                "Tarifa avulsa",
                "amount",
                "10.00",
                "occurredOn",
                "2026-10-05",
                "totalInstallments",
                1))
        .andExpect(status().isCreated());

    assertThat(summary().path("expenses").asString()).isEqualTo("20.00");
    JsonNode entries = entries("from=2026-10-01&to=2026-10-31");
    assertThat(entries.path("items").toString()).contains("Tarifa avulsa");
  }

  @Test
  void filtroQueIsolaReducaoNaoProduzDespesaNegativa() throws Exception {
    createCardTransaction("-50.00", "2026-10-03");
    String firstId = firstInvoiceId();
    alice
        .post(
            "/api/v1/invoices/" + firstId + "/items",
            Map.of(
                "kind",
                "CREDIT",
                "reportingEffect",
                "EXPENSE_DECREASE",
                "description",
                "Crédito isolado",
                "amount",
                "30.00",
                "occurredOn",
                "2026-10-05"))
        .andExpect(status().isCreated());

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31&type=REFUND");
    assertThat(summary.path("expenses").asString()).isEqualTo("0.00");

    JsonNode categories = categories("from=2026-10-01&to=2026-10-31&type=REFUND");
    for (JsonNode category : categories) {
      assertThat(new BigDecimal(category.path("amount").asString()).signum())
          .isGreaterThanOrEqualTo(0);
    }
    JsonNode series = timeseries("from=2026-10-01&to=2026-10-31&period=MONTH&type=REFUND");
    for (JsonNode bucket : series) {
      assertThat(new BigDecimal(bucket.path("expenses").asString()).signum())
          .isGreaterThanOrEqualTo(0);
    }
  }

  @Test
  void filtroRefundNaoIncluiJurosETarifas() throws Exception {
    createCardTransaction("-100.00", "2026-10-03");
    String firstId = firstInvoiceId();
    alice
        .post(
            "/api/v1/invoices/" + firstId + "/items",
            Map.of(
                "kind",
                "INTEREST",
                "reportingEffect",
                "EXPENSE_INCREASE",
                "description",
                "Juros do ciclo",
                "amount",
                "20.00",
                "occurredOn",
                "2026-10-05"))
        .andExpect(status().isCreated());
    alice
        .post(
            "/api/v1/invoices/" + firstId + "/items",
            Map.of(
                "kind",
                "CREDIT",
                "reportingEffect",
                "EXPENSE_DECREASE",
                "description",
                "Crédito do ciclo",
                "amount",
                "30.00",
                "occurredOn",
                "2026-10-05"))
        .andExpect(status().isCreated());

    JsonNode entries = entries("from=2026-10-01&to=2026-10-31&type=REFUND");
    String body = entries.path("items").toString();
    assertThat(body).contains("Crédito do ciclo").doesNotContain("Juros do ciclo");

    assertThat(summary("from=2026-10-01&to=2026-10-31&type=REFUND").path("expenses").asString())
        .isEqualTo("0.00");
  }

  @Test
  void resumoCategoriasSerieEEntriesUsamAMesmaProjecao() throws Exception {
    createCardTransaction("-100.00", "2026-10-03");
    String firstId = firstInvoiceId();
    alice
        .post(
            "/api/v1/invoices/" + firstId + "/items",
            Map.of(
                "kind", "CREDIT",
                "reportingEffect", "EXPENSE_DECREASE",
                "description", "Crédito",
                "amount", "30.00",
                "occurredOn", "2026-10-05"))
        .andExpect(status().isCreated());

    assertThat(summary().path("expenses").asString()).isEqualTo("70.00");

    BigDecimal categoryTotal = BigDecimal.ZERO;
    for (JsonNode category : categories("from=2026-10-01&to=2026-10-31&period=MONTH")) {
      categoryTotal = categoryTotal.add(new BigDecimal(category.path("amount").asString()));
    }
    assertThat(categoryTotal).isEqualByComparingTo("70.00");

    BigDecimal seriesTotal = BigDecimal.ZERO;
    for (JsonNode bucket : timeseries("from=2026-10-01&to=2026-10-31&period=MONTH")) {
      seriesTotal = seriesTotal.add(new BigDecimal(bucket.path("expenses").asString()));
    }
    assertThat(seriesTotal).isEqualByComparingTo("70.00");

    BigDecimal drillDown = BigDecimal.ZERO;
    for (JsonNode item : entries("from=2026-10-01&to=2026-10-31").path("items")) {
      BigDecimal amount = new BigDecimal(item.path("amount").asString());
      drillDown = amount.signum() < 0 ? drillDown.add(amount.abs()) : drillDown.subtract(amount);
    }
    assertThat(drillDown).isEqualByComparingTo("70.00");
  }

  @Test
  void correcaoDeCompraParceladaPropagaParaTodasAsParcelas() throws Exception {
    createCardTransaction("-120.00", "2026-10-03");
    String transactionId = null;
    for (JsonNode transaction : transactions().path("items")) {
      if ("Compra -120.00".equals(transaction.path("description").asString())) {
        transactionId = transaction.path("id").asString();
      }
    }
    assertThat(transactionId).isNotNull();
    String firstId = firstInvoiceId();

    alice
        .post(
            "/api/v1/invoices/" + firstId + "/items",
            Map.of(
                "kind", "PURCHASE",
                "reportingEffect", "NONE",
                "description", "Compra -120.00",
                "amount", "120.00",
                "occurredOn", "2026-10-03",
                "transactionId", transactionId,
                "totalInstallments", 3))
        .andExpect(status().isCreated());

    alice
        .patch(
            "/api/v1/transactions/" + transactionId,
            Map.of(
                "amount", "-150.00", "description", "Compra corrigida", "postedOn", "2026-10-03"))
        .andExpect(status().isOk());

    int corrected = 0;
    for (JsonNode invoice : invoices().path("items")) {
      JsonNode detail = invoiceDetail(invoice.path("id").asString());
      for (JsonNode item : detail.path("items")) {
        if ("Compra corrigida".equals(item.path("description").asString())) {
          assertThat(item.path("amount").asString()).isEqualTo("150.00");
          corrected++;
        }
      }
    }
    assertThat(corrected).isEqualTo(3);
  }

  @Test
  void creditoExcedenteNaoDeixaDespesaNegativaEContinuaComoCredito() throws Exception {
    createCardTransaction("-100.00", "2026-10-03");
    String firstId = firstInvoiceId();
    alice
        .post(
            "/api/v1/invoices/" + firstId + "/items",
            Map.of(
                "kind", "CREDIT",
                "reportingEffect", "EXPENSE_DECREASE",
                "description", "Crédito excedente",
                "amount", "150.00",
                "occurredOn", "2026-10-05"))
        .andExpect(status().isCreated());

    assertThat(summary().path("expenses").asString()).isEqualTo("0.00");
    assertThat(invoiceDetail(firstId).path("total").asString()).isEqualTo("0.00");
    assertThat(
            json(alice
                    .get("/api/v1/accounts/" + cardId)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
                .path("cardProfile")
                .path("creditBalance")
                .asString())
        .isEqualTo("50.00");
  }

  private String firstInvoiceId() throws Exception {
    for (JsonNode invoice : invoices().path("items")) {
      if ("2026-10-05".equals(invoice.path("closingDate").asString())) {
        return invoice.path("id").asString();
      }
    }
    throw new AssertionError("Fatura de fechamento 2026-10-05 não encontrada.");
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

  private JsonNode timeseries(String query) throws Exception {
    return json(
        alice
            .get("/api/v1/dashboard/timeseries?" + query)
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

  private JsonNode transactions() throws Exception {
    return json(
        alice
            .get("/api/v1/transactions?limit=100&accountId=" + cardId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void createCardTransaction(String amount, String postedOn) throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", cardId);
    request.put("type", "DEBIT");
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", "Compra " + amount);
    alice.post("/api/v1/transactions", request).andExpect(status().isCreated());
    invoices();
  }

  private JsonNode summary() throws Exception {
    return json(
        alice
            .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
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

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
