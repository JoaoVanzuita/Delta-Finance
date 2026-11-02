package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PostgresTestBase;
import com.deltafinance.support.QueryCountingDataSourceConfig;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Orçamento de consultas para leituras quentes: falha se uma tela voltar a fazer varredura. */
@AutoConfigureMockMvc
@Import(QueryCountingDataSourceConfig.class)
class QueryBudgetIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String USER = "alice.querybudget";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-query-budget";

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
  private String invoiceId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret");
    PLUGGY.addItem("item-budget", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-budget",
        new AccountFixture("acc-card", "CREDIT", "CREDIT_CARD", "Cartão Alfa", "0.00", null));
    PLUGGY.addAccount(
        "item-budget",
        new AccountFixture("acc-bank", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(USER, PASSWORD).login(USER, PASSWORD);
    alice
        .put("/api/v1/integrations/pluggy", Map.of("clientId", CLIENT, "clientSecret", "secret"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-budget"))
        .andExpect(status().isCreated());

    for (JsonNode account : accounts().path("items")) {
      if ("CREDIT".equals(account.path("type").asString())) {
        cardId = account.path("id").asString();
      }
    }
    assertThat(cardId).isNotBlank();
    configureCard();
    createCardTransaction();
    invoiceId = invoices().path("items").get(0).path("id").asString();
    assertThat(invoiceId).isNotBlank();
  }

  @Test
  void dashboardResumeDentroDoOrcamentoDeConsultas() throws Exception {
    long queries = countQueries(() -> alice.get("/api/v1/dashboard/summary?period=MONTH"));
    assertThat(queries).as("queries do dashboard: %s", queries).isLessThan(80);
  }

  @Test
  void listaEDetalheDeFaturaDentroDoOrcamento() throws Exception {
    long listQueries = countQueries(() -> alice.get("/api/v1/cards/" + cardId + "/invoices"));
    long detailQueries = countQueries(() -> alice.get("/api/v1/invoices/" + invoiceId));

    assertThat(listQueries).as("queries da lista de faturas: %s", listQueries).isLessThan(80);
    assertThat(detailQueries).as("queries do detalhe da fatura: %s", detailQueries).isLessThan(80);
  }

  private long countQueries(CheckedRequest request) throws Exception {
    QueryCountHolder.clear();
    request.perform().andExpect(status().isOk());
    return QueryCountHolder.getGrandTotal().getTotal();
  }

  private void configureCard() throws Exception {
    alice
        .put(
            "/api/v1/cards/" + cardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "5000.00"))
        .andExpect(status().isOk());
  }

  private void createCardTransaction() throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", cardId);
    request.put("type", "DEBIT");
    request.put("postedOn", LocalDate.now().toString());
    request.put("amount", "100.00");
    request.put("description", "Compra orçamento");
    alice.post("/api/v1/transactions", request).andExpect(status().isCreated());
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

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }

  @FunctionalInterface
  private interface CheckedRequest {
    org.springframework.test.web.servlet.ResultActions perform() throws Exception;
  }
}
