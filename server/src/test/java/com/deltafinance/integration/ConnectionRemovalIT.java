package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PostgresTestBase;
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
 * Remoção de conexão duplicada.
 *
 * <p>Duas autorizações distintas podem expor a mesma conta bancária. A remoção padrão desativa a
 * conexão e tira suas contas dos saldos correntes; a remoção explícita apaga as contas e transações
 * importadas por ela, sem tocar nas demais conexões, contas manuais ou categorias.
 */
@AutoConfigureMockMvc
class ConnectionRemovalIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.remocao";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-remocao";

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
  private UUID aliceId;
  private String firstConnectionId;
  private String secondConnectionId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-remocao-a", "Banco Alfa", "UPDATED");
    PLUGGY.addItem("item-remocao-b", "Banco Alfa", "UPDATED");
    // A mesma conta aparece nas duas autorizações, reproduzindo o saldo duplicado.
    PLUGGY.addAccount(
        "item-remocao-a",
        new AccountFixture(
            "acc-remocao", "BANK", "CHECKING_ACCOUNT", "Conta Dup", "1000.00", null));
    PLUGGY.addAccount(
        "item-remocao-b",
        new AccountFixture(
            "acc-remocao", "BANK", "CHECKING_ACCOUNT", "Conta Dup", "1000.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    aliceId =
        UUID.fromString(
            json(alice
                    .get("/api/v1/auth/me")
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
                .path("id")
                .asString());
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    firstConnectionId = addConnection("item-remocao-a");
    secondConnectionId = addConnection("item-remocao-b");
  }

  @Test
  void desativarConexaoTiraAsContasDosSaldosEPreservaHistorico() throws Exception {
    assertThat(accountCount()).isEqualTo(2);
    assertThat(currentBalance()).isEqualTo("2000.00");

    alice.delete("/api/v1/connections/" + firstConnectionId).andExpect(status().isNoContent());

    assertThat(connectionStatus(firstConnectionId)).isEqualTo("ITEM_UNAVAILABLE");
    assertThat(accountStatus(firstConnectionId)).isEqualTo("UNAVAILABLE");
    assertThat(accountStatus(secondConnectionId)).isEqualTo("ACTIVE");
    assertThat(accountCount()).isEqualTo(2);
    assertThat(currentBalance()).isEqualTo("1000.00");
  }

  @Test
  void excluirConexaoApagaSomenteOsDadosImportadosPorEla() throws Exception {
    alice
        .delete("/api/v1/connections/" + firstConnectionId + "?deleteHistory=true")
        .andExpect(status().isNoContent());

    assertThat(connectionStatus(firstConnectionId)).isNull();
    assertThat(accountCount()).isEqualTo(1);
    assertThat(accountCountByConnection(firstConnectionId)).isZero();
    assertThat(accountCountByConnection(secondConnectionId)).isEqualTo(1);
    assertThat(currentBalance()).isEqualTo("1000.00");

    JsonNode connections =
        json(
            alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(connections.path("items")).hasSize(1);
  }

  private String addConnection(String itemId) throws Exception {
    return json(alice
            .post("/api/v1/connections", Map.of("itemId", itemId))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
  }

  private String connectionStatus(String connectionId) {
    var statuses =
        jdbcTemplate.queryForList(
            "select status from pluggy_connection where id = ? and user_id = ?",
            String.class,
            UUID.fromString(connectionId),
            aliceId);
    return statuses.isEmpty() ? null : statuses.getFirst();
  }

  private String accountStatus(String connectionId) {
    return jdbcTemplate.queryForObject(
        "select status from financial_account where connection_id = ? and user_id = ?",
        String.class,
        UUID.fromString(connectionId),
        aliceId);
  }

  private int accountCount() {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_account where user_id = ?", Integer.class, aliceId);
    return count == null ? 0 : count;
  }

  private int accountCountByConnection(String connectionId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from financial_account where user_id = ? and connection_id = ?",
            Integer.class,
            aliceId,
            UUID.fromString(connectionId));
    return count == null ? 0 : count;
  }

  private String currentBalance() throws Exception {
    return json(alice
            .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("currentBalance")
        .asString();
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
