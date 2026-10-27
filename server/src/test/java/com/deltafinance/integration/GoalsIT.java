package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Metas financeiras (T163).
 *
 * <p>Cobre progresso por alocação, percentuais e resíduo, saldo não alocado, excesso, redução de
 * dívida, patrimônio líquido, estados, histórico, fontes e ausência de movimentação de dinheiro.
 */
@AutoConfigureMockMvc
class GoalsIT extends PostgresTestBase {

  private static final String ALICE = "alice.metas";
  private static final String BOB = "bob.metas";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  private AuthTestClient alice;
  private AuthTestClient bob;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
  }

  @Test
  void aplicaResiduoNaUltimaAlocacaoPercentualELimitaProgresso() throws Exception {
    JsonNode goal =
        createGoal(
            alice,
            goalBody(
                "Reserva",
                "EMERGENCY_FUND",
                "100.00",
                "ALLOCATIONS",
                null,
                List.of(percentage("33.33"), percentage("33.33"), percentage("33.34"))));

    assertThat(goal.path("currentValue").asString()).isEqualTo("100.00");
    assertThat(goal.path("percentage").asString()).isEqualTo("100.00");
    assertThat(goal.path("remainingAmount").asString()).isEqualTo("0.00");
    assertThat(goal.path("unallocatedAmount").asString()).isEqualTo("0.00");
    assertThat(goal.path("excessAmount").asString()).isEqualTo("0.00");
    assertThat(goal.path("status").asString()).isEqualTo("ACHIEVED");
    assertThat(goal.path("allocations")).hasSize(3);

    List<String> contributions = new ArrayList<>();
    for (JsonNode allocation : goal.path("allocations")) {
      contributions.add(allocation.path("contributionAmount").asString());
    }
    assertThat(contributions).containsExactly("33.33", "33.33", "33.34");
    assertThat(goal.path("progressHistory")).hasSize(1);
    assertThat(goal.path("progressHistory").get(0).path("referenceDate").asString())
        .isEqualTo(today().toString());
  }

  @Test
  void percentuaisAbaixoDeCemMantemSaldoNaoAlocadoSemRedistribuir() throws Exception {
    JsonNode goal =
        createGoal(
            alice,
            goalBody(
                "Viagem",
                "TRAVEL",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(percentage("30.00"), percentage("20.00"))));

    assertThat(goal.path("currentValue").asString()).isEqualTo("500.00");
    assertThat(goal.path("percentage").asString()).isEqualTo("50.00");
    assertThat(goal.path("remainingAmount").asString()).isEqualTo("500.00");
    assertThat(goal.path("unallocatedAmount").asString()).isEqualTo("500.00");
    assertThat(goal.path("excessAmount").asString()).isEqualTo("0.00");
    assertThat(goal.path("status").asString()).isEqualTo("ACTIVE");
  }

  @Test
  void valorFixoPodeExcederMetaComProgressoLimitado() throws Exception {
    JsonNode goal =
        createGoal(
            alice,
            goalBody(
                "Compra planejada",
                "PLANNED_PURCHASE",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(fixed("MANUAL", null, "1200.00"))));

    assertThat(goal.path("currentValue").asString()).isEqualTo("1200.00");
    assertThat(goal.path("percentage").asString()).isEqualTo("100.00");
    assertThat(goal.path("remainingAmount").asString()).isEqualTo("0.00");
    assertThat(goal.path("unallocatedAmount").asString()).isEqualTo("0.00");
    assertThat(goal.path("excessAmount").asString()).isEqualTo("200.00");
    assertThat(goal.path("status").asString()).isEqualTo("ACHIEVED");
  }

  @Test
  void metaDePatrimonioLiquidoRecalculaComOsDadosAtuais() throws Exception {
    createManualAsset("Reserva", "1500.00");

    JsonNode goal =
        createGoal(
            alice, goalBody("Patrimônio", "NET_WORTH", "1000.00", "NET_WORTH", null, List.of()));

    assertThat(goal.path("currentValue").asString()).isEqualTo("1500.00");
    assertThat(goal.path("percentage").asString()).isEqualTo("100.00");
    assertThat(goal.path("status").asString()).isEqualTo("ACHIEVED");

    createManualAsset("Reserva extra", "500.00");
    JsonNode refreshed = getGoal(alice, goal.path("id").asString());
    assertThat(refreshed.path("currentValue").asString()).isEqualTo("2000.00");
    assertThat(refreshed.path("percentage").asString()).isEqualTo("100.00");

    alice
        .post(
            "/api/v1/goals",
            goalBody(
                "Patrimônio com alocação",
                "NET_WORTH",
                "1000.00",
                "NET_WORTH",
                null,
                List.of(fixed("MANUAL", null, "100.00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("allocations"));
  }

  @Test
  void aplicaEstadosEHistoricoDeAlteracoes() throws Exception {
    String id =
        createGoal(
                alice,
                goalBody(
                    "Reserva",
                    "EMERGENCY_FUND",
                    "1000.00",
                    "ALLOCATIONS",
                    today().plusDays(10).toString(),
                    List.of(fixed("MANUAL", null, "500.00"))))
            .path("id")
            .asString();

    JsonNode changed =
        patchGoal(
            alice,
            id,
            goalBody(
                "Reserva",
                "EMERGENCY_FUND",
                "2000.00",
                "ALLOCATIONS",
                today().plusDays(10).toString(),
                List.of(fixed("MANUAL", null, "500.00"))));
    assertThat(changed.path("status").asString()).isEqualTo("CHANGED");
    assertThat(changed.path("changedAt").isNull()).isFalse();

    JsonNode acknowledged = setStatus(alice, id, "ACTIVE");
    assertThat(acknowledged.path("status").asString()).isEqualTo("ACTIVE");
    assertThat(acknowledged.path("changedAt").isNull()).isTrue();

    patchGoal(
        alice,
        id,
        goalBody(
            "Reserva",
            "EMERGENCY_FUND",
            "2000.00",
            "ALLOCATIONS",
            today().minusDays(1).toString(),
            List.of(fixed("MANUAL", null, "500.00"))));
    setStatus(alice, id, "ACTIVE");
    assertThat(getGoal(alice, id).path("status").asString()).isEqualTo("OVERDUE");

    JsonNode paused = setStatus(alice, id, "PAUSED");
    assertThat(paused.path("status").asString()).isEqualTo("PAUSED");
    assertThat(paused.path("currentValue").asString()).isEqualTo("500.00");

    patchGoal(
        alice,
        id,
        goalBody(
            "Reserva",
            "EMERGENCY_FUND",
            "2000.00",
            "ALLOCATIONS",
            today().minusDays(1).toString(),
            List.of(fixed("MANUAL", null, "900.00"))));
    JsonNode frozen = getGoal(alice, id);
    assertThat(frozen.path("status").asString()).isEqualTo("PAUSED");
    assertThat(frozen.path("currentValue").asString()).isEqualTo("500.00");

    JsonNode reactivated = setStatus(alice, id, "ACTIVE");
    assertThat(reactivated.path("currentValue").asString()).isEqualTo("900.00");
    assertThat(reactivated.path("percentage").asString()).isEqualTo("45.00");

    assertThat(setStatus(alice, id, "ARCHIVED").path("status").asString()).isEqualTo("ARCHIVED");

    JsonNode achieved =
        createGoal(
            alice,
            goalBody(
                "Concluída",
                "TRAVEL",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(fixed("MANUAL", null, "1000.00"))));
    assertThat(achieved.path("status").asString()).isEqualTo("ACHIEVED");
    assertThat(achieved.path("progressHistory")).hasSize(1);
    assertThat(achieved.path("progressHistory").get(0).path("currentValue").asString())
        .isEqualTo("1000.00");
  }

  @Test
  void validaFontesEPercentuaisEntreMetas() throws Exception {
    String accountId = createManualAccount("Conta reserva", "1000.00");

    createGoal(
        alice,
        goalBody(
            "Meta A",
            "EMERGENCY_FUND",
            "1000.00",
            "ALLOCATIONS",
            null,
            List.of(fixed("ACCOUNT", accountId, "800.00"))));

    alice
        .post(
            "/api/v1/goals",
            goalBody(
                "Meta B",
                "TRAVEL",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(fixed("ACCOUNT", accountId, "300.00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("allocations"));

    createGoal(
        alice,
        goalBody(
            "Meta B",
            "TRAVEL",
            "1000.00",
            "ALLOCATIONS",
            null,
            List.of(fixed("ACCOUNT", accountId, "200.00"))));

    alice
        .post(
            "/api/v1/goals",
            goalBody(
                "Meta C",
                "PLANNED_PURCHASE",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(percentageSource("ACCOUNT", accountId, "50.00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("allocations"));

    alice
        .post(
            "/api/v1/goals",
            goalBody(
                "Percentual inválido",
                "TRAVEL",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(percentage("60.00"), percentage("50.00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("allocations"));

    alice
        .post(
            "/api/v1/goals",
            goalBody(
                "Modo misto",
                "TRAVEL",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(mixed("100.00", "10.00"))))
        .andExpect(status().isUnprocessableEntity());

    alice
        .post(
            "/api/v1/goals",
            goalBody(
                "Fonte ausente",
                "TRAVEL",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(fixed("ACCOUNT", null, "100.00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("allocations"));

    alice
        .post(
            "/api/v1/goals",
            goalBody(
                "Manual com fonte",
                "TRAVEL",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(fixed("MANUAL", accountId, "100.00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("allocations"));
  }

  @Test
  void isolaMetasEntreUsuarios() throws Exception {
    String id =
        createGoal(
                alice,
                goalBody(
                    "Meta Alice",
                    "TRAVEL",
                    "1000.00",
                    "ALLOCATIONS",
                    null,
                    List.of(fixed("MANUAL", null, "100.00"))))
            .path("id")
            .asString();

    assertThat(
            json(bob.get("/api/v1/goals")
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
                .path("items"))
        .isEmpty();
    bob.get("/api/v1/goals/" + id).andExpect(status().isNotFound());
    bob.patch(
            "/api/v1/goals/" + id,
            goalBody("Invasão", "TRAVEL", "1.00", "ALLOCATIONS", null, List.of()))
        .andExpect(status().isNotFound());
    bob.post("/api/v1/goals/" + id + "/status", Map.of("status", "PAUSED"))
        .andExpect(status().isNotFound());
    bob.delete("/api/v1/goals/" + id).andExpect(status().isNotFound());

    assertThat(listGoals(alice).path("items")).hasSize(1);
  }

  @Test
  void metasNaoMovimentamDinheiroNemCriamOrdens() throws Exception {
    int transactionsBefore = count("select count(*) from transaction");
    BigDecimal balanceBefore =
        new BigDecimal(
            jdbcTemplate
                .queryForObject("select count(*) from financial_account", Integer.class)
                .toString());

    String id =
        createGoal(
                alice,
                goalBody(
                    "Meta sem dinheiro",
                    "TRAVEL",
                    "1000.00",
                    "ALLOCATIONS",
                    null,
                    List.of(fixed("MANUAL", null, "100.00"))))
            .path("id")
            .asString();
    patchGoal(
        alice,
        id,
        goalBody(
            "Meta sem dinheiro",
            "TRAVEL",
            "2000.00",
            "ALLOCATIONS",
            null,
            List.of(fixed("MANUAL", null, "500.00"))));
    setStatus(alice, id, "PAUSED");

    assertThat(count("select count(*) from transaction")).isEqualTo(transactionsBefore);
    assertThat(
            new BigDecimal(
                jdbcTemplate
                    .queryForObject("select count(*) from financial_account", Integer.class)
                    .toString()))
        .isEqualByComparingTo(balanceBefore);
    assertThat(tableExists("orders")).isFalse();
  }

  @Test
  void validaCamposObrigatorios() throws Exception {
    alice
        .post("/api/v1/goals", goalBody(" ", "TRAVEL", "1000.00", "ALLOCATIONS", null, List.of()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    alice
        .post("/api/v1/goals", goalBody("X", "INVALID", "1000.00", "ALLOCATIONS", null, List.of()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("type"));
    alice
        .post("/api/v1/goals", goalBody("X", "TRAVEL", "0.00", "ALLOCATIONS", null, List.of()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("targetAmount"));
    alice
        .post("/api/v1/goals", goalBody("X", "TRAVEL", "1000.00", "INVALID", null, List.of()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("progressRule"));
  }

  @Test
  void valorFixoAcimaDaCapacidadeNaoEhTruncadoSilenciosamente() throws Exception {
    String accountId = createManualAccount("Conta em queda", "1000.00");
    JsonNode goal =
        createGoal(
            alice,
            goalBody(
                "Reserva",
                "EMERGENCY_FUND",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(fixed("ACCOUNT", accountId, "800.00"))));
    assertThat(goal.path("currentValue").asString()).isEqualTo("800.00");

    alice
        .patch("/api/v1/accounts/" + accountId, Map.of("referenceBalance", "500.00"))
        .andExpect(status().isOk());

    JsonNode refreshed = getGoal(alice, goal.path("id").asString());
    assertThat(refreshed.path("currentValue").asString()).isEqualTo("800.00");
    assertThat(refreshed.path("percentage").asString()).isEqualTo("80.00");
    JsonNode allocation = refreshed.path("allocations").get(0);
    assertThat(allocation.path("contributionAmount").asString()).isEqualTo("800.00");
    assertThat(allocation.path("capacityExceeded").asBoolean()).isTrue();
  }

  @Test
  void multiplasMetasNaoContamOMesmoSaldoIntegralmente() throws Exception {
    String accountId = createManualAccount("Conta compartilhada", "1000.00");
    String firstId =
        createGoal(
                alice,
                goalBody(
                    "Meta A",
                    "EMERGENCY_FUND",
                    "1000.00",
                    "ALLOCATIONS",
                    null,
                    List.of(percentageSource("ACCOUNT", accountId, "50.00"))))
            .path("id")
            .asString();
    String secondId =
        createGoal(
                alice,
                goalBody(
                    "Meta B",
                    "TRAVEL",
                    "1000.00",
                    "ALLOCATIONS",
                    null,
                    List.of(percentageSource("ACCOUNT", accountId, "50.00"))))
            .path("id")
            .asString();

    alice
        .patch("/api/v1/accounts/" + accountId, Map.of("referenceBalance", "600.00"))
        .andExpect(status().isOk());

    JsonNode first = getGoal(alice, firstId);
    JsonNode second = getGoal(alice, secondId);
    BigDecimal total =
        new BigDecimal(first.path("currentValue").asString())
            .add(new BigDecimal(second.path("currentValue").asString()));
    assertThat(total).isEqualByComparingTo("600.00");
    assertThat(first.path("allocations").get(0).path("capacityExceeded").asBoolean())
        .isNotEqualTo(second.path("allocations").get(0).path("capacityExceeded").asBoolean());
  }

  @Test
  void recusaFonteEmMoedaDiferenteDeBrl() throws Exception {
    String accountId = createManualAccount("Conta USD", "100.00", "USD");

    alice
        .post(
            "/api/v1/goals",
            goalBody(
                "Meta em dólar",
                "TRAVEL",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(fixed("ACCOUNT", accountId, "50.00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("allocations"));
  }

  @Test
  void persisteObservacoesEExpoeNomeDaFonte() throws Exception {
    String accountId = createManualAccount("Conta reserva", "1000.00");
    Map<String, Object> body =
        goalBody(
            "Reserva 2027",
            "EMERGENCY_FUND",
            "1000.00",
            "ALLOCATIONS",
            null,
            List.of(fixed("ACCOUNT", accountId, "500.00")));
    body.put("notes", "Guardar para a viagem de 2027.");

    JsonNode created = createGoal(alice, body);
    assertThat(created.path("notes").asString()).isEqualTo("Guardar para a viagem de 2027.");
    assertThat(created.path("allocations").get(0).path("sourceName").asString())
        .isEqualTo("Conta reserva");

    String id = created.path("id").asString();
    assertThat(getGoal(alice, id).path("notes").asString())
        .isEqualTo("Guardar para a viagem de 2027.");
    assertThat(listGoals(alice).path("items").get(0).path("notes").asString())
        .isEqualTo("Guardar para a viagem de 2027.");

    Map<String, Object> noNotes =
        goalBody(
            "Reserva 2027",
            "EMERGENCY_FUND",
            "1000.00",
            "ALLOCATIONS",
            null,
            List.of(fixed("ACCOUNT", accountId, "500.00")));
    JsonNode cleared = patchGoal(alice, id, noNotes);
    assertThat(cleared.path("notes").isNull()).isTrue();
    assertThat(cleared.path("status").asString()).isEqualTo("ACTIVE");
  }

  @Test
  void alteracaoDeAlocacaoMarcaChanged() throws Exception {
    String id =
        createGoal(
                alice,
                goalBody(
                    "Reserva",
                    "EMERGENCY_FUND",
                    "1000.00",
                    "ALLOCATIONS",
                    null,
                    List.of(fixed("MANUAL", null, "500.00"))))
            .path("id")
            .asString();

    JsonNode changed =
        patchGoal(
            alice,
            id,
            goalBody(
                "Reserva",
                "EMERGENCY_FUND",
                "1000.00",
                "ALLOCATIONS",
                null,
                List.of(fixed("MANUAL", null, "600.00"))));
    assertThat(changed.path("status").asString()).isEqualTo("CHANGED");
    assertThat(changed.path("changedAt").isNull()).isFalse();
    assertThat(setStatus(alice, id, "ACTIVE").path("status").asString()).isEqualTo("ACTIVE");
  }

  private JsonNode createGoal(AuthTestClient client, Map<String, Object> body) throws Exception {
    return json(
        client
            .post("/api/v1/goals", body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode patchGoal(AuthTestClient client, String id, Map<String, Object> body)
      throws Exception {
    return json(
        client
            .patch("/api/v1/goals/" + id, body)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode getGoal(AuthTestClient client, String id) throws Exception {
    return json(
        client
            .get("/api/v1/goals/" + id)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode setStatus(AuthTestClient client, String id, String statusValue)
      throws Exception {
    return json(
        client
            .post("/api/v1/goals/" + id + "/status", Map.of("status", statusValue))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode listGoals(AuthTestClient client) throws Exception {
    return json(
        client
            .get("/api/v1/goals")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String createManualAccount(String alias, String balance) throws Exception {
    return createManualAccount(alias, balance, "BRL");
  }

  private String createManualAccount(String alias, String balance, String currency)
      throws Exception {
    return json(alice
            .post(
                "/api/v1/accounts",
                Map.of(
                    "type", "BANK",
                    "alias", alias,
                    "referenceBalance", balance,
                    "referenceDate", "2026-10-24T12:00:00Z",
                    "currency", currency))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
  }

  private void createManualAsset(String description, String value) throws Exception {
    alice
        .post(
            "/api/v1/wealth/manual-items",
            Map.of(
                "kind",
                "ASSET",
                "type",
                "INVESTMENT",
                "description",
                description,
                "referenceValue",
                value,
                "referenceDate",
                today().toString()))
        .andExpect(status().isCreated());
  }

  private int count(String sql) {
    return jdbcTemplate.queryForObject(sql, Integer.class);
  }

  private static Map<String, Object> goalBody(
      String name,
      String type,
      String target,
      String rule,
      String targetDate,
      List<Map<String, Object>> allocations) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("type", type);
    body.put("targetAmount", target);
    body.put("progressRule", rule);
    if (targetDate != null) {
      body.put("targetDate", targetDate);
    }
    if (allocations != null && !allocations.isEmpty()) {
      body.put("allocations", allocations);
    }
    return body;
  }

  private static Map<String, Object> fixed(String sourceType, String sourceId, String amount) {
    Map<String, Object> allocation = new LinkedHashMap<>();
    allocation.put("sourceType", sourceType);
    if (sourceId != null) {
      allocation.put("sourceId", sourceId);
    }
    allocation.put("allocationMode", "FIXED_AMOUNT");
    allocation.put("allocatedAmount", amount);
    return allocation;
  }

  private static Map<String, Object> percentage(String value) {
    return percentageSource("MANUAL", null, value);
  }

  private static Map<String, Object> percentageSource(
      String sourceType, String sourceId, String value) {
    Map<String, Object> allocation = new LinkedHashMap<>();
    allocation.put("sourceType", sourceType);
    if (sourceId != null) {
      allocation.put("sourceId", sourceId);
    }
    allocation.put("allocationMode", "PERCENTAGE");
    allocation.put("percentage", value);
    return allocation;
  }

  private static Map<String, Object> mixed(String amount, String percentage) {
    Map<String, Object> allocation = new LinkedHashMap<>();
    allocation.put("sourceType", "MANUAL");
    allocation.put("allocationMode", "PERCENTAGE");
    allocation.put("allocatedAmount", amount);
    allocation.put("percentage", percentage);
    return allocation;
  }

  private static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
