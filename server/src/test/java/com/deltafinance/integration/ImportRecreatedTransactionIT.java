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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

/**
 * Reconciliação de identidade (C1): quando o provedor remove e recria uma transação com novo id
 * externo, o registro local é mantido — sem duplicar a despesa, sem perder correções do usuário e
 * sem órfãos de vínculo.
 */
@AutoConfigureMockMvc
class ImportRecreatedTransactionIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.import.recreated";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-import-recreated";
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

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
  private String recentDate;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    recentDate = LocalDate.now(ZONE).minusDays(2).toString();
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
  }

  @Test
  void transacaoRecriadaComMesmoProviderIdAtualizaRegistroExistente() throws Exception {
    addItem("item-c1-pid", "acc-c1-pid");
    PLUGGY.addTransaction(
        "acc-c1-pid",
        new TransactionFixture(
            "tx-1",
            "acc-c1-pid",
            "Assinatura",
            "-30.00",
            recentDate,
            "POSTED",
            "Assinaturas",
            "prov-1",
            null));
    String connectionId = connect("item-c1-pid");
    String localId = findId("Assinatura");

    alice
        .patch("/api/v1/transactions/" + localId, Map.of("notes", "Minha assinatura"))
        .andExpect(status().isOk());

    // O provedor recria a transação: novo id externo, mesma âncora, descrição ajustada.
    PLUGGY.setTransactions(
        "acc-c1-pid",
        List.of(
            new TransactionFixture(
                "tx-2",
                "acc-c1-pid",
                "Assinatura Streaming",
                "-30.00",
                recentDate,
                "POSTED",
                "Assinaturas",
                "prov-1",
                null)));
    sync(connectionId);

    JsonNode items = transactions().path("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).path("id").asString()).isEqualTo(localId);
    assertThat(items.get(0).path("description").asString()).isEqualTo("Assinatura Streaming");
    assertThat(items.get(0).path("notes").asString()).isEqualTo("Minha assinatura");
  }

  @Test
  void providerCodeAncoraQuandoNaoHaProviderId() throws Exception {
    addItem("item-c1-code", "acc-c1-code");
    PLUGGY.addTransaction(
        "acc-c1-code",
        new TransactionFixture(
            "tx-1",
            "acc-c1-code",
            "Transferência recebida",
            "200.00",
            recentDate,
            "POSTED",
            "Outros",
            null,
            "code-9"));
    String connectionId = connect("item-c1-code");
    String localId = findId("Transferência recebida");

    PLUGGY.setTransactions(
        "acc-c1-code",
        List.of(
            new TransactionFixture(
                "tx-2",
                "acc-c1-code",
                "Transferência recebida",
                "200.00",
                recentDate,
                "POSTED",
                "Outros",
                null,
                "code-9")));
    sync(connectionId);

    JsonNode items = transactions().path("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).path("id").asString()).isEqualTo(localId);
  }

  @Test
  void transacaoRecriadaSemAncorasComAtributosIguaisPreservaCorrecoes() throws Exception {
    addItem("item-c1-fp", "acc-c1-fp");
    PLUGGY.addTransaction(
        "acc-c1-fp",
        new TransactionFixture(
            "tx-1", "acc-c1-fp", "Café", "-10.00", recentDate, "POSTED", "Alimentação"));
    String connectionId = connect("item-c1-fp");
    String localId = findId("Café");

    String cafeteria = createCategory("Cafés");
    alice
        .patch("/api/v1/transactions/" + localId, Map.of("categoryId", cafeteria))
        .andExpect(status().isOk());

    // Recriação sem providerId/providerCode e com atributos inalterados: o id externo antigo
    // deixou de estar na leitura, e o fingerprint adota o registro existente.
    PLUGGY.setTransactions(
        "acc-c1-fp",
        List.of(
            new TransactionFixture(
                "tx-2", "acc-c1-fp", "Café", "-10.00", recentDate, "POSTED", "Alimentação")));
    sync(connectionId);

    JsonNode items = transactions().path("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).path("id").asString()).isEqualTo(localId);
    assertThat(items.get(0).path("categoryId").asString()).isEqualTo(cafeteria);
    assertThat(items.get(0).path("editedFields").toString()).contains("category");
  }

  @Test
  void transacaoRecriadaAposAusenciaRestauraRegistroExistente() throws Exception {
    addItem("item-c1-gone", "acc-c1-gone");
    PLUGGY.addTransaction(
        "acc-c1-gone",
        new TransactionFixture(
            "tx-1",
            "acc-c1-gone",
            "Assinatura",
            "-30.00",
            recentDate,
            "POSTED",
            "Assinaturas",
            "prov-1",
            null));
    PLUGGY.addTransaction(
        "acc-c1-gone",
        new TransactionFixture(
            "tx-outro", "acc-c1-gone", "Outra", "-5.00", recentDate, "POSTED", "Outros"));
    String connectionId = connect("item-c1-gone");
    String localId = findId("Assinatura");

    // Leitura conclusiva sem a transação: marca SOURCE_REMOVED.
    PLUGGY.setTransactions(
        "acc-c1-gone",
        List.of(
            new TransactionFixture(
                "tx-outro", "acc-c1-gone", "Outra", "-5.00", recentDate, "POSTED", "Outros")));
    sync(connectionId);
    assertThat(find("Assinatura").path("status").asString()).isEqualTo("SOURCE_REMOVED");

    // A origem recria com novo id e mesma âncora: a mesma linha volta aos totais.
    PLUGGY.setTransactions(
        "acc-c1-gone",
        List.of(
            new TransactionFixture(
                "tx-2",
                "acc-c1-gone",
                "Assinatura",
                "-30.00",
                recentDate,
                "POSTED",
                "Assinaturas",
                "prov-1",
                null),
            new TransactionFixture(
                "tx-outro", "acc-c1-gone", "Outra", "-5.00", recentDate, "POSTED", "Outros")));
    sync(connectionId);

    JsonNode restored = find("Assinatura");
    assertThat(restored.path("id").asString()).isEqualTo(localId);
    assertThat(restored.path("status").asString()).isEqualTo("POSTED");
    assertThat(transactions().path("items")).hasSize(2);
  }

  private void addItem(String itemId, String accountId) {
    PLUGGY.addItem(itemId, "Banco C1", "UPDATED");
    PLUGGY.addAccount(
        itemId,
        new AccountFixture(accountId, "BANK", "CHECKING_ACCOUNT", "Conta C1", "1000.00", null));
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

  private void sync(String connectionId) throws Exception {
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "import-recreated");
  }

  private String createCategory(String name) throws Exception {
    String body =
        alice
            .post("/api/v1/categories", Map.of("name", name, "kind", "EXPENSE"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(body).path("id").asString();
  }

  private String findId(String description) throws Exception {
    return find(description).path("id").asString();
  }

  private JsonNode find(String description) throws Exception {
    for (JsonNode item : transactions().path("items")) {
      if (description.equals(item.path("description").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + description);
  }

  private JsonNode transactions() throws Exception {
    String body =
        alice
            .get("/api/v1/transactions?limit=100")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(body);
  }
}
