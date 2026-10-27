package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotência HTTP restrita às escritas financeiras críticas: replay, conflito de corpo,
 * isolamento por usuário e TTL.
 */
@AutoConfigureMockMvc
class IdempotencyIT extends PostgresTestBase {

  private static final String ALICE = "alice.idempotency";
  private static final String BOB = "bob.idempotency";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String HEADER = "Idempotency-Key";
  private static final String TRANSACTIONS = "/api/v1/transactions";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  private AuthTestClient alice;
  private AuthTestClient bob;
  private String aliceAccountId;
  private String bobAccountId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    aliceAccountId = createAccount(alice, "Carteira Alice");
    bobAccountId = createAccount(bob, "Carteira Bob");
  }

  @Test
  void mesmaChaveECorpoReplayAMesmaRespostaSemDuplicar() throws Exception {
    Map<String, Object> body = transactionBody(aliceAccountId, "Mercado");

    JsonNode first = createTransaction("idem-replay-01", body);
    JsonNode second = createTransaction("idem-replay-01", body);

    assertThat(second.path("id").asString()).isEqualTo(first.path("id").asString());
    assertThat(transactions(alice).path("items")).hasSize(1);
  }

  @Test
  void mesmaChaveComCorpoDiferenteRetorna409() throws Exception {
    createTransaction("idem-conflito-01", transactionBody(aliceAccountId, "Mercado"));

    alice
        .postWithHeader(
            TRANSACTIONS, transactionBody(aliceAccountId, "Farmácia"), HEADER, "idem-conflito-01")
        .andExpect(status().isConflict())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                .value("IDEMPOTENCY_KEY_REUSED"));
    assertThat(transactions(alice).path("items")).hasSize(1);
  }

  @Test
  void aMesmaChaveEmUsuariosDiferentesNaoColide() throws Exception {
    JsonNode aliceTransaction =
        createTransaction(
            alice, "idem-usuarios-01", transactionBody(aliceAccountId, "Mercado Alice"));
    JsonNode bobTransaction =
        createTransaction(bob, "idem-usuarios-01", transactionBody(bobAccountId, "Mercado Bob"));

    assertThat(aliceTransaction.path("id").asString())
        .isNotEqualTo(bobTransaction.path("id").asString());
    assertThat(transactions(alice).path("items")).hasSize(1);
    assertThat(transactions(bob).path("items")).hasSize(1);
  }

  @Test
  void novaChavePermiteCorrigirAposErroDeValidacao() throws Exception {
    Map<String, Object> invalid = transactionBody(aliceAccountId, " ");
    alice
        .postWithHeader(TRANSACTIONS, invalid, HEADER, "idem-retry-01")
        .andExpect(status().isUnprocessableEntity());

    createTransaction("idem-retry-02", transactionBody(aliceAccountId, "Mercado"));
    assertThat(transactions(alice).path("items")).hasSize(1);
  }

  @Test
  void resposta4xxEhReproduzidaComAMesmaChave() throws Exception {
    Map<String, Object> invalid = transactionBody(aliceAccountId, " ");

    String first =
        alice
            .postWithHeader(TRANSACTIONS, invalid, HEADER, "idem-422-01")
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        alice
            .postWithHeader(TRANSACTIONS, invalid, HEADER, "idem-422-01")
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(second).isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForObject(
                "select response_status from idempotency_key where user_id = ? and key = ?",
                Integer.class,
                userId(alice),
                "idem-422-01"))
        .isEqualTo(422);
  }

  @Test
  void reservaEmAndamentoRetorna409SemExecutarAMutacao() throws Exception {
    UUID userId = userId(alice);
    Map<String, Object> body = transactionBody(aliceAccountId, "Mercado em andamento");
    byte[] bodyBytes = jsonMapper.writeValueAsBytes(body);
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update("POST".getBytes(StandardCharsets.UTF_8));
    digest.update((byte) '\n');
    digest.update(TRANSACTIONS.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) '\n');
    digest.update(bodyBytes);

    Instant now = Instant.now();
    jdbcTemplate.update(
        "insert into idempotency_key"
            + " (id, user_id, key, method, path, request_hash, created_at, expires_at)"
            + " values (?, ?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        userId,
        "idem-em-andamento",
        "POST",
        TRANSACTIONS,
        digest.digest(),
        Timestamp.from(now),
        Timestamp.from(now.plusSeconds(3600)));

    alice
        .postWithHeader(TRANSACTIONS, body, HEADER, "idem-em-andamento")
        .andExpect(status().isConflict())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                .value("IDEMPOTENCY_IN_PROGRESS"));
    assertThat(transactions(alice).path("items")).hasSize(0);
  }

  @Test
  void chaveForaDoTamanhoRetorna422() throws Exception {
    alice
        .postWithHeader(TRANSACTIONS, transactionBody(aliceAccountId, "Mercado"), HEADER, "curta")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                .value("VALIDATION_FAILED"));
  }

  @Test
  void chaveExpiradaPodeSerReutilizada() throws Exception {
    Map<String, Object> body = transactionBody(aliceAccountId, "Mercado");
    createTransaction("idem-expira-01", body);
    jdbcTemplate.update(
        "update idempotency_key set expires_at = now() - interval '1 hour'"
            + " where user_id = ? and key = ?",
        userId(alice),
        "idem-expira-01");

    createTransaction("idem-expira-01", body);
    assertThat(transactions(alice).path("items")).hasSize(2);
  }

  @Test
  void patchEDeleteDeTransacaoSaoIdempotentes() throws Exception {
    JsonNode created =
        createTransaction("idem-create-01", transactionBody(aliceAccountId, "Mercado"));
    String transactionId = created.path("id").asString();
    Map<String, Object> correction = Map.of("description", "Mercado corrigido");

    JsonNode firstPatch = patchTransaction("idem-patch-01", transactionId, correction);
    JsonNode secondPatch = patchTransaction("idem-patch-01", transactionId, correction);

    assertThat(secondPatch.path("id").asString()).isEqualTo(firstPatch.path("id").asString());
    assertThat(secondPatch.path("description").asString()).isEqualTo("Mercado corrigido");
    assertThat(transactions(alice).path("items")).hasSize(1);

    alice
        .deleteWithHeader(TRANSACTIONS + "/" + transactionId, HEADER, "idem-delete-01")
        .andExpect(status().isNoContent());
    alice
        .deleteWithHeader(TRANSACTIONS + "/" + transactionId, HEADER, "idem-delete-01")
        .andExpect(status().isNoContent());

    assertThat(transactions(alice).path("items")).hasSize(0);
  }

  @Test
  void mutacoesNaoProtegidasIgnoramOHeader() throws Exception {
    String first = createAccount(alice, "Carteira nova");
    String second = createAccount(alice, "Carteira nova");

    assertThat(second).isNotEqualTo(first);
    assertThat(accounts(alice).path("items")).hasSize(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from idempotency_key where user_id = ? and key = ?",
                Integer.class,
                userId(alice),
                "idem-fora-01"))
        .isZero();
  }

  private JsonNode createTransaction(String key, Map<String, Object> body) throws Exception {
    return createTransaction(alice, key, body);
  }

  private JsonNode createTransaction(AuthTestClient client, String key, Map<String, Object> body)
      throws Exception {
    return json(
        client
            .postWithHeader(TRANSACTIONS, body, HEADER, key)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode patchTransaction(String key, String transactionId, Map<String, Object> body)
      throws Exception {
    return json(
        alice
            .patchWithHeader(TRANSACTIONS + "/" + transactionId, body, HEADER, key)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String createAccount(AuthTestClient client, String alias) throws Exception {
    return json(client
            .postWithHeader("/api/v1/accounts", accountBody(alias), HEADER, "idem-fora-01")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
  }

  private static Map<String, Object> transactionBody(String accountId, String description) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("accountId", accountId);
    body.put("type", "DEBIT");
    body.put("postedOn", "2026-10-22");
    body.put("amount", "25.00");
    body.put("description", description);
    return body;
  }

  private static Map<String, Object> accountBody(String alias) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "CASH");
    body.put("alias", alias);
    body.put("referenceBalance", "100.00");
    body.put("referenceDate", "2026-10-01T12:00:00Z");
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

  private UUID userId(AuthTestClient client) throws Exception {
    return UUID.fromString(
        json(client
                .get("/api/v1/auth/me")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("id")
            .asString());
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
