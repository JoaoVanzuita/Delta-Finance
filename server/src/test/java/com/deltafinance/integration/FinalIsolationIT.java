package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

/**
 * Matriz final de acesso cruzado (T183).
 *
 * <p>Dois usuários e duas integrações independentes; cobre leitura, escrita, exclusão, exportação,
 * relatórios e a remoção/restauração automática de {@code SOURCE_REMOVED} sem atravessar o limite
 * do titular.
 */
@AutoConfigureMockMvc
class FinalIsolationIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.final.iso";
  private static final String BOB = "bob.final.iso";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-final-iso";
  private static final String BOB_CLIENT = "client-bob-final-iso";
  private static final String ALICE_SECRET = "secret-alice-final-iso";
  private static final String BOB_SECRET = "secret-bob-final-iso";

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
  @Autowired private PluggyRateLimiter rateLimiter;
  @Autowired private SyncJobService syncJobService;

  private AuthTestClient alice;
  private AuthTestClient bob;
  private String aliceAccountId;
  private String aliceTransactionId;
  private String aliceConnectionId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(ALICE_CLIENT, ALICE_SECRET);
    PLUGGY.registerClient(BOB_CLIENT, BOB_SECRET);
    PLUGGY.addItem("item-alice-final-iso", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice-final-iso",
        new AccountFixture(
            "acc-alice-final-iso", "BANK", "CHECKING_ACCOUNT", "Conta Alice", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-alice-final-iso",
        new TransactionFixture(
            "tx-alice-final-iso",
            "acc-alice-final-iso",
            "Mercado Alice",
            "-120.00",
            "2026-10-23",
            "POSTED",
            "Mercado"));
    PLUGGY.addTransaction(
        "acc-alice-final-iso",
        new TransactionFixture(
            "tx-alice-final-iso-2",
            "acc-alice-final-iso",
            "Padaria Alice",
            "-20.00",
            "2026-10-22",
            "POSTED",
            "Padaria"));
    PLUGGY.addItem("item-bob-final-iso", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-bob-final-iso",
        new AccountFixture(
            "acc-bob-final-iso", "BANK", "CHECKING_ACCOUNT", "Conta Bob", "800.00", null));
    PLUGGY.addTransaction(
        "acc-bob-final-iso",
        new TransactionFixture(
            "tx-bob-final-iso",
            "acc-bob-final-iso",
            "Mercado Bob",
            "-80.00",
            "2026-10-23",
            "POSTED",
            "Mercado"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    configureAndSync(alice, ALICE_CLIENT, ALICE_SECRET, "item-alice-final-iso");
    configureAndSync(bob, BOB_CLIENT, BOB_SECRET, "item-bob-final-iso");

    aliceConnectionId = firstConnectionId(alice);
    aliceAccountId = firstAccountId(alice);
    aliceTransactionId = transactionIdByDescription(alice, "Mercado Alice");
  }

  @Test
  void leituraEscritaExclusaoExportacaoRelatoriosERemocaoAutomaticaSaoIsolados() throws Exception {
    // Leitura cruzada por ID: 404 sem vazar dados.
    bob.get("/api/v1/accounts/" + aliceAccountId).andExpect(status().isNotFound());

    // Escrita cruzada: Bob não altera conta nem cria lançamento em conta de Alice.
    bob.patch("/api/v1/accounts/" + aliceAccountId, Map.of("alias", "Invadida"))
        .andExpect(status().isNotFound());
    Map<String, Object> intrusion = new LinkedHashMap<>();
    intrusion.put("accountId", aliceAccountId);
    intrusion.put("type", "DEBIT");
    intrusion.put("postedOn", "2026-10-06");
    intrusion.put("amount", "10.00");
    intrusion.put("description", "Intrusão");
    bob.post("/api/v1/transactions", intrusion).andExpect(status().isNotFound());
    bob.delete("/api/v1/transactions/" + aliceTransactionId).andExpect(status().isNotFound());
    bob.delete("/api/v1/connections/" + aliceConnectionId).andExpect(status().isNotFound());
    bob.post("/api/v1/connections/" + aliceConnectionId + "/sync", null)
        .andExpect(status().isNotFound());

    // Exportação contém apenas o titular.
    Map<String, String> aliceExport = export(alice);
    assertThat(aliceExport.get("transactions.jsonl"))
        .contains("Mercado Alice")
        .doesNotContain("Mercado Bob");
    assertThat(aliceExport.get("accounts.jsonl"))
        .contains("Conta Alice")
        .doesNotContain("Conta Bob");
    Map<String, String> bobExport = export(bob);
    assertThat(bobExport.get("transactions.jsonl"))
        .contains("Mercado Bob")
        .doesNotContain("Mercado Alice");

    // Relatórios refletem apenas o próprio titular.
    JsonNode aliceSummary = summary(alice);
    JsonNode bobSummary = summary(bob);
    assertThat(aliceSummary.path("expenses").asString()).isEqualTo("140.00");
    assertThat(bobSummary.path("expenses").asString()).isEqualTo("80.00");

    // SOURCE_REMOVED automático: a linha de Alice sai dos totais sem afetar Bob.
    PLUGGY.removeTransaction("acc-alice-final-iso", "tx-alice-final-iso");
    sync(alice);
    JsonNode missing = transaction(alice, aliceTransactionId);
    assertThat(missing.path("status").asString()).isEqualTo("SOURCE_REMOVED");
    assertThat(missing.path("reconciliationState").asString()).isEqualTo("SOURCE_REMOVED");
    assertThat(summary(alice).path("expenses").asString()).isEqualTo("20.00");
    assertThat(summary(bob).path("expenses").asString()).isEqualTo("80.00");

    // Quando a origem volta a trazer o item, a mesma linha é restaurada automaticamente.
    PLUGGY.addTransaction(
        "acc-alice-final-iso",
        new TransactionFixture(
            "tx-alice-final-iso",
            "acc-alice-final-iso",
            "Mercado Alice",
            "-120.00",
            "2026-10-23",
            "POSTED",
            "Mercado"));
    sync(alice);
    JsonNode restored = transaction(alice, aliceTransactionId);
    assertThat(restored.path("status").asString()).isEqualTo("POSTED");
    assertThat(restored.path("reconciliationState").asString()).isEqualTo("NONE");
    assertThat(summary(alice).path("expenses").asString()).isEqualTo("140.00");
    assertThat(summary(bob).path("expenses").asString()).isEqualTo("80.00");

    PLUGGY.removeTransaction("acc-alice-final-iso", "tx-alice-final-iso");
    sync(alice);
    JsonNode removedAgain = transaction(alice, aliceTransactionId);
    assertThat(removedAgain.path("status").asString()).isEqualTo("SOURCE_REMOVED");
    assertThat(
            alice
                .get("/api/v1/transactions?limit=100")
                .andReturn()
                .getResponse()
                .getContentAsString())
        .contains("Mercado Alice");
    assertThat(summary(alice).path("expenses").asString()).isEqualTo("20.00");

    // Bob segue intacto.
    assertThat(summary(bob).path("expenses").asString()).isEqualTo("80.00");
    bob.get("/api/v1/auth/me").andExpect(status().isOk());
  }

  private void configureAndSync(
      AuthTestClient client, String clientId, String clientSecret, String itemId) throws Exception {
    client
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", clientId, "clientSecret", clientSecret))
        .andExpect(status().isOk());
    client.post("/api/v1/connections", Map.of("itemId", itemId)).andExpect(status().isCreated());
    sync(client);
  }

  private void sync(AuthTestClient client) throws Exception {
    String connectionId = firstConnectionId(client);
    client
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    while (syncJobService.processNext("final-iso", "final-iso").isPresent()) {
      // processa toda a fila até esvaziar
    }
  }

  private String firstConnectionId(AuthTestClient client) throws Exception {
    return json(client.get("/api/v1/connections").andReturn().getResponse().getContentAsString())
        .path("items")
        .get(0)
        .path("id")
        .asString();
  }

  private String firstAccountId(AuthTestClient client) throws Exception {
    return json(client.get("/api/v1/accounts").andReturn().getResponse().getContentAsString())
        .path("items")
        .get(0)
        .path("id")
        .asString();
  }

  private String transactionIdByDescription(AuthTestClient client, String description)
      throws Exception {
    JsonNode page =
        json(
            client
                .get("/api/v1/transactions?limit=100")
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode item : page.path("items")) {
      if (description.equals(item.path("description").asString())) {
        return item.path("id").asString();
      }
    }
    throw new AssertionError("Transação não encontrada: " + description);
  }

  private JsonNode transaction(AuthTestClient client, String id) throws Exception {
    JsonNode page =
        json(
            client
                .get("/api/v1/transactions?limit=100")
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode item : page.path("items")) {
      if (id.equals(item.path("id").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + id);
  }

  private JsonNode summary(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private Map<String, String> export(AuthTestClient client) throws Exception {
    MvcResult result = client.get("/api/v1/me/export").andExpect(status().isOk()).andReturn();
    if (result.getRequest().isAsyncStarted()) {
      result = mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
    }
    Map<String, String> files = new HashMap<>();
    try (ZipInputStream input =
        new ZipInputStream(
            new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        files.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
        input.closeEntry();
      }
    }
    return files;
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
