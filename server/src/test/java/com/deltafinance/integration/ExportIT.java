package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
 * Exportação portátil (T139).
 *
 * <p>Prova que o ZIP contém os dados ativos apenas do titular, em JSON Lines e CSV, sem segredos,
 * credenciais, hashes ou dados de outro usuário, e que a auditoria da exportação é sanitizada.
 */
@AutoConfigureMockMvc
class ExportIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.export";
  private static final String BOB = "bob.export";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-export";
  private static final String BOB_CLIENT = "client-bob-export";
  private static final String ALICE_SECRET = "secret-alice-export";
  private static final Set<String> DATASETS =
      Set.of(
          "profile",
          "accounts",
          "card_profiles",
          "investment_positions",
          "manual_asset_liabilities",
          "financial_goals",
          "goal_allocations",
          "goal_progress_snapshots",
          "categories",
          "transactions",
          "invoices",
          "invoice_items",
          "invoice_payments",
          "provider_bills",
          "provider_bill_payments",
          "provider_investments",
          "provider_investment_transactions",
          "provider_investment_syncs",
          "investment_incomes",
          "invoice_item_corrections",
          "transaction_corrections",
          "internal_matches",
          "integrations",
          "connections",
          "sync_runs",
          "sync_jobs",
          "audit_events");

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
  private AuthTestClient bob;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(ALICE_CLIENT, ALICE_SECRET);
    PLUGGY.registerClient(BOB_CLIENT, "secret-bob-export");
    PLUGGY.addItem("item-alice-export", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice-export",
        new AccountFixture(
            "acc-alice-export", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-alice-export",
        new TransactionFixture(
            "tx-alice-export",
            "acc-alice-export",
            "Mercado Alice",
            "-100.00",
            "2026-10-05",
            "POSTED",
            "Mercado"));
    PLUGGY.addItem("item-bob-export", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-bob-export",
        new AccountFixture(
            "acc-bob-export", "BANK", "CHECKING_ACCOUNT", "Conta Beta", "500.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", ALICE_CLIENT, "clientSecret", ALICE_SECRET))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice-export"))
        .andExpect(status().isCreated());

    createManualAccount(alice, "Carteira Alice", "100.00");
    createManualTransaction(alice, "Salário Alice", "2026-10-02");
    createInvestment(alice, "Tesouro Alice");
    createManualWealthItem(alice, "Apartamento Alice");
    createGoal(alice, "Meta Alice");
    createManualAccount(bob, "Carteira Bob", "50.00");
    createManualTransaction(bob, "Salário Bob", "2026-10-02");
    createInvestment(bob, "Tesouro Bob");
    createManualWealthItem(bob, "Apartamento Bob");
    createGoal(bob, "Meta Bob");
  }

  @Test
  void exportaSomenteDadosDoTitularComJsonlCsvESemSegredos() throws Exception {
    MvcResult result = alice.get("/api/v1/me/export").andExpect(status().isOk()).andReturn();
    if (result.getRequest().isAsyncStarted()) {
      result = mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
    }
    byte[] zip = result.getResponse().getContentAsByteArray();
    Map<String, String> files = unzip(zip);

    Set<String> expectedFiles = new TreeSet<>();
    expectedFiles.add("manifest.json");
    for (String dataset : DATASETS) {
      expectedFiles.add(dataset + ".jsonl");
      expectedFiles.add(dataset + ".csv");
    }
    assertThat(files.keySet()).containsExactlyInAnyOrderElementsOf(expectedFiles);
    assertThat(files)
        .doesNotContainKeys(
            "wealth_snapshots.jsonl",
            "wealth_snapshots.csv",
            "wealth_snapshot_items.jsonl",
            "wealth_snapshot_items.csv",
            "webhook_subscriptions.jsonl",
            "webhook_subscriptions.csv",
            "webhook_events.jsonl",
            "webhook_events.csv");

    JsonNode manifest = json(files.get("manifest.json"));
    assertThat(manifest.path("format").asString()).isEqualTo("delta-finance-export");
    assertThat(manifest.path("version").asInt()).isEqualTo(1);
    assertThat(manifest.path("timezone").asString()).isEqualTo("America/Sao_Paulo");
    assertThat(manifest.path("currency").asString()).isEqualTo("BRL");
    assertThat(manifest.path("files").properties()).hasSize(DATASETS.size() * 2);

    int recordCount = 0;
    for (Map.Entry<String, JsonNode> entry : manifest.path("files").properties()) {
      String name = entry.getKey();
      int declared = entry.getValue().asInt();
      int expectedLines = name.endsWith(".csv") ? declared + 1 : declared;
      assertThat(lineCount(files.get(name))).as("contagem de %s", name).isEqualTo(expectedLines);
      if (name.endsWith(".jsonl")) {
        recordCount += declared;
      }
    }
    assertThat(manifest.path("recordCount").asInt()).isEqualTo(recordCount);
    assertThat(manifest.path("files").path("accounts.jsonl").asInt()).isGreaterThanOrEqualTo(2);
    assertThat(manifest.path("files").path("transactions.jsonl").asInt()).isGreaterThanOrEqualTo(2);
    assertThat(manifest.path("files").path("investment_positions.jsonl").asInt()).isEqualTo(1);
    assertThat(manifest.path("files").path("manual_asset_liabilities.jsonl").asInt()).isEqualTo(1);
    assertThat(manifest.path("files").path("financial_goals.jsonl").asInt()).isEqualTo(1);
    assertThat(manifest.path("files").path("goal_allocations.jsonl").asInt()).isEqualTo(1);
    assertThat(manifest.path("files").path("goal_progress_snapshots.jsonl").asInt())
        .isGreaterThanOrEqualTo(1);

    assertThat(files.get("profile.jsonl")).contains(ALICE).doesNotContain(BOB);
    assertThat(files.get("accounts.jsonl"))
        .contains("Carteira Alice", "Conta Alfa")
        .doesNotContain("Carteira Bob", "Conta Beta");
    assertThat(files.get("transactions.jsonl"))
        .contains("Salário Alice", "Mercado Alice")
        .doesNotContain("Salário Bob");
    assertThat(files.get("investment_positions.jsonl"))
        .contains("Tesouro Alice")
        .doesNotContain("Tesouro Bob");
    assertThat(files.get("manual_asset_liabilities.jsonl"))
        .contains("Apartamento Alice")
        .doesNotContain("Apartamento Bob");
    assertThat(files.get("financial_goals.jsonl"))
        .contains("Meta Alice", "Observação de Meta Alice")
        .doesNotContain("Meta Bob");
    assertThat(files.get("goal_allocations.jsonl")).isNotBlank();
    assertThat(files.get("goal_progress_snapshots.jsonl")).isNotBlank();
    assertThat(files.get("financial_goals.jsonl"))
        .doesNotContain("initialValue", "DEBT_REDUCTION", "MANUAL_LIABILITY");

    assertThat(files.get("accounts.csv")).contains("Carteira Alice").doesNotContain("Carteira Bob");
    assertThat(files.get("transactions.csv"))
        .contains("Salário Alice")
        .doesNotContain("Salário Bob");

    assertThat(files.get("integrations.jsonl")).contains("MEU_PLUGGY");
    String all = String.join("\n", files.values());
    assertThat(all)
        .doesNotContain(ALICE_SECRET)
        .doesNotContain(ALICE_CLIENT)
        .doesNotContainIgnoringCase("clientSecret")
        .doesNotContainIgnoringCase("client_secret")
        .doesNotContainIgnoringCase("apiKey")
        .doesNotContainIgnoringCase("password_hash")
        .doesNotContain("Salário Bob", "Tesouro Bob", "Apartamento Bob", "Meta Bob");

    String metadata =
        jdbcTemplate.queryForObject(
            "select metadata::text from audit_event where event_type = 'EXPORT_REQUESTED'",
            String.class);
    assertThat(metadata).contains("zip").doesNotContain(ALICE_SECRET, "Carteira", "Salário");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_event where event_type = 'EXPORT_REQUESTED'",
                Integer.class))
        .isEqualTo(1);
  }

  private void createInvestment(AuthTestClient client, String assetName) throws Exception {
    client
        .post(
            "/api/v1/investments/positions",
            Map.of(
                "broker", "XP",
                "assetName", assetName,
                "quantity", "1",
                "totalValue", "500.00",
                "referenceDate", "2026-10-01"))
        .andExpect(status().isCreated());
  }

  private void createManualWealthItem(AuthTestClient client, String description) throws Exception {
    client
        .post(
            "/api/v1/wealth/manual-items",
            Map.of(
                "kind", "ASSET",
                "type", "IMOVEL",
                "description", description,
                "referenceValue", "100000.00",
                "referenceDate", "2026-10-01"))
        .andExpect(status().isCreated());
  }

  private void createGoal(AuthTestClient client, String name) throws Exception {
    Map<String, Object> allocation = new LinkedHashMap<>();
    allocation.put("sourceType", "MANUAL");
    allocation.put("allocationMode", "FIXED_AMOUNT");
    allocation.put("allocatedAmount", "100.00");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("notes", "Observação de " + name);
    body.put("type", "TRAVEL");
    body.put("targetAmount", "1000.00");
    body.put("progressRule", "ALLOCATIONS");
    body.put("allocations", List.of(allocation));
    client.post("/api/v1/goals", body).andExpect(status().isCreated());
  }

  private void createManualAccount(AuthTestClient client, String alias, String balance)
      throws Exception {
    client
        .post(
            "/api/v1/accounts",
            Map.of(
                "type",
                "CASH",
                "alias",
                alias,
                "referenceBalance",
                balance,
                "referenceDate",
                Instant.parse("2026-10-01T12:00:00Z").toString()))
        .andExpect(status().isCreated());
  }

  private void createManualTransaction(AuthTestClient client, String description, String postedOn)
      throws Exception {
    String accountId =
        json(client
                .get("/api/v1/accounts")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("items")
            .get(0)
            .path("id")
            .asString();
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", accountId);
    request.put("type", "CREDIT");
    request.put("postedOn", postedOn);
    request.put("amount", "500.00");
    request.put("description", description);
    client.post("/api/v1/transactions", request).andExpect(status().isCreated());
  }

  private static int lineCount(String content) {
    if (content == null || content.isBlank()) {
      return 0;
    }
    return (int) content.lines().count();
  }

  private Map<String, String> unzip(byte[] zip) throws Exception {
    Map<String, String> files = new HashMap<>();
    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
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
