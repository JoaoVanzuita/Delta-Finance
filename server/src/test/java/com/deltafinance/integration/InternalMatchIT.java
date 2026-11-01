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

/** Sugestões determinísticas, confirmação/rejeição e falsos positivos (T106). */
@AutoConfigureMockMvc
class InternalMatchIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.match";
  private static final String BOB = "bob.match";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-match";
  private static final String BOB_CLIENT = "client-bob-match";

  @DynamicPropertySource
  static void pluggyProperties(DynamicPropertyRegistry registry) {
    registry.add("delta.pluggy.base-url", PLUGGY::baseUrl);
    // A remoção na origem usa a janela diária; mantenha as datas fixas do teste cobertas.
    registry.add("delta.sync.daily-window-days", () -> "3650");
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
  private String bankAccountId;
  private String creditAccountId;

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
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("card-alice", "CREDIT", "CREDIT_CARD", "Cartão Alfa", "0.00", null));
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

    for (JsonNode account : accounts(alice).path("items")) {
      if ("BANK".equals(account.path("type").asString())) {
        bankAccountId = account.path("id").asString();
      }
      if ("CREDIT".equals(account.path("type").asString())) {
        creditAccountId = account.path("id").asString();
      }
    }
  }

  @Test
  void sincronizacaoGeraSugestoesSemAbrirATelaDeReconciliacao() throws Exception {
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-transfer-out",
            "acc-alice",
            "Saída para carteira",
            "-100.00",
            "2026-10-22",
            "POSTED",
            null));
    String wallet = createAccount("CASH", "Carteira");
    createManualTransaction("CREDIT", wallet, "100.00", "Entrada da conta", "2026-10-22");

    alice
        .post("/api/v1/connections/" + connectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("internal-match-it", "internal-match-it");

    JsonNode summary =
        json(
            alice
                .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(summary.path("pendingInternalMatches").asInt()).isEqualTo(1);
  }

  @Test
  void sugereConfirmaEExcluiTransferenciaInterna() throws Exception {
    String wallet = createAccount("CASH", "Carteira");
    String debit = createTransaction("DEBIT", bankAccountId, "-100.00", "2026-10-22");
    String credit = createTransaction("CREDIT", wallet, "100.00", "2026-10-22");

    JsonNode matches = internalMatches("PENDING");
    assertThat(matches.path("items")).hasSize(1);
    JsonNode match = matches.path("items").get(0);
    assertThat(match.path("kind").asString()).isEqualTo("TRANSFER");
    assertThat(match.path("score").asDouble()).isGreaterThanOrEqualTo(80);
    assertThat(match.path("status").asString()).isEqualTo("PENDING");
    assertThat(match.path("criteriaVersion").asString()).isEqualTo("v1");

    JsonNode confirmed =
        json(
            alice
                .post("/api/v1/internal-matches/" + match.path("id").asString() + "/confirm", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(confirmed.path("debitTransactionId").asString()).isEqualTo(debit);
    assertThat(confirmed.path("creditTransactionId").asString()).isEqualTo(credit);

    JsonNode transactions = transactions(alice);
    assertThat(findById(transactions, debit).path("reconciliationState").asString())
        .isEqualTo("MATCHED");
    assertThat(findById(transactions, credit).path("reconciliationState").asString())
        .isEqualTo("MATCHED");

    alice
        .patch("/api/v1/transactions/" + debit, Map.of("amount", "-90.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    alice
        .patch("/api/v1/transactions/" + debit, Map.of("postedOn", "2026-10-23"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    alice
        .patch("/api/v1/transactions/" + debit, Map.of("description", "Transferência revista"))
        .andExpect(status().isOk());

    alice
        .post("/api/v1/internal-matches/" + match.path("id").asString() + "/confirm", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MATCH_ALREADY_DECIDED"));
  }

  @Test
  void classificaPagamentoDeCartaoSeparadamente() throws Exception {
    createTransaction("DEBIT", bankAccountId, "-200.00", "2026-10-22");
    createTransaction("CREDIT", creditAccountId, "200.00", "2026-10-22");

    JsonNode matches = internalMatches("PENDING");
    assertThat(matches.path("items")).hasSize(1);
    assertThat(matches.path("items").get(0).path("kind").asString()).isEqualTo("CARD_PAYMENT");
  }

  @Test
  void normalizaSinalDeCartaoDoPluggyEDetectaPagamentoDeFatura() throws Exception {
    alice
        .put(
            "/api/v1/cards/" + creditAccountId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "5000.00"))
        .andExpect(status().isOk());
    // Convenção do Pluggy em cartões: compra é positiva e pagamento é negativo.
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-bank-card-payment",
            "acc-alice",
            "Pagamento fatura cartão",
            "-300.00",
            "2026-10-22",
            "POSTED",
            null));
    PLUGGY.addTransaction(
        "card-alice",
        new TransactionFixture(
            "tx-card-payment",
            "card-alice",
            "Pagamento recebido",
            "-300.00",
            "2026-10-22",
            "POSTED",
            null));
    PLUGGY.addTransaction(
        "card-alice",
        new TransactionFixture(
            "tx-card-purchase",
            "card-alice",
            "Compra loja",
            "100.00",
            "2026-10-23",
            "POSTED",
            "Compras"));
    sync();

    JsonNode transactions = transactions(alice);
    JsonNode purchase = findTransactionByDescription(transactions, "Compra loja");
    assertThat(purchase.path("amount").asString()).isEqualTo("-100.00");
    assertThat(purchase.path("type").asString()).isEqualTo("DEBIT");
    JsonNode payment = findTransactionByDescription(transactions, "Pagamento recebido");
    assertThat(payment.path("amount").asString()).isEqualTo("300.00");
    assertThat(payment.path("type").asString()).isEqualTo("REFUND");

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31");
    assertThat(summary.path("expenses").asString()).isEqualTo("400.00");
    assertThat(summary.path("pendingInternalMatches").asInt()).isEqualTo(1);

    JsonNode match = internalMatches("PENDING").path("items").get(0);
    assertThat(match.path("kind").asString()).isEqualTo("CARD_PAYMENT");
    alice
        .post("/api/v1/internal-matches/" + match.path("id").asString() + "/confirm", null)
        .andExpect(status().isOk());

    JsonNode after = summary("from=2026-10-01&to=2026-10-31");
    assertThat(after.path("expenses").asString()).isEqualTo("100.00");
    assertThat(after.path("pendingInternalMatches").asInt()).isZero();

    JsonNode invoices =
        json(
            alice
                .get("/api/v1/cards/" + creditAccountId + "/invoices")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode invoice = null;
    for (JsonNode candidate : invoices.path("items")) {
      if ("2026-09-06".equals(candidate.path("cycleStart").asString())) {
        invoice = candidate;
      }
    }
    assertThat(invoice).isNotNull();
    JsonNode detail =
        json(
            alice
                .get("/api/v1/invoices/" + invoice.path("id").asString())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(detail.path("payments")).hasSize(1);
    assertThat(detail.path("payments").get(0).path("paymentAccountId").asString())
        .isEqualTo(bankAccountId);
  }

  @Test
  void reabreCorrespondenciaQuandoDadosImportadosMudamDeSinal() throws Exception {
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-bank",
            "acc-alice",
            "Pagamento fatura cartão",
            "-150.00",
            "2026-10-22",
            "POSTED",
            null));
    PLUGGY.addTransaction(
        "card-alice",
        new TransactionFixture(
            "tx-card",
            "card-alice",
            "Pagamento recebido",
            "-150.00",
            "2026-10-22",
            "POSTED",
            null));
    sync();

    JsonNode match = internalMatches("PENDING").path("items").get(0);
    assertThat(match.path("kind").asString()).isEqualTo("CARD_PAYMENT");
    alice
        .post("/api/v1/internal-matches/" + match.path("id").asString() + "/confirm", null)
        .andExpect(status().isOk());
    assertThat(internalMatches("CONFIRMED").path("items")).hasSize(1);

    // A origem passa a reportar o mesmo lançamento como compra: a correspondência confirmada
    // deixa de fazer sentido e deve ser reaberta em vez de continuar excluindo as pernas.
    PLUGGY.setTransactions(
        "card-alice",
        java.util.List.of(
            new TransactionFixture(
                "tx-card",
                "card-alice",
                "Pagamento recebido",
                "150.00",
                "2026-10-22",
                "POSTED",
                null)));
    sync();

    assertThat(internalMatches("CONFIRMED").path("items")).isEmpty();
    JsonNode card = findTransactionByDescription(transactions(alice), "Pagamento recebido");
    assertThat(card.path("amount").asString()).isEqualTo("-150.00");
    assertThat(card.path("reconciliationState").asString()).isEqualTo("NONE");
  }

  @Test
  void aplicaToleranciaJanelaEIgnoraMesmaConta() throws Exception {
    String wallet = createAccount("CASH", "Carteira");

    // Diferença de 0,40 dentro da tolerância (0,5% de 100,40) e mesma data.
    createTransaction("DEBIT", bankAccountId, "-100.00", "2026-10-22");
    createTransaction("CREDIT", wallet, "100.40", "2026-10-22");
    assertThat(internalMatches("PENDING").path("items")).hasSize(1);

    cleanUserData();
    // Diferença acima da tolerância.
    createTransaction("DEBIT", bankAccountId, "-100.00", "2026-10-22");
    createTransaction("CREDIT", wallet, "100.80", "2026-10-22");
    assertThat(internalMatches("PENDING").path("items")).isEmpty();

    cleanUserData();
    // Fora da janela de ±3 dias para transferência.
    createTransaction("DEBIT", bankAccountId, "-100.00", "2026-10-22");
    createTransaction("CREDIT", wallet, "100.00", "2026-10-16");
    assertThat(internalMatches("PENDING").path("items")).isEmpty();

    cleanUserData();
    // Mesma conta: não é transferência interna.
    createTransaction("DEBIT", bankAccountId, "-100.00", "2026-10-22");
    createTransaction("CREDIT", bankAccountId, "100.00", "2026-10-22");
    assertThat(internalMatches("PENDING").path("items")).isEmpty();
  }

  @Test
  void empateEscolheMenorIdentificadorERejeitadoNaoVolta() throws Exception {
    String wallet = createAccount("CASH", "Carteira");
    createTransaction("DEBIT", bankAccountId, "-50.00", "2026-10-22");
    String firstCredit = createTransaction("CREDIT", wallet, "50.00", "2026-10-22");
    String secondCredit = createTransaction("CREDIT", wallet, "50.00", "2026-10-22");

    JsonNode first = internalMatches("PENDING");
    assertThat(first.path("items")).hasSize(1);
    String chosenCredit = first.path("items").get(0).path("creditTransactionId").asString();
    java.util.UUID firstId = java.util.UUID.fromString(firstCredit);
    java.util.UUID secondId = java.util.UUID.fromString(secondCredit);
    String expected = firstId.compareTo(secondId) < 0 ? firstCredit : secondCredit;
    assertThat(chosenCredit).isEqualTo(expected);

    alice
        .post(
            "/api/v1/internal-matches/"
                + first.path("items").get(0).path("id").asString()
                + "/reject",
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));

    JsonNode second = internalMatches("PENDING");
    assertThat(second.path("items")).hasSize(1);
    assertThat(second.path("items").get(0).path("creditTransactionId").asString())
        .isNotEqualTo(chosenCredit);
    assertThat(internalMatches("REJECTED").path("items")).hasSize(1);
  }

  @Test
  void sugestoesSaoIsoladasEntreUsuarios() throws Exception {
    String wallet = createAccount("CASH", "Carteira");
    createTransaction("DEBIT", bankAccountId, "-75.00", "2026-10-22");
    createTransaction("CREDIT", wallet, "75.00", "2026-10-22");
    assertThat(internalMatches("PENDING").path("items")).hasSize(1);

    JsonNode bobMatches =
        json(
            bob.get("/api/v1/internal-matches")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(bobMatches.path("items")).isEmpty();
  }

  @Test
  void correspondenciaConfirmadaSobreviveARemocaoETemporariaNaOrigem() throws Exception {
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-out", "acc-alice", "Saída para carteira", "-80.00", "2026-10-22", "POSTED", null));
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-keep", "acc-alice", "Padaria", "-10.00", "2026-10-22", "POSTED", null));
    String wallet = createAccount("CASH", "Carteira");
    createManualTransaction("CREDIT", wallet, "80.00", "Entrada da conta", "2026-10-22");
    sync();

    JsonNode match = internalMatches("PENDING").path("items").get(0);
    alice
        .post("/api/v1/internal-matches/" + match.path("id").asString() + "/confirm", null)
        .andExpect(status().isOk());
    JsonNode out = findTransactionByDescription(transactions(alice), "Saída para carteira");
    assertThat(out.path("reconciliationState").asString()).isEqualTo("MATCHED");

    // A perna desaparece temporariamente da origem e depois volta com o mesmo identificador.
    PLUGGY.setTransactions(
        "acc-alice",
        java.util.List.of(
            new TransactionFixture(
                "tx-keep", "acc-alice", "Padaria", "-10.00", "2026-10-22", "POSTED", null)));
    sync();
    JsonNode removed = findTransactionByDescription(transactions(alice), "Saída para carteira");
    assertThat(removed.path("status").asString()).isEqualTo("SOURCE_REMOVED");
    assertThat(internalMatches("CONFIRMED").path("items")).hasSize(1);

    PLUGGY.setTransactions(
        "acc-alice",
        java.util.List.of(
            new TransactionFixture(
                "tx-out",
                "acc-alice",
                "Saída para carteira",
                "-80.00",
                "2026-10-22",
                "POSTED",
                null),
            new TransactionFixture(
                "tx-keep", "acc-alice", "Padaria", "-10.00", "2026-10-22", "POSTED", null)));
    sync();
    JsonNode restored = findTransactionByDescription(transactions(alice), "Saída para carteira");
    assertThat(restored.path("status").asString()).isEqualTo("POSTED");
    assertThat(restored.path("reconciliationState").asString()).isEqualTo("MATCHED");
    assertThat(internalMatches("CONFIRMED").path("items")).hasSize(1);

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31");
    assertThat(summary.path("expenses").asString()).isEqualTo("10.00");
    assertThat(summary.path("income").asString()).isEqualTo("0.00");
  }

  private void cleanUserData() {
    // Remove transações do teste anterior sem apagar usuários/integrações.
    jdbcTemplate.execute("delete from internal_match");
    jdbcTemplate.execute("delete from transaction_correction");
    jdbcTemplate.execute("delete from external_identity_history");
    jdbcTemplate.execute("delete from transaction");
  }

  private String connectionId() throws Exception {
    return json(alice
            .get("/api/v1/connections")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("items")
        .get(0)
        .path("id")
        .asString();
  }

  private String createManualTransaction(
      String type, String accountId, String amount, String description, String postedOn)
      throws Exception {
    return json(alice
            .post(
                "/api/v1/transactions",
                Map.of(
                    "accountId", accountId,
                    "type", type,
                    "amount", amount,
                    "description", description,
                    "postedOn", postedOn))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
  }

  private String createAccount(String type, String alias) throws Exception {
    String body =
        alice
            .post(
                "/api/v1/accounts",
                Map.of(
                    "type", type,
                    "alias", alias,
                    "referenceBalance", "0.00",
                    "referenceDate", "2026-10-23T12:00:00Z",
                    "currency", "BRL"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMarker(body);
  }

  private String createTransaction(String type, String accountId, String amount, String postedOn)
      throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", accountId);
    request.put("type", type);
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", "Movimento " + amount);
    String body =
        alice
            .post("/api/v1/transactions", request)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json(body).path("id").asString();
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

  private static JsonNode findById(JsonNode page, String id) {
    for (JsonNode item : page.path("items")) {
      if (id.equals(item.path("id").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + id);
  }

  private static JsonNode findTransactionByDescription(JsonNode page, String description) {
    for (JsonNode item : page.path("items")) {
      if (description.equals(item.path("description").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + description);
  }

  private void sync() throws Exception {
    alice
        .post("/api/v1/connections/" + connectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("internal-match-it", "internal-match-it");
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

  private String jsonMarker(String body) {
    return json(body).path("id").asString();
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
