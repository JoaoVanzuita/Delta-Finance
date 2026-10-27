package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import com.deltafinance.transactions.application.TransactionImportService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Duas integrações independentes, múltiplos itemIds e importação isolada por usuário. */
@AutoConfigureMockMvc
class PluggyIntegrationSetupIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.familia";
  private static final String BOB = "bob.familia";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice";
  private static final String BOB_CLIENT = "client-bob";

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
  @Autowired private TransactionImportService transactionImportService;
  @Autowired private PluggyIntegrationRepository integrationRepository;
  @Autowired private PluggyConnectionRepository connectionRepository;

  private AuthTestClient alice;
  private AuthTestClient bob;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    PLUGGY.registerClient(ALICE_CLIENT, "secret-alice");
    PLUGGY.registerClient(BOB_CLIENT, "secret-bob");
    fixtures();

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
  }

  @Test
  void duasIntegracoesImportamContasECartoesSemVazamento() throws Exception {
    configure(alice, ALICE_CLIENT, "secret-alice");
    configure(bob, BOB_CLIENT, "secret-bob");

    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-1"))
        .andExpect(status().isCreated());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-2"))
        .andExpect(status().isCreated());
    bob.post("/api/v1/connections", Map.of("itemId", "item-bob-1")).andExpect(status().isCreated());

    alice
        .get("/api/v1/connections")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
    String aliceConnections =
        alice.get("/api/v1/connections").andReturn().getResponse().getContentAsString();
    assertThat(aliceConnections).doesNotContain("item-alice-1").doesNotContain("item-alice-2");

    alice
        .get("/api/v1/accounts")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[?(@.alias == 'Conta Alfa')]").exists())
        .andExpect(jsonPath("$.items[?(@.alias == 'Cartão Alfa')]").exists());
    String aliceAccounts =
        alice.get("/api/v1/accounts").andReturn().getResponse().getContentAsString();
    assertThat(aliceAccounts).doesNotContain("Conta Gama").doesNotContain("Banco Gama");

    JsonNode aliceAccountsJson = jsonMapper.readTree(aliceAccounts);
    JsonNode card = findAccount(aliceAccountsJson, "Cartão Alfa");
    assertThat(card.path("cardProfile").path("closingDay").asInt()).isEqualTo(5);
    assertThat(card.path("cardProfile").path("dueDay").asInt()).isEqualTo(15);
    assertThat(card.path("cardProfile").path("creditLimit").asString()).isEqualTo("5000.00");

    JsonNode bobAccounts = json(bob.get("/api/v1/accounts").andExpect(status().isOk()).andReturn());
    assertThat(bobAccounts.path("items").size()).isEqualTo(1);
    assertThat(bobAccounts.path("items").get(0).path("alias").asString()).isEqualTo("Conta Gama");

    JsonNode aliceTransactions =
        json(alice.get("/api/v1/transactions").andExpect(status().isOk()).andReturn());
    assertThat(aliceTransactions.path("items").size()).isEqualTo(6);
    assertThat(aliceTransactions.toString())
        .contains("Padaria")
        .contains("Farmácia")
        .contains("Estorno Padaria")
        .contains("Crédito cartão")
        .doesNotContain("Café");
    assertThat(findTransaction(aliceTransactions, "Crédito cartão").path("type").asString())
        .isEqualTo("REFUND");
    java.util.List<String> statuses = new java.util.ArrayList<>();
    java.util.List<String> currencies = new java.util.ArrayList<>();
    for (JsonNode item : aliceTransactions.path("items")) {
      statuses.add(item.path("status").asString());
      currencies.add(item.path("currency").asString());
    }
    assertThat(statuses).contains("PENDING", "POSTED");
    assertThat(currencies).containsOnly("BRL");
    JsonNode refund = findTransaction(aliceTransactions, "Estorno Padaria");
    assertThat(refund.path("relatedTransactionId").asString()).isNotBlank();

    JsonNode bobTransactions =
        json(bob.get("/api/v1/transactions").andExpect(status().isOk()).andReturn());
    assertThat(bobTransactions.path("items").size()).isEqualTo(1);
    assertThat(bobTransactions.toString()).contains("Café").doesNotContain("Padaria");

    assertThat(PLUGGY.dataApiKeys()).contains("key-" + ALICE_CLIENT, "key-" + BOB_CLIENT);
  }

  @Test
  void importaMetadadosDeParcelamentoDoProvedor() throws Exception {
    PLUGGY.setInstallments("tx-c1", 1, 3);
    configure(alice, ALICE_CLIENT, "secret-alice");
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-1"))
        .andExpect(status().isCreated());

    JsonNode card =
        findAccount(
            json(alice.get("/api/v1/accounts").andExpect(status().isOk()).andReturn()),
            "Cartão Alfa");
    JsonNode invoices =
        json(
            alice
                .get("/api/v1/cards/" + card.path("id").asString() + "/invoices")
                .andExpect(status().isOk())
                .andReturn());
    assertThat(invoices.path("items")).hasSize(1);
    JsonNode detail =
        json(
            alice
                .get("/api/v1/invoices/" + invoices.path("items").get(0).path("id").asString())
                .andExpect(status().isOk())
                .andReturn());
    JsonNode installment = null;
    for (JsonNode item : detail.path("items")) {
      if ("INSTALLMENT".equals(item.path("kind").asString())) {
        installment = item;
      }
    }
    assertThat(installment).isNotNull();
    assertThat(installment.path("installmentNumber").asInt()).isEqualTo(1);
    assertThat(installment.path("totalInstallments").asInt()).isEqualTo(3);
  }

  @Test
  void reembolsoParcialImportadoVinculaOriginalEPodeSerReligadoManualmente() throws Exception {
    PLUGGY.addTransaction(
        "acc-a1",
        new TransactionFixture(
            "tx-extra-debit",
            "acc-a1",
            "Compra extra",
            "-20.00",
            "2026-10-19",
            "POSTED",
            "Compras"));
    PLUGGY.addTransaction(
        "acc-a1",
        new TransactionFixture(
            "tx-partial-refund",
            "acc-a1",
            "Reembolso parcial",
            "5.00",
            "2026-10-22",
            "POSTED",
            "Compras"));

    configure(alice, ALICE_CLIENT, "secret-alice");
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-1"))
        .andExpect(status().isCreated());

    JsonNode transactions =
        json(alice.get("/api/v1/transactions?limit=100").andExpect(status().isOk()).andReturn());
    JsonNode refund = findTransaction(transactions, "Reembolso parcial");
    JsonNode original = findTransaction(transactions, "Padaria");
    JsonNode alternative = findTransaction(transactions, "Compra extra");
    assertThat(refund.path("relatedTransactionId").asString())
        .isEqualTo(original.path("id").asString());
    assertThat(refund.path("categoryId").asString())
        .isEqualTo(original.path("categoryId").asString());

    alice
        .patch(
            "/api/v1/transactions/" + refund.path("id").asString(),
            Map.of("relatedTransactionId", alternative.path("id").asString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.relatedTransactionId").value(alternative.path("id").asString()));
  }

  @Test
  void importacaoRepetidaNaoDuplicaTransacoes() throws Exception {
    configure(alice, ALICE_CLIENT, "secret-alice");
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-1"))
        .andExpect(status().isCreated());

    int before = json(alice.get("/api/v1/transactions").andReturn()).path("items").size();
    var integration = integrationRepository.findMeuPluggy(userId(ALICE)).orElseThrow();
    var connection =
        connectionRepository.findByIntegrationIdOrderByCreatedAtAsc(integration.getId()).getFirst();

    transactionImportService.importInitial(integration, connection.getId());

    int after = json(alice.get("/api/v1/transactions").andReturn()).path("items").size();
    assertThat(after).isEqualTo(before);
  }

  @Test
  void itemIdInexistenteRetornaValidacaoDeCampoSemCriarConexao() throws Exception {
    configure(alice, ALICE_CLIENT, "secret-alice");

    alice
        .post("/api/v1/connections", Map.of("itemId", "item-inexistente"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("itemId"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("não encontrado")));

    alice
        .get("/api/v1/connections")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void itemIdMalformadoNoProvedorRetornaValidacaoDeCampo() throws Exception {
    configure(alice, ALICE_CLIENT, "secret-alice");
    PLUGGY.failPath("/items/", 400, 1);

    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("itemId"));
  }

  @Test
  void acessoCruzadoANomeDeContaDeOutroUsuarioRetorna404() throws Exception {
    configure(alice, ALICE_CLIENT, "secret-alice");
    configure(bob, BOB_CLIENT, "secret-bob");
    alice.post("/api/v1/connections", Map.of("itemId", "item-alice-1"));
    bob.post("/api/v1/connections", Map.of("itemId", "item-bob-1"));

    JsonNode bobAccounts = json(bob.get("/api/v1/accounts").andReturn());
    String bobAccountId = bobAccounts.path("items").get(0).path("id").asString();

    alice
        .get("/api/v1/accounts/" + bobAccountId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    alice
        .get("/api/v1/transactions?accountId=" + bobAccountId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void filtrosDeTransacaoPorStatusETipoFuncionam() throws Exception {
    configure(alice, ALICE_CLIENT, "secret-alice");
    alice.post("/api/v1/connections", Map.of("itemId", "item-alice-1"));

    alice
        .get("/api/v1/transactions?status=PENDING")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].description").value("Farmácia"));
    alice
        .get("/api/v1/transactions?origin=IMPORTED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(5));
  }

  private void configure(AuthTestClient client, String clientId, String secret) throws Exception {
    client
        .put("/api/v1/integrations/pluggy", Map.of("clientId", clientId, "clientSecret", secret))
        .andExpect(status().isOk());
  }

  private void fixtures() {
    PLUGGY.addItem("item-alice-1", "Banco Alfa", "UPDATED");
    PLUGGY.addItem("item-alice-2", "Banco Beta", "UPDATED");
    PLUGGY.addItem("item-bob-1", "Banco Gama", "UPDATED");

    PLUGGY.addAccount(
        "item-alice-1",
        new AccountFixture("acc-a1", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1500.00", null));
    PLUGGY.addAccount(
        "item-alice-1",
        new AccountFixture(
            "card-a1",
            "CREDIT",
            "CREDIT_CARD",
            "Cartão Alfa",
            "-250.00",
            Map.of(
                "balanceCloseDate", "2026-10-05",
                "balanceDueDate", "2026-10-15",
                "creditLimit", "5000.00")));
    PLUGGY.addAccount(
        "item-alice-2",
        new AccountFixture("acc-a2", "BANK", "SAVINGS_ACCOUNT", "Poupança Beta", "300.00", null));
    PLUGGY.addAccount(
        "item-bob-1",
        new AccountFixture("acc-b1", "BANK", "CHECKING_ACCOUNT", "Conta Gama", "42.00", null));

    PLUGGY.addTransaction(
        "acc-a1",
        new TransactionFixture(
            "tx-a1", "acc-a1", "Padaria", "-12.50", "2026-10-20", "POSTED", "Alimentação"));
    PLUGGY.addTransaction(
        "acc-a1",
        new TransactionFixture(
            "tx-a2", "acc-a1", "Estorno Padaria", "12.50", "2026-10-21", "POSTED", "Alimentação"));
    PLUGGY.addTransaction(
        "acc-a1",
        new TransactionFixture(
            "tx-a3", "acc-a1", "Farmácia", "-20.00", "2026-10-22", "PENDING", "Saúde"));
    PLUGGY.addTransaction(
        "card-a1",
        new TransactionFixture(
            "tx-c1", "card-a1", "Loja", "100.00", "2026-10-18", "POSTED", "Compras"));
    PLUGGY.addTransaction(
        "card-a1",
        new TransactionFixture(
            "tx-c2", "card-a1", "Crédito cartão", "-30.00", "2026-10-19", "POSTED", "Compras"));
    PLUGGY.addTransaction(
        "acc-a2",
        new TransactionFixture(
            "tx-a4", "acc-a2", "Transferência", "300.00", "2026-10-19", "POSTED", "Transferência"));
    PLUGGY.addTransaction(
        "acc-b1",
        new TransactionFixture(
            "tx-b1", "acc-b1", "Café", "-7.00", "2026-10-20", "POSTED", "Alimentação"));
  }

  private JsonNode findAccount(JsonNode accounts, String alias) {
    for (JsonNode item : accounts.path("items")) {
      if (alias.equals(item.path("alias").asString())) {
        return item;
      }
    }
    throw new AssertionError("Conta não encontrada: " + alias);
  }

  private JsonNode findTransaction(JsonNode transactions, String description) {
    for (JsonNode item : transactions.path("items")) {
      if (description.equals(item.path("description").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + description);
  }

  private JsonNode json(MvcResult result) throws Exception {
    return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  private java.util.UUID userId(String username) {
    return jdbcTemplate.queryForObject(
        "select id from app_user where username_normalized = ?", java.util.UUID.class, username);
  }
}
