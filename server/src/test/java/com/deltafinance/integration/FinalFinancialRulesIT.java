package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regressão final das regras financeiras (T184).
 *
 * <p>Cobre sinal assinado, reembolso parcial com limite acumulado, categoria herdada, ajuste com
 * motivo, transferência manual em duas pernas e ausência de dupla contagem.
 */
@AutoConfigureMockMvc
class FinalFinancialRulesIT extends PostgresTestBase {

  private static final String ALICE = "alice.final.regras";
  private static final String PASSWORD = "cavalo correto bateria grampo";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  private AuthTestClient alice;
  private String bankId;
  private String cashId;
  private String categoryId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bankId = createAccount("BANK", "Conta principal", "1000.00");
    cashId = createAccount("CASH", "Carteira", "100.00");
    categoryId =
        json(alice
                .post("/api/v1/categories", Map.of("name", "Mercado final", "kind", "EXPENSE"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("id")
            .asString();
  }

  @Test
  void reembolsosParciaisRespeitamOLimiteDoOriginalEHerdaCategoria() throws Exception {
    String original =
        createTransaction("DEBIT", bankId, "100.00", "Compra original", "2026-10-02", null, null);
    createTransaction("CREDIT", bankId, "500.00", "Salário", "2026-10-01", null, null);
    String firstRefund =
        createTransaction("REFUND", bankId, "80.00", "Reembolso 1", "2026-10-03", original, null);
    String secondRefund =
        createTransaction("REFUND", bankId, "50.00", "Reembolso 2", "2026-10-04", original, null);

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31");
    assertThat(summary.path("income").asString()).isEqualTo("500.00");
    // 100 de despesa - 80 - 20 (limite restante) = 0; o excedente de 30 não vira despesa negativa.
    assertThat(summary.path("expenses").asString()).isEqualTo("0.00");
    assertThat(summary.path("periodResult").asString()).isEqualTo("500.00");

    JsonNode transactions = transactions();
    JsonNode refund = find(transactions, firstRefund);
    assertThat(refund.path("categoryId").asString()).isEqualTo(categoryId);
    assertThat(refund.path("relatedTransactionId").asString()).isEqualTo(original);
    JsonNode second = find(transactions, secondRefund);
    assertThat(second.path("relatedTransactionId").asString()).isEqualTo(original);
    JsonNode originalView = find(transactions, original);
    assertThat(originalView.path("amount").asString()).isEqualTo("-100.00");

    JsonNode entries = entries("from=2026-10-01&to=2026-10-31");
    java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
    for (JsonNode entry : entries.path("items")) {
      sum = sum.add(new java.math.BigDecimal(entry.path("amount").asString()));
    }
    assertThat(sum.setScale(2)).isEqualByComparingTo("500.00");
  }

  @Test
  void transferenciaManualTemDuasPernasEAjusteTemSinalEMotivo() throws Exception {
    createTransaction("CREDIT", bankId, "300.00", "Entrada", "2026-10-01", null, null);
    alice
        .post(
            "/api/v1/transactions",
            request("TRANSFER", bankId, "50.00", "Para carteira", "2026-10-02", null, null, cashId))
        .andExpect(status().isCreated());
    createTransaction(
        "ADJUSTMENT",
        bankId,
        "-25.00",
        "Ajuste de saldo",
        "2026-10-03",
        null,
        "Correção de extrato");

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31");
    assertThat(summary.path("income").asString()).isEqualTo("300.00");
    assertThat(summary.path("expenses").asString()).isEqualTo("25.00");
    assertThat(summary.path("periodResult").asString()).isEqualTo("275.00");
    assertThat(summary.path("currentBalance").asString()).isEqualTo("1375.00");

    JsonNode transactions = transactions();
    long transferLegs =
        java.util.stream.StreamSupport.stream(transactions.path("items").spliterator(), false)
            .filter(item -> "TRANSFER".equals(item.path("type").asString()))
            .count();
    assertThat(transferLegs).isEqualTo(2);

    JsonNode entries = entries("from=2026-10-01&to=2026-10-31");
    assertThat(
            java.util.stream.StreamSupport.stream(entries.path("items").spliterator(), false)
                .noneMatch(entry -> "TRANSFER".equals(entry.path("type").asString())))
        .isTrue();
    JsonNode adjustment =
        java.util.stream.StreamSupport.stream(transactions.path("items").spliterator(), false)
            .filter(item -> "ADJUSTMENT".equals(item.path("type").asString()))
            .findFirst()
            .orElseThrow();
    assertThat(adjustment.path("amount").asString()).isEqualTo("-25.00");
    assertThat(adjustment.path("adjustmentReason").asString()).isEqualTo("Correção de extrato");
  }

  private String createAccount(String type, String alias, String balance) throws Exception {
    return json(alice
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
            .getContentAsString())
        .path("id")
        .asString();
  }

  private String createTransaction(
      String type,
      String accountId,
      String amount,
      String description,
      String postedOn,
      String relatedTransactionId,
      String adjustmentReason)
      throws Exception {
    return createTransaction(
        type,
        accountId,
        amount,
        description,
        postedOn,
        relatedTransactionId,
        adjustmentReason,
        null);
  }

  private String createTransaction(
      String type,
      String accountId,
      String amount,
      String description,
      String postedOn,
      String relatedTransactionId,
      String adjustmentReason,
      String transferAccountId)
      throws Exception {
    return json(alice
            .post(
                "/api/v1/transactions",
                request(
                    type,
                    accountId,
                    amount,
                    description,
                    postedOn,
                    relatedTransactionId,
                    adjustmentReason,
                    transferAccountId))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
  }

  private Map<String, Object> request(
      String type,
      String accountId,
      String amount,
      String description,
      String postedOn,
      String relatedTransactionId,
      String adjustmentReason,
      String transferAccountId) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", accountId);
    request.put("type", type);
    request.put("amount", amount);
    request.put("description", description);
    request.put("postedOn", postedOn);
    if (categoryId != null && "DEBIT".equals(type)) {
      request.put("categoryId", categoryId);
    }
    if (relatedTransactionId != null) {
      request.put("relatedTransactionId", relatedTransactionId);
    }
    if (adjustmentReason != null) {
      request.put("adjustmentReason", adjustmentReason);
    }
    if (transferAccountId != null) {
      request.put("transferAccountId", transferAccountId);
    }
    return request;
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
            .get("/api/v1/reports/entries?" + query + "&limit=100")
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

  private static JsonNode find(JsonNode page, String id) {
    for (JsonNode item : page.path("items")) {
      if (id.equals(item.path("id").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + id);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
