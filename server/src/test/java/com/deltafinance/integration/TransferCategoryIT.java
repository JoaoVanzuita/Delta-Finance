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
 * Movimentos internos já identificados pela categoria do provedor.
 *
 * <p>Transferências entre contas do próprio titular e movimentações de cofrinho não podem compor
 * receitas/despesas apenas porque uma das pernas não foi importada ou porque o par ainda não foi
 * confirmado. Categorias do tipo TRANSFER são excluídas dos totais.
 */
@AutoConfigureMockMvc
class TransferCategoryIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.transfer.category";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-transfer-category";

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

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-transfer-category", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-transfer-category",
        new AccountFixture("acc-a", "BANK", "CHECKING_ACCOUNT", "Conta A", "1000.00", null));
    PLUGGY.addAccount(
        "item-transfer-category",
        new AccountFixture("acc-b", "BANK", "SAVINGS_ACCOUNT", "Conta B", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-salary", "acc-a", "Salário", "1000.00", "2026-10-05", "POSTED", "Salary"));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-same-out",
            "acc-a",
            "Pix enviado Joao",
            "-100.00",
            "2026-10-10",
            "POSTED",
            "Same person transfer"));
    PLUGGY.addTransaction(
        "acc-b",
        new TransactionFixture(
            "tx-same-in",
            "acc-b",
            "Pix recebido Joao",
            "100.00",
            "2026-10-10",
            "POSTED",
            "Same person transfer"));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-cofrinho-out",
            "acc-a",
            "Dinheiro reservado Cofrinho",
            "-200.00",
            "2026-10-11",
            "POSTED",
            "Transfer - Internal"));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-cofrinho-in",
            "acc-a",
            "Dinheiro retirado Cofrinho",
            "200.00",
            "2026-10-12",
            "POSTED",
            "Transfer - Internal"));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-market", "acc-a", "Mercado", "-50.00", "2026-10-13", "POSTED", "Groceries"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-transfer-category"))
        .andExpect(status().isCreated());
  }

  @Test
  void categoriasInternasNaoEntramEmReceitasNemDespesas() throws Exception {
    JsonNode categories = categories();
    assertThat(categoryKind(categories, "Same person transfer")).isEqualTo("TRANSFER");
    assertThat(categoryKind(categories, "Transfer - Internal")).isEqualTo("TRANSFER");
    assertThat(categoryKind(categories, "Salary")).isEqualTo("INCOME");
    assertThat(categoryKind(categories, "Groceries")).isEqualTo("EXPENSE");

    JsonNode summary = summary();
    assertThat(summary.path("income").asString()).isEqualTo("1000.00");
    assertThat(summary.path("expenses").asString()).isEqualTo("50.00");
    assertThat(summary.path("periodResult").asString()).isEqualTo("950.00");
    assertThat(summary.path("pendingInternalMatches").asInt()).isZero();

    JsonNode entries = entries();
    assertThat(entries.path("items")).hasSize(2);
    BigDecimal sum = BigDecimal.ZERO;
    for (JsonNode entry : entries.path("items")) {
      sum = sum.add(new BigDecimal(entry.path("amount").asString()));
    }
    assertThat(sum.setScale(2)).isEqualByComparingTo("950.00");
  }

  private JsonNode categories() throws Exception {
    return json(
        alice
            .get("/api/v1/categories")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static String categoryKind(JsonNode page, String name) {
    for (JsonNode item : page.path("items")) {
      if (name.equals(item.path("name").asString())) {
        return item.path("kind").asString();
      }
    }
    throw new AssertionError("Categoria não encontrada: " + name);
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

  private JsonNode entries() throws Exception {
    return json(
        alice
            .get("/api/v1/reports/entries?from=2026-10-01&to=2026-10-31")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
