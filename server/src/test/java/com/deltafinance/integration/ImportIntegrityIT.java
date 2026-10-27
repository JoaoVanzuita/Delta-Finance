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

/** Integridade da importação: identidade, dados obrigatórios e reembolsos (C05–C07). */
@AutoConfigureMockMvc
class ImportIntegrityIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.import.integrity";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-import-integrity";

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

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
  }

  @Test
  void duasComprasIdenticasComIdsDiferentesNaoSaoFundidas() throws Exception {
    addItem("item-c05", "acc-c05", "BANK", "Conta C05", "1000.00");
    PLUGGY.addTransaction(
        "acc-c05",
        new TransactionFixture(
            "tx-c05-a", "acc-c05", "Café", "-10.00", "2026-10-20", "POSTED", "Alimentação"));
    PLUGGY.addTransaction(
        "acc-c05",
        new TransactionFixture(
            "tx-c05-b", "acc-c05", "Café", "-10.00", "2026-10-20", "POSTED", "Alimentação"));

    String connectionId = connect("item-c05");
    assertThat(transactionsMatching("Café")).isEqualTo(2);

    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "c05");
    assertThat(transactionsMatching("Café")).isEqualTo(2);
  }

  @Test
  void transacaoSemValorOuDataEhDescartadaComDiagnostico() throws Exception {
    addItem("item-c06", "acc-c06", "BANK", "Conta C06", "1000.00");
    PLUGGY.addTransaction(
        "acc-c06",
        new TransactionFixture(
            "tx-sem-valor", "acc-c06", "Sem valor", null, "2026-10-20", "POSTED", null));
    PLUGGY.addTransaction(
        "acc-c06",
        new TransactionFixture(
            "tx-sem-data", "acc-c06", "Sem data", "-5.00", null, "POSTED", null));

    connect("item-c06");

    JsonNode transactions = transactions();
    assertThat(transactions.toString()).doesNotContain("Sem valor").doesNotContain("Sem data");

    JsonNode latest = syncRuns().path("items").get(0);
    assertThat(latest.path("status").asString()).isEqualTo("PARTIAL");
    assertThat(latest.path("readCount").asInt()).isEqualTo(2);
    assertThat(latest.path("failedCount").asInt()).isEqualTo(2);
    assertThat(latest.path("errorCode").asString()).isEqualTo("IMPORT_MISSING_FIELDS");
  }

  @Test
  void tipoDeContaDesconhecidoFicaUnknownEForaDoSaldo() throws Exception {
    addItem("item-unknown", "acc-unknown", "INVESTMENT", "Investimento", "100.00");

    connect("item-unknown");

    JsonNode account = accounts().path("items").get(0);
    assertThat(account.path("type").asString()).isEqualTo("UNKNOWN");
    assertThat(account.path("currentBalance").asString()).isEqualTo("100.00");

    JsonNode summary =
        json(
            alice
                .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(summary.path("currentBalance").asString()).isEqualTo("0.00");
  }

  @Test
  void reembolsoAmbigouOuComDescricaoIncompativelNaoVinculaNemReduzDespesa() throws Exception {
    addItem("item-c07", "acc-c07", "BANK", "Conta C07", "1000.00");
    PLUGGY.addTransaction(
        "acc-c07",
        new TransactionFixture(
            "tx-c07-a", "acc-c07", "Mercado", "-50.00", "2026-10-01", "POSTED", "Mercado"));
    PLUGGY.addTransaction(
        "acc-c07",
        new TransactionFixture(
            "tx-c07-b", "acc-c07", "Mercado", "-50.00", "2026-10-01", "POSTED", "Mercado"));
    PLUGGY.addTransaction(
        "acc-c07",
        new TransactionFixture(
            "tx-c07-c", "acc-c07", "Padaria", "-20.00", "2026-10-02", "POSTED", "Alimentação"));
    PLUGGY.addTransaction(
        "acc-c07",
        new TransactionFixture(
            "tx-c07-refund-ambiguous",
            "acc-c07",
            "Reembolso Mercado",
            "50.00",
            "2026-10-03",
            "POSTED",
            "Mercado"));
    PLUGGY.addTransaction(
        "acc-c07",
        new TransactionFixture(
            "tx-c07-refund-mismatch",
            "acc-c07",
            "Estorno Farmácia",
            "20.00",
            "2026-10-03",
            "POSTED",
            "Alimentação"));

    connect("item-c07");

    JsonNode transactions = transactions();
    assertThat(find(transactions, "Reembolso Mercado").path("relatedTransactionId").isNull())
        .isTrue();
    assertThat(find(transactions, "Estorno Farmácia").path("relatedTransactionId").isNull())
        .isTrue();

    JsonNode summary =
        json(
            alice
                .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(summary.path("income").asString()).isEqualTo("0.00");
    assertThat(summary.path("expenses").asString()).isEqualTo("120.00");
  }

  private void addItem(String itemId, String accountId, String type, String alias, String balance) {
    PLUGGY.addItem(itemId, "Banco Teste", "UPDATED");
    PLUGGY.addAccount(
        itemId, new AccountFixture(accountId, type, "CHECKING_ACCOUNT", alias, balance, null));
  }

  private String connect(String itemId) throws Exception {
    String body =
        alice
            .post("/api/v1/connections", Map.of("itemId", itemId))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(body).path("id").asString();
  }

  private int transactionsMatching(String description) throws Exception {
    int count = 0;
    for (JsonNode item : transactions().path("items")) {
      if (description.equals(item.path("description").asString())) {
        count++;
      }
    }
    return count;
  }

  private JsonNode find(JsonNode transactions, String description) {
    for (JsonNode item : transactions.path("items")) {
      if (description.equals(item.path("description").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + description);
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

  private JsonNode accounts() throws Exception {
    return json(
        alice
            .get("/api/v1/accounts")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode syncRuns() throws Exception {
    return json(
        alice
            .get("/api/v1/sync/runs")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
