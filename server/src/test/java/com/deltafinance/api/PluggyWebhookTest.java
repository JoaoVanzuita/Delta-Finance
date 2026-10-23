package com.deltafinance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.application.WebhookService;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.integrations.infrastructure.PluggyProperties;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Webhook opcional: segredo, replay e mapeamento por itemId (T074). */
@AutoConfigureMockMvc
class PluggyWebhookTest extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.webhook";
  private static final String BOB = "bob.webhook";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String ALICE_CLIENT = "client-alice-webhook";
  private static final String BOB_CLIENT = "client-bob-webhook";
  private static final String WEBHOOK_TOKEN = "hook-token-alice-0001";
  private static final String WEBHOOK_SECRET = "segredo-webhook-muito-secreto";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("delta.pluggy.base-url", PLUGGY::baseUrl);
    registry.add("delta.pluggy.webhooks-enabled", () -> "true");
  }

  @AfterAll
  static void stopStub() {
    PLUGGY.stop();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private WebhookService webhookService;
  @Autowired private SyncJobService syncJobService;
  @Autowired private PluggyIntegrationRepository integrationRepository;
  @Autowired private PluggyProperties pluggyProperties;
  @Autowired private PluggyRateLimiter rateLimiter;

  private AuthTestClient alice;
  private AuthTestClient bob;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    pluggyProperties.setWebhooksEnabled(true);
    PLUGGY.registerClient(ALICE_CLIENT, "secret-alice");
    PLUGGY.registerClient(BOB_CLIENT, "secret-bob");
    PLUGGY.addItem("item-alice", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-alice", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-1", "acc-alice", "Padaria", "-20.00", "2026-10-20", "POSTED", null));
    PLUGGY.addItem("item-bob", "Banco Beta", "UPDATED");
    PLUGGY.addAccount(
        "item-bob",
        new AccountFixture("acc-bob", "BANK", "CHECKING_ACCOUNT", "Conta Beta", "500.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    configure(alice, ALICE_CLIENT, "secret-alice", "item-alice");
    configure(bob, BOB_CLIENT, "secret-bob", "item-bob");

    webhookService.register(
        userId(ALICE),
        integrationId(ALICE),
        WEBHOOK_TOKEN,
        WEBHOOK_SECRET,
        new String[] {"item/updated"});
  }

  @Test
  void eventoValidoResponde202ERegistraUmaVez() throws Exception {
    sendEvent(WEBHOOK_TOKEN, WEBHOOK_SECRET, "event-1", "item-alice")
        .andExpect(status().isAccepted());

    assertThat(count("select count(*) from webhook_event")).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from webhook_event where provider_event_id = 'event-1'",
                String.class))
        .isEqualTo("RECEIVED");
    assertThat(
            jdbcTemplate.queryForObject(
                "select connection_id from webhook_event where provider_event_id = 'event-1'",
                UUID.class))
        .isNotNull();
    assertThat(count("select count(*) from sync_job where job_type = 'PROCESS_WEBHOOK'"))
        .isEqualTo(1);
  }

  @Test
  void replayDoMesmoEventoRetorna409SemDuplicar() throws Exception {
    sendEvent(WEBHOOK_TOKEN, WEBHOOK_SECRET, "event-1", "item-alice")
        .andExpect(status().isAccepted());
    sendEvent(WEBHOOK_TOKEN, WEBHOOK_SECRET, "event-1", "item-alice")
        .andExpect(status().isConflict());

    assertThat(count("select count(*) from webhook_event")).isEqualTo(1);
    assertThat(count("select count(*) from sync_job where job_type = 'PROCESS_WEBHOOK'"))
        .isEqualTo(1);
  }

  @Test
  void segredoOuTokenInvalidosRetornam401() throws Exception {
    sendEvent(WEBHOOK_TOKEN, "segredo-errado", "event-2", "item-alice")
        .andExpect(status().isUnauthorized());
    sendEvent("token-inexistente", WEBHOOK_SECRET, "event-3", "item-alice")
        .andExpect(status().isUnauthorized());
    assertThat(count("select count(*) from webhook_event")).isZero();
  }

  @Test
  void itemDeOutroUsuarioNaoResolveConexaoEIgnoraEvento() throws Exception {
    sendEvent(WEBHOOK_TOKEN, WEBHOOK_SECRET, "event-bob", "item-bob")
        .andExpect(status().isAccepted());

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from webhook_event where provider_event_id = 'event-bob'",
                String.class))
        .isEqualTo("IGNORED");
    assertThat(count("select count(*) from sync_job where job_type = 'PROCESS_WEBHOOK'")).isZero();
  }

  @Test
  void processamentoAssincronoConsultaDadosCompletosEMarcaProcessado() throws Exception {
    sendEvent(WEBHOOK_TOKEN, WEBHOOK_SECRET, "event-1", "item-alice")
        .andExpect(status().isAccepted());
    PLUGGY.addTransaction(
        "acc-alice",
        new TransactionFixture(
            "tx-2", "acc-alice", "Mercado", "-30.00", "2026-10-21", "POSTED", null));

    assertThat(syncJobService.processNext("worker", "corr")).isPresent();

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from webhook_event where provider_event_id = 'event-1'",
                String.class))
        .isEqualTo("PROCESSED");
    String body = alice.get("/api/v1/transactions").andReturn().getResponse().getContentAsString();
    assertThat(jsonMapper.readTree(body).path("items")).hasSize(2);
  }

  @Test
  void featureFlagDesligadaNaoExpoeEndpoint() throws Exception {
    pluggyProperties.setWebhooksEnabled(false);
    try {
      sendEvent(WEBHOOK_TOKEN, WEBHOOK_SECRET, "event-4", "item-alice")
          .andExpect(status().isNotFound());
      assertThat(count("select count(*) from webhook_event")).isZero();
    } finally {
      pluggyProperties.setWebhooksEnabled(true);
    }
  }

  @Test
  void eventoSemEventIdRetorna422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/pluggy/{token}", WEBHOOK_TOKEN)
                .header("X-Webhook-Secret", WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"item/updated\",\"itemId\":\"item-alice\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  private org.springframework.test.web.servlet.ResultActions sendEvent(
      String token, String secret, String eventId, String itemId) throws Exception {
    String body =
        jsonMapper.writeValueAsString(
            Map.of("eventId", eventId, "event", "item/updated", "itemId", itemId));
    return mockMvc.perform(
        post("/api/v1/webhooks/pluggy/{token}", token)
            .header("X-Webhook-Secret", secret)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private void configure(AuthTestClient client, String clientId, String secret, String itemId)
      throws Exception {
    client
        .put("/api/v1/integrations/pluggy", Map.of("clientId", clientId, "clientSecret", secret))
        .andExpect(status().isOk());
    client.post("/api/v1/connections", Map.of("itemId", itemId)).andExpect(status().isCreated());
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }

  private UUID userId(String username) {
    return jdbcTemplate.queryForObject(
        "select id from app_user where username_normalized = ?", UUID.class, username);
  }

  private UUID integrationId(String username) {
    return integrationRepository.findMeuPluggy(userId(username)).orElseThrow().getId();
  }
}
