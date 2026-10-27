package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Reconciliação de contas após troca de itemId, sem apagar histórico nem duplicar saldo (C16). */
@AutoConfigureMockMvc
class AccountReconciliationIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.contas.reconcile";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-contas-reconcile";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
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
  private String connectionId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");

    PLUGGY.addItem("item-contas-old", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-contas-old",
        new AccountFixture(
            "acc-antiga", "BANK", "CHECKING_ACCOUNT", "Conta antiga", "300.00", null));
    PLUGGY.addAccount(
        "item-contas-old",
        new AccountFixture("acc-comum", "BANK", "CHECKING_ACCOUNT", "Conta comum", "100.00", null));
    PLUGGY.addTransaction(
        "acc-antiga",
        new TransactionFixture(
            "tx-antiga", "acc-antiga", "Compra antiga", "-30.00", "2026-10-20", "POSTED", null));

    PLUGGY.addItem("item-contas-new", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-contas-new",
        new AccountFixture("acc-comum", "BANK", "CHECKING_ACCOUNT", "Conta comum", "100.00", null));
    PLUGGY.addAccount(
        "item-contas-new",
        new AccountFixture("acc-nova", "BANK", "CHECKING_ACCOUNT", "Conta nova", "200.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    connectionId =
        json(alice
                .post("/api/v1/connections", Map.of("itemId", "item-contas-old"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("id")
            .asString();
  }

  @Test
  void contaAusenteNaNovaAutorizacaoFicaIndisponivelSemDuplicarSaldo() throws Exception {
    assertThat(accounts().path("items")).hasSize(2);

    alice
        .patch("/api/v1/connections/" + connectionId, Map.of("itemId", "item-contas-new"))
        .andExpect(status().isOk());

    JsonNode accounts = accounts().path("items");
    assertThat(accounts).hasSize(3);
    String oldId = accountIdByAlias(accounts, "Conta antiga");
    String sharedId = accountIdByAlias(accounts, "Conta comum");
    String newId = accountIdByAlias(accounts, "Conta nova");

    assertThat(accountStatus(accounts, oldId)).isEqualTo("UNAVAILABLE");
    assertThat(accountStatus(accounts, sharedId)).isEqualTo("ACTIVE");
    assertThat(accountStatus(accounts, newId)).isEqualTo("ACTIVE");

    JsonNode transactions = transactions();
    assertThat(transactions.path("items")).hasSize(1);
    assertThat(transactions.path("items").get(0).path("description").asString())
        .isEqualTo("Compra antiga");

    JsonNode summary =
        json(
            alice
                .get("/api/v1/dashboard/summary?from=2026-10-01&to=2026-10-31")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    // A conta antiga continua no histórico, mas não no saldo; a conta comum não duplica.
    assertThat(summary.path("currentBalance").asString()).isEqualTo("300.00");
  }

  private String accountIdByAlias(JsonNode accounts, String alias) {
    for (JsonNode account : accounts) {
      if (alias.equals(account.path("alias").asString())) {
        return account.path("id").asString();
      }
    }
    throw new AssertionError("Conta não encontrada: " + alias);
  }

  private String accountStatus(JsonNode accounts, String id) {
    for (JsonNode account : accounts) {
      if (id.equals(account.path("id").asString())) {
        return account.path("status").asString();
      }
    }
    throw new AssertionError("Conta não encontrada: " + id);
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

  private JsonNode transactions() throws Exception {
    return json(
        alice
            .get("/api/v1/transactions")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
