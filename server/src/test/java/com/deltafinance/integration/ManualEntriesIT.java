package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
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

/** Contas manuais, lançamentos, categorias, transferência em duas pernas e vínculos (T104). */
@AutoConfigureMockMvc
class ManualEntriesIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.manual";
  private static final String BOB = "bob.manual";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-manual";
  private static final String BOB_CLIENT = "client-bob-manual";

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
  @Autowired private SyncJobService syncJobService;
  @Autowired private PluggyRateLimiter rateLimiter;

  private AuthTestClient alice;
  private AuthTestClient bob;
  private String importedAccountId;
  private String importedTransactionId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(ALICE_CLIENT, "secret-alice");
    PLUGGY.registerClient(BOB_CLIENT, "secret-bob");
    PLUGGY.addItem("item-alice", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-alice", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-1", "acc-alice", "Mercado", "-100.00", "2026-10-20", "POSTED", "Mercado"));
    PLUGGY.addItem("item-bob", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-bob",
        new AccountFixture("acc-bob", "BANK", "CHECKING_ACCOUNT", "Conta Beta", "500.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);

    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", ALICE_CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    bob.put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", BOB_CLIENT, "clientSecret", "secret-bob"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice"))
        .andExpect(status().isCreated());
    bob.post("/api/v1/connections", Map.of("itemId", "item-bob")).andExpect(status().isCreated());

    importedAccountId = accounts(alice).path("items").get(0).path("id").asString();
    importedTransactionId = transactions(alice).path("items").get(0).path("id").asString();
  }

  @Test
  void registraTodosOsTiposManuaisETransferenciaEmDuasPernas() throws Exception {
    String cash = createAccount("CASH", "Carteira", "100.00");
    String bank = createAccount("BANK", "Conta Casa", "500.00");

    JsonNode income = createTransaction("CREDIT", cash, "50.00", "Venda", null, null, null);
    assertThat(income.path("source").asString()).isEqualTo("MANUAL");
    assertThat(income.path("amount").asString()).isEqualTo("50.00");

    JsonNode expense = createTransaction("DEBIT", cash, "20.00", "Feira", null, null, null);
    assertThat(expense.path("amount").asString()).isEqualTo("-20.00");

    JsonNode transfer =
        createTransaction("TRANSFER", cash, "30.00", "Para banco", null, bank, null);
    assertThat(transfer.path("amount").asString()).isEqualTo("-30.00");
    assertThat(transfer.path("counterpartTransactionId").asString()).isNotBlank();

    JsonNode destinationLegs = transactionsForAccount(bank);
    assertThat(destinationLegs.path("items")).hasSize(1);
    assertThat(destinationLegs.path("items").get(0).path("amount").asString()).isEqualTo("30.00");
    assertThat(destinationLegs.path("items").get(0).path("counterpartTransactionId").asString())
        .isEqualTo(transfer.path("id").asString());

    JsonNode refund =
        createTransaction(
            "REFUND",
            importedAccountId,
            "10.00",
            "Reembolso parcial",
            importedTransactionId,
            null,
            null);
    assertThat(refund.path("amount").asString()).isEqualTo("10.00");
    assertThat(refund.path("relatedTransactionId").asString()).isEqualTo(importedTransactionId);

    Map<String, Object> chargebackRequest =
        request("CHARGEBACK", importedAccountId, "5.00", "Chargeback", null);
    JsonNode chargeback = create(chargebackRequest);
    assertThat(chargeback.path("relatedTransactionId").asString()).isEqualTo(importedTransactionId);

    Map<String, Object> adjustmentRequest =
        request("ADJUSTMENT", cash, "-7.00", "Ajuste de saldo", null);
    adjustmentRequest.put("adjustmentReason", "Diferença de caixa");
    JsonNode adjustment = create(adjustmentRequest);
    assertThat(adjustment.path("adjustmentReason").asString()).isEqualTo("Diferença de caixa");

    JsonNode all = transactions(alice);
    assertThat(all.path("items")).hasSize(8);
    assertThat(all.toString()).contains("Venda", "Feira", "Para banco", "Reembolso parcial");

    JsonNode matches = internalMatches("CONFIRMED");
    assertThat(matches.path("items").size()).isEqualTo(1);
    assertThat(matches.path("items").get(0).path("kind").asString()).isEqualTo("TRANSFER");
    assertThat(matches.path("items").get(0).path("status").asString()).isEqualTo("CONFIRMED");
  }

  @Test
  void saldoManualAjustaSomenteLancamentosAPartirDaReferenciaEExpoeOsDoisSaldos() throws Exception {
    String cash = createAccount("CASH", "Carteira", "100.00");
    String bank = createAccount("BANK", "Conta Casa", "500.00");

    createTransactionOn("DEBIT", cash, "999.00", "Antes da referência", "2026-10-22", null);
    createTransactionOn("CREDIT", cash, "50.00", "No dia da referência", "2026-10-23", null);
    createTransactionOn("DEBIT", cash, "20.00", "Depois da referência", "2026-10-24", null);
    createTransactionOn("TRANSFER", cash, "30.00", "Para banco", "2026-10-24", bank);

    JsonNode cashView = account(cash);
    assertThat(cashView.path("referenceBalance").asString()).isEqualTo("100.00");
    assertThat(cashView.path("currentBalance").asString()).isEqualTo("100.00");

    JsonNode bankView = account(bank);
    assertThat(bankView.path("referenceBalance").asString()).isEqualTo("500.00");
    assertThat(bankView.path("currentBalance").asString()).isEqualTo("530.00");

    JsonNode summary =
        json(
            alice
                .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(summary.path("currentBalance").asString()).isEqualTo("1630.00");
  }

  @Test
  void filtrosDeCategoriaEFaixaDeValorRespeitamPaginacao() throws Exception {
    String cash = createAccount("CASH", "Filtros", "1000.00");
    String category =
        json(alice
                .post("/api/v1/categories", Map.of("name", "Categoria filtro", "kind", "EXPENSE"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("id")
            .asString();
    createTransaction("DEBIT", cash, "10.00", "Dez", null, null, null);
    createTransaction("DEBIT", cash, "50.00", "Cinquenta", null, null, category);
    createTransaction("DEBIT", cash, "55.00", "Cinquenta e cinco", null, null, null);
    createTransaction("CREDIT", cash, "57.00", "Cinquenta e sete", null, null, null);
    createTransaction("CREDIT", cash, "100.00", "Cem", null, null, null);
    createTransaction("CREDIT", cash, "200.00", "Duzentos", null, null, null);

    JsonNode firstPage =
        json(
            alice
                .get("/api/v1/transactions?limit=1&minAmount=55&maxAmount=57")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(firstPage.path("items")).hasSize(1);
    assertThat(firstPage.path("hasMore").asBoolean()).isTrue();

    JsonNode secondPage =
        json(
            alice
                .get(
                    "/api/v1/transactions?limit=1&minAmount=55&maxAmount=57&cursor="
                        + firstPage.path("nextCursor").asString())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(secondPage.path("items")).hasSize(1);
    assertThat(secondPage.path("hasMore").asBoolean()).isFalse();

    JsonNode byCategory =
        json(
            alice
                .get("/api/v1/transactions?limit=100&categoryId=" + category)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(byCategory.path("items")).hasSize(1);
    assertThat(byCategory.path("items").get(0).path("description").asString())
        .isEqualTo("Cinquenta");

    alice
        .get("/api/v1/transactions?minAmount=100&maxAmount=50")
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void contaManualDesativadaNaoRecebeLancamentosETipoPermaneceImutavel() throws Exception {
    String cash = createAccount("CASH", "Carteira", "100.00");
    alice
        .patch("/api/v1/accounts/" + cash, Map.of("status", "INACTIVE", "alias", "Carteira antiga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.type").value("CASH"));

    alice
        .post("/api/v1/transactions", request("DEBIT", cash, "10.00", "Bloqueado", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));

    alice
        .patch("/api/v1/accounts/" + importedAccountId, Map.of("alias", "Tentativa"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_MANUAL"));

    alice
        .patch("/api/v1/accounts/" + cash, Map.of("status", "ACTIVE", "type", "BANK"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("CASH"));

    createTransaction("DEBIT", cash, "10.00", "Reativado", null, null, null);
  }

  @Test
  void categoriasPersonalizadasReatribuemSemApagarTransacoes() throws Exception {
    JsonNode categories = listCategories();
    assertThat(categories.path("items").size()).isGreaterThanOrEqualTo(10);

    String pets =
        json(alice
                .post("/api/v1/categories", Map.of("name", "Pets", "kind", "EXPENSE"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("id")
            .asString();

    alice
        .post("/api/v1/categories", Map.of("name", "Pets", "kind", "EXPENSE"))
        .andExpect(status().isConflict());

    alice
        .patch("/api/v1/categories/" + pets, Map.of("name", "Animais", "kind", "EXPENSE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Animais"));

    alice
        .patch("/api/v1/transactions/" + importedTransactionId, Map.of("categoryId", pets))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categoryName").value("Animais"));

    String outros = categoryIdByName("Outros");
    alice
        .delete("/api/v1/categories/" + pets + "?reassignTo=" + outros)
        .andExpect(status().isNoContent());

    JsonNode after = transactions(alice);
    assertThat(after.path("items")).hasSize(1);
    assertThat(after.path("items").get(0).path("categoryName").asString()).isEqualTo("Outros");
    assertThat(after.path("items").get(0).path("editedFields").toString()).contains("category");
  }

  @Test
  void edicaoEExclusaoManualAtualizamSemAfetarImportadas() throws Exception {
    String cash = createAccount("CASH", "Carteira", "100.00");
    JsonNode created = createTransaction("CREDIT", cash, "50.00", "Venda", null, null, null);
    String id = created.path("id").asString();

    alice
        .patch(
            "/api/v1/transactions/" + id,
            Map.of("description", "Venda corrigida", "amount", "55.00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Venda corrigida"))
        .andExpect(jsonPath("$.amount").value("55.00"));

    alice.delete("/api/v1/transactions/" + id).andExpect(status().isNoContent());
    assertThat(transactionsForAccount(cash).path("items")).isEmpty();

    alice
        .delete("/api/v1/transactions/" + importedTransactionId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_MANUAL"));

    String bank = createAccount("BANK", "Conta Casa", "0.00");
    JsonNode transfer =
        createTransaction("TRANSFER", cash, "20.00", "Transferir", null, bank, null);
    alice
        .delete("/api/v1/transactions/" + transfer.path("id").asString())
        .andExpect(status().isNoContent());
    assertThat(transactionsForAccount(cash).path("items")).isEmpty();
    assertThat(transactionsForAccount(bank).path("items")).isEmpty();
  }

  @Test
  void isolamentoDeContasCategoriasELancamentosEntreUsuarios() throws Exception {
    String aliceCash = createAccount("CASH", "Carteira Alice", "100.00");
    String bobCash = createAccount("CASH", "Carteira Bob", "100.00", bob);

    alice.get("/api/v1/accounts/" + bobCash).andExpect(status().isNotFound());
    alice
        .patch("/api/v1/accounts/" + bobCash, Map.of("alias", "Invasão"))
        .andExpect(status().isNotFound());
    alice
        .post("/api/v1/transactions", request("DEBIT", bobCash, "10.00", "Invasão", null))
        .andExpect(status().isNotFound());
    assertThat(transactions(alice).toString()).doesNotContain("Carteira Bob");
    assertThat(accounts(bob).toString()).contains("Carteira Bob").doesNotContain("Carteira Alice");
    assertThat(transactionsForAccount(bobCash).path("items")).isEmpty();
    assertThat(aliceCash).isNotEqualTo(bobCash);
  }

  @Test
  void correcaoDeTransferenciaNaoPermiteAlterarValorOuDataDeUmaPerna() throws Exception {
    String cash = createAccount("CASH", "Carteira transferência", "100.00");
    String bank = createAccount("BANK", "Conta transferência", "0.00");
    JsonNode transfer =
        createTransactionOn("TRANSFER", cash, "30.00", "Para banco", "2026-10-24", bank);
    String legId = transfer.path("id").asString();

    alice
        .patch("/api/v1/transactions/" + legId, Map.of("amount", "40.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    alice
        .patch("/api/v1/transactions/" + legId, Map.of("postedOn", "2026-10-25"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    alice
        .patch("/api/v1/transactions/" + legId, Map.of("description", "Para banco corrigido"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Para banco corrigido"));

    JsonNode bankLeg = transactionsForAccount(bank).path("items").get(0);
    assertThat(bankLeg.path("amount").asString()).isEqualTo("30.00");
    assertThat(bankLeg.path("postedOn").asString()).isEqualTo("2026-10-24");
  }

  private String createAccount(String type, String alias, String balance) throws Exception {
    return createAccount(type, alias, balance, alice);
  }

  private String createAccount(String type, String alias, String balance, AuthTestClient client)
      throws Exception {
    String body =
        client
            .post(
                "/api/v1/accounts",
                Map.of(
                    "type", type,
                    "alias", alias,
                    "referenceBalance", balance,
                    "referenceDate", "2026-10-23T12:00:00Z",
                    "currency", "BRL"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json(body).path("id").asString();
  }

  private JsonNode createTransactionOn(
      String type,
      String accountId,
      String amount,
      String description,
      String postedOn,
      String transferAccountId)
      throws Exception {
    Map<String, Object> request = request(type, accountId, amount, description, null);
    request.put("postedOn", postedOn);
    if (transferAccountId != null) {
      request.put("transferAccountId", transferAccountId);
    }
    return create(request);
  }

  private JsonNode account(String accountId) throws Exception {
    return json(
        alice
            .get("/api/v1/accounts/" + accountId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode createTransaction(
      String type,
      String accountId,
      String amount,
      String description,
      String relatedTransactionId,
      String transferAccountId,
      String categoryId)
      throws Exception {
    Map<String, Object> request = request(type, accountId, amount, description, null);
    if (relatedTransactionId != null) {
      request.put("relatedTransactionId", relatedTransactionId);
    }
    if (transferAccountId != null) {
      request.put("transferAccountId", transferAccountId);
    }
    if (categoryId != null) {
      request.put("categoryId", categoryId);
    }
    return create(request);
  }

  private JsonNode create(Map<String, Object> request) throws Exception {
    String body =
        alice
            .post("/api/v1/transactions", request)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json(body);
  }

  private static Map<String, Object> request(
      String type, String accountId, String amount, String description, String categoryId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("accountId", accountId);
    body.put("type", type);
    body.put("postedOn", "2026-10-22");
    body.put("amount", amount);
    body.put("description", description);
    if (categoryId != null) {
      body.put("categoryId", categoryId);
    }
    return body;
  }

  private JsonNode accounts(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/accounts")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode transactions(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/transactions?limit=100")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode transactionsForAccount(String accountId) throws Exception {
    return json(
        alice
            .get("/api/v1/transactions?limit=100&accountId=" + accountId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode listCategories() throws Exception {
    return json(
        alice
            .get("/api/v1/categories")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String categoryIdByName(String name) throws Exception {
    for (JsonNode item : listCategories().path("items")) {
      if (name.equals(item.path("name").asString())) {
        return item.path("id").asString();
      }
    }
    throw new AssertionError("Categoria não encontrada: " + name);
  }

  private JsonNode internalMatches(String status) throws Exception {
    return json(
        alice
            .get("/api/v1/internal-matches?status=" + status)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
