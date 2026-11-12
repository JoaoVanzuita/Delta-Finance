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

/** Reconcilição diária de compras de cartão antigas que mudam de PENDING para POSTED. */
@AutoConfigureMockMvc
class CardPendingReconciliationIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.pendingcard";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-pendingcard";
  private static final String ITEM = "item-pendingcard";
  private static final String CARD = "acc-pendingcard";

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
  private String cardId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        ITEM,
        new AccountFixture(
            CARD,
            "CREDIT",
            "CREDIT_CARD",
            "Cartão pendente",
            "0.00",
            Map.of(
                "balanceCloseDate", "2026-10-05",
                "balanceDueDate", "2026-10-15",
                "creditLimit", "2000.00")));
    PLUGGY.addTransaction(
        CARD,
        new TransactionFixture(
            "tx-old-pending",
            CARD,
            "Compra antiga pendente",
            "100.00",
            "2026-10-01",
            "PENDING",
            "Groceries"));
    PLUGGY.setCreditCardMetadata("tx-old-pending", Map.of("billForecastDate", "2026-10"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice.post("/api/v1/connections", Map.of("itemId", ITEM)).andExpect(status().isCreated());

    for (JsonNode account : accounts().path("items")) {
      if ("Cartão pendente".equals(account.path("alias").asString())) {
        cardId = account.path("id").asString();
      }
    }
    assertThat(cardId).isNotBlank();
  }

  @Test
  void sincronizacaoDiariaReleituraDeCartaoAntigoAindaPendente() throws Exception {
    assertThat(transaction().path("status").asString()).isEqualTo("PENDING");

    PLUGGY.updateTransactionStatus(CARD, "tx-old-pending", "POSTED");

    String connectionId = firstConnectionId();
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "pending-card");

    JsonNode transaction = transaction();
    assertThat(transaction.path("status").asString()).isEqualTo("POSTED");
    assertThat(invoiceTotal()).isEqualByComparingTo("100.00");
  }

  private JsonNode transaction() throws Exception {
    JsonNode transactions =
        json(
            alice
                .get("/api/v1/transactions?limit=100")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode transaction : transactions.path("items")) {
      if ("tx-old-pending".equals(transaction.path("externalTransactionId").asString())) {
        return transaction;
      }
      if ("Compra antiga pendente".equals(transaction.path("description").asString())) {
        return transaction;
      }
    }
    throw new AssertionError("Transação não encontrada: " + transactions);
  }

  private java.math.BigDecimal invoiceTotal() throws Exception {
    JsonNode invoices =
        json(
            alice
                .get("/api/v1/cards/" + cardId + "/invoices")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode invoice : invoices.path("items")) {
      if ("2026-09-06".equals(invoice.path("cycleStart").asString())) {
        return new java.math.BigDecimal(invoice.path("total").asString());
      }
    }
    return java.math.BigDecimal.ZERO;
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

  private String firstConnectionId() throws Exception {
    JsonNode connections =
        json(
            alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return connections.path("items").get(0).path("id").asString();
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
