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
    assertThat(aliceTransactions.path("items").size()).isEqualTo(5);
    assertThat(aliceTransactions.toString())
        .contains("Padaria")
        .contains("Farmácia")
        .contains("Estorno Padaria")
        .doesNotContain("Café");
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
        .andExpect(jsonPath("$.items.length()").value(4));
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
            "tx-c1", "card-a1", "Loja", "-100.00", "2026-10-18", "POSTED", "Compras"));
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
