package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PostgresTestBase;
import java.util.LinkedHashMap;
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
 * Importação de investimentos: cobertura, idempotência, posições encerradas, proventos e isolamento
 * de falhas. Nenhum dado real é usado.
 */
@AutoConfigureMockMvc
class InvestmentImportIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.investimport";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-investimport";
  private static final String ITEM = "item-investimport";

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
  @Autowired private SyncJobService syncJobService;
  @Autowired private CryptoService cryptoService;

  private AuthTestClient alice;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "MeuPluggy", "UPDATED");
    PLUGGY.setInvestmentResources(
        ITEM,
        List.of(
            Map.of("resourceId", "r-1", "type", "VARIABLE_INCOME", "status", "AVAILABLE"),
            Map.of("resourceId", "r-2", "type", "VARIABLE_INCOME", "status", "AVAILABLE"),
            Map.of("resourceId", "r-3", "type", "ACCOUNT", "status", "AVAILABLE")));
    PLUGGY.addInvestment(
        ITEM, investment("inv-1", "FII Alfa", "AAAA11", "ACTIVE", "1000.00", "10"));
    PLUGGY.addInvestment(ITEM, investment("inv-2", "FII Beta", "BBBB11", "ACTIVE", "500.00", "5"));
    PLUGGY.addInvestment(
        ITEM, investment("inv-3", "FII Gama", "CCCC11", "TOTAL_WITHDRAWAL", "0", "0"));
    PLUGGY.addInvestmentTransaction(
        "inv-1", transaction("tx-buy-1", "BUY", "1000.00", "10", "2026-10-20", "Aplicação"));
    PLUGGY.addInvestmentTransaction(
        "inv-1",
        transaction("tx-interest-1", "INTEREST", "12.50", "0", "2026-10-25", "Rendimento"));
    PLUGGY.addInvestmentTransaction(
        "inv-2", transaction("tx-buy-2", "BUY", "500.00", "5", "2026-10-21", "Aplicação"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice.post("/api/v1/connections", Map.of("itemId", ITEM)).andExpect(status().isCreated());
  }

  @Test
  void importaPosicoesMovimentacoesECobertura() {
    assertThat(
            jdbcTemplate.queryForObject("select count(*) from provider_investment", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment where status = 'ACTIVE'", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment where status = 'CLOSED'", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment where unavailable_at is not null",
                Integer.class))
        .isZero();

    Map<String, Object> active =
        jdbcTemplate.queryForMap(
            "select id, user_id, name_enc, balance_enc from provider_investment where type = 'EQUITY'"
                + " and status = 'ACTIVE' order by name_enc limit 1");
    UUID investmentId = (UUID) active.get("id");
    UUID userId = (UUID) active.get("user_id");
    assertThat(
            decrypt(
                userId,
                "provider_investment",
                "name",
                investmentId,
                (byte[]) active.get("name_enc")))
        .isNotBlank();
    assertThat(
            decrypt(
                userId,
                "provider_investment",
                "balance",
                investmentId,
                (byte[]) active.get("balance_enc")))
        .isIn("1000.00", "500.00");

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment_transaction", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment_transaction where type = 'INTEREST'",
                Integer.class))
        .isEqualTo(1);
    Map<String, Object> interest =
        jdbcTemplate.queryForMap(
            "select id, user_id, description_enc, amount_enc from provider_investment_transaction"
                + " where type = 'INTEREST'");
    UUID transactionId = (UUID) interest.get("id");
    UUID transactionUserId = (UUID) interest.get("user_id");
    assertThat(
            decrypt(
                transactionUserId,
                "provider_investment_transaction",
                "description",
                transactionId,
                (byte[]) interest.get("description_enc")))
        .isEqualTo("Rendimento");
    assertThat(
            decrypt(
                transactionUserId,
                "provider_investment_transaction",
                "amount",
                transactionId,
                (byte[]) interest.get("amount_enc")))
        .isEqualTo("12.50");

    Map<String, Object> sync =
        jdbcTemplate.queryForMap(
            "select scope, resource_count, available_resource_count from provider_investment_sync");
    assertThat(sync.get("scope")).isEqualTo("AVAILABLE");
    assertThat(((Number) sync.get("resource_count")).intValue()).isEqualTo(2);
    assertThat(((Number) sync.get("available_resource_count")).intValue()).isEqualTo(2);
  }

  @Test
  void ressincronizacaoNaoDuplicaEMarcaPosicaoAusente() throws Exception {
    assertThat(countInvestments()).isEqualTo(3);

    PLUGGY.setInvestments(
        ITEM,
        List.of(
            investment("inv-1", "FII Alfa", "AAAA11", "ACTIVE", "1100.00", "10"),
            investment("inv-2", "FII Beta", "BBBB11", "ACTIVE", "500.00", "5")));
    syncNow();

    assertThat(countInvestments()).isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment where unavailable_at is not null",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment_transaction", Integer.class))
        .isEqualTo(3);

    PLUGGY.setInvestments(
        ITEM,
        List.of(
            investment("inv-1", "FII Alfa", "AAAA11", "ACTIVE", "1100.00", "10"),
            investment("inv-2", "FII Beta", "BBBB11", "ACTIVE", "500.00", "5"),
            investment("inv-3", "FII Gama", "CCCC11", "TOTAL_WITHDRAWAL", "0", "0")));
    syncNow();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment where unavailable_at is not null",
                Integer.class))
        .isZero();
  }

  @Test
  void itemSemRecursosNaoCriaPosicoesENaoTrataComoErro() throws Exception {
    PLUGGY.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "MeuPluggy", "UPDATED");
    PLUGGY.setInvestmentResources(ITEM, List.of());
    alice
        .post("/api/v1/connections/" + firstConnectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "invest-no-resources");

    assertThat(countInvestments()).isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment where unavailable_at is not null",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject("select scope from provider_investment_sync", String.class))
        .isEqualTo("NOT_SHARED");
  }

  @Test
  void recursoPendenteDeAutorizacaoNaoCriaPosicoes() throws Exception {
    PLUGGY.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "MeuPluggy", "UPDATED");
    PLUGGY.setInvestmentResources(
        ITEM,
        List.of(
            Map.of(
                "resourceId", "r-pending",
                "type", "VARIABLE_INCOME",
                "status", "PENDING_AUTHORISATION")));
    alice
        .post("/api/v1/connections/" + firstConnectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "invest-pending");

    assertThat(countInvestments()).isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from provider_investment where unavailable_at is not null",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject("select scope from provider_investment_sync", String.class))
        .isEqualTo("PENDING_AUTHORIZATION");
  }

  @Test
  void recursoNaoColetadoFicaUnknown() throws Exception {
    PLUGGY.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "MeuPluggy", "UPDATED");
    PLUGGY.setInvestmentResources(ITEM, List.of());
    PLUGGY.setResourcesCollectedAt(ITEM, null);
    alice
        .post("/api/v1/connections/" + firstConnectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "invest-unknown");

    assertThat(
            jdbcTemplate.queryForObject("select scope from provider_investment_sync", String.class))
        .isEqualTo("UNKNOWN");
  }

  @Test
  void falhaDeRecursosSemPosicoesFicaError() throws Exception {
    PLUGGY.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "MeuPluggy", "UPDATED");
    PLUGGY.failPath("/items/" + ITEM + "/resources", 500, 1);
    alice
        .post("/api/v1/connections/" + firstConnectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "invest-error");

    assertThat(
            jdbcTemplate.queryForObject("select scope from provider_investment_sync", String.class))
        .isEqualTo("ERROR");
  }

  @Test
  void falhaDeRecursosNaoInterrompeImportacaoDePosicoes() throws Exception {
    PLUGGY.failPath("/items/" + ITEM + "/resources", 500, 1);
    alice
        .post("/api/v1/connections/" + firstConnectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "invest-resource-failure");

    assertThat(countInvestments()).isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject("select scope from provider_investment_sync", String.class))
        .isEqualTo("AVAILABLE");
  }

  private int countInvestments() {
    return jdbcTemplate.queryForObject("select count(*) from provider_investment", Integer.class);
  }

  private void syncNow() throws Exception {
    alice
        .post("/api/v1/connections/" + firstConnectionId() + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "invest-sync");
  }

  private String firstConnectionId() throws Exception {
    JsonNode connections =
        json(
            alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return connections.path("items").get(0).path("id").asString();
  }

  private String decrypt(UUID userId, String table, String column, UUID recordId, byte[] value) {
    return cryptoService.decryptString(value, new CryptoContext(userId, table, column, recordId));
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }

  private static Map<String, Object> investment(
      String id, String name, String code, String status, String balance, String quantity) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", id);
    value.put("name", name);
    value.put("code", code);
    value.put("type", "EQUITY");
    value.put("subtype", "STOCK");
    value.put("status", status);
    value.put("currencyCode", "BRL");
    value.put("balance", balance);
    value.put("quantity", quantity);
    value.put("value", quantity.equals("0") ? "0" : "100.00");
    value.put("amount", balance);
    value.put("date", "2026-10-27");
    value.put("updatedAt", "2026-10-27T12:00:00Z");
    return value;
  }

  private static Map<String, Object> transaction(
      String id, String type, String amount, String quantity, String date, String description) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", id);
    value.put("type", type);
    value.put("movementType", "CREDIT");
    value.put("amount", amount);
    value.put("quantity", quantity);
    value.put("value", amount);
    value.put("date", date);
    value.put("tradeDate", date);
    value.put("description", description);
    value.put("expenses", Map.of());
    return value;
  }
}
