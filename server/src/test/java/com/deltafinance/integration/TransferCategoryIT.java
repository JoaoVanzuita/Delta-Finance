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
import java.util.ArrayList;
import java.util.List;
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
 * Movimentos internos e transferências externas.
 *
 * <p>Transferências entre contas do próprio titular e movimentações de cofrinho não podem compor
 * receitas/despesas apenas porque uma das pernas não foi importada ou porque o par ainda não foi
 * confirmado. Categorias do tipo TRANSFER são excluídas dos totais. Transferências genéricas
 * (“Transfer - TED/PIX” e “Transfers”) são externas por padrão e seguem a direção: entrada é
 * receita e saída é despesa.
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
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-external-in",
            "acc-a",
            "TED recebida Caixa",
            "2175.00",
            "2026-10-05",
            "POSTED",
            "Transfer - TED"));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-external-out",
            "acc-a",
            "Pix enviado terceiro",
            "-123.45",
            "2026-10-06",
            "POSTED",
            "Transfer - PIX"));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-groceries-credit",
            "acc-a",
            "Devolução mercado",
            "30.00",
            "2026-10-07",
            "POSTED",
            "Groceries"));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-investment-buy",
            "acc-a",
            "Compra de bolsa",
            "-1000.00",
            "2026-10-08",
            "POSTED",
            "Variable income"));
    PLUGGY.addTransaction(
        "acc-a",
        new TransactionFixture(
            "tx-investment-sell",
            "acc-a",
            "Resgate de fundo",
            "500.00",
            "2026-10-09",
            "POSTED",
            "Fixed income"));

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
  void transferenciasInternasFicamForaMasTransferenciasExternasSeguemADirecao() throws Exception {
    JsonNode categories = categories();
    assertThat(categoryKind(categories, "Transferências")).isEqualTo("TRANSFER");
    assertThat(categoryKind(categories, "Transferências externas")).isEqualTo("OTHER");
    assertThat(categoryKind(categories, "Aportes")).isEqualTo("TRANSFER");
    assertThat(categoryKind(categories, "Resgates")).isEqualTo("TRANSFER");
    assertThat(categoryKind(categories, "Salário")).isEqualTo("INCOME");
    assertThat(categoryKind(categories, "Alimentação")).isEqualTo("EXPENSE");
    assertThat(categoryKind(categories, "Same person transfer")).isNull();

    JsonNode summary = summary();
    assertThat(summary.path("income").asString()).isEqualTo("3205.00");
    assertThat(summary.path("expenses").asString()).isEqualTo("173.45");
    assertThat(summary.path("periodResult").asString()).isEqualTo("3031.55");
    assertThat(summary.path("pendingInternalMatches").asInt()).isZero();

    JsonNode entries = entries();
    assertThat(entries.path("items")).hasSize(5);
    BigDecimal sum = BigDecimal.ZERO;
    for (JsonNode entry : entries.path("items")) {
      sum = sum.add(new BigDecimal(entry.path("amount").asString()));
    }
    assertThat(sum.setScale(2)).isEqualByComparingTo("3031.55");

    JsonNode incomeCategories = incomeCategories();
    assertThat(categoryNames(incomeCategories))
        .contains("Salário", "Outros", "Transferências externas")
        .doesNotContain("Alimentação", "Compras");
  }

  @Test
  void receitaManualEmCategoriaDeDespesaApareceEmOutros() throws Exception {
    JsonNode categories = categories();
    UUID accountId = accountId("Conta A");
    UUID expenseCategoryId = categoryId(categories, "Alimentação");

    alice
        .post(
            "/api/v1/transactions",
            Map.of(
                "accountId",
                accountId.toString(),
                "type",
                "CREDIT",
                "postedOn",
                "2026-10-20",
                "amount",
                "77.00",
                "description",
                "Receita manual em categoria de despesa",
                "categoryId",
                expenseCategoryId.toString()))
        .andExpect(status().isCreated());

    JsonNode summary = summary();
    assertThat(summary.path("income").asString()).isEqualTo("3282.00");
    assertThat(summary.path("expenses").asString()).isEqualTo("173.45");
    assertThat(summary.path("periodResult").asString()).isEqualTo("3108.55");

    JsonNode incomeCategories = incomeCategories();
    assertThat(categoryNames(incomeCategories)).doesNotContain("Alimentação");
    JsonNode outros = categoryByName(incomeCategories, "Outros");
    assertThat(outros.path("amount").asString()).isEqualTo("107.00");

    UUID outrosId = categoryId(categories, "Outros");
    assertThat(sumAmounts(entries("&categoryId=" + outrosId + "&direction=INCOME")))
        .isEqualByComparingTo("107.00");
    assertThat(entries("&categoryId=" + outrosId + "&direction=EXPENSE").path("items").size())
        .isZero();
  }

  private static BigDecimal sumAmounts(JsonNode page) {
    BigDecimal sum = BigDecimal.ZERO;
    for (JsonNode entry : page.path("items")) {
      sum = sum.add(new BigDecimal(entry.path("amount").asString()));
    }
    return sum.setScale(2);
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
    return null;
  }

  private static List<String> categoryNames(JsonNode categories) {
    List<String> names = new ArrayList<>();
    for (JsonNode item : categories) {
      names.add(item.path("categoryName").asString());
    }
    return names;
  }

  private static UUID categoryId(JsonNode page, String name) {
    for (JsonNode item : page.path("items")) {
      if (name.equals(item.path("name").asString())) {
        return UUID.fromString(item.path("id").asString());
      }
    }
    throw new AssertionError("Categoria não encontrada: " + name);
  }

  private static JsonNode categoryByName(JsonNode categories, String name) {
    for (JsonNode item : categories) {
      if (name.equals(item.path("categoryName").asString())) {
        return item;
      }
    }
    throw new AssertionError("Categoria de relatório não encontrada: " + name);
  }

  private UUID accountId(String name) throws Exception {
    JsonNode page =
        json(
            alice
                .get("/api/v1/accounts")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode item : page.path("items")) {
      if (name.equals(item.path("alias").asString())
          || name.equals(item.path("providerName").asString())) {
        return UUID.fromString(item.path("id").asString());
      }
    }
    throw new AssertionError("Conta não encontrada: " + name);
  }

  private JsonNode incomeCategories() throws Exception {
    return json(
        alice
            .get("/api/v1/dashboard/categories?from=2026-10-01&to=2026-10-31&direction=INCOME")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
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
    return entries("");
  }

  private JsonNode entries(String extra) throws Exception {
    return json(
        alice
            .get("/api/v1/reports/entries?from=2026-10-01&to=2026-10-31" + extra)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
