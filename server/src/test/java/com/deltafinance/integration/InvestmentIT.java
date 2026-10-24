package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Posições manuais de investimento (T148).
 *
 * <p>Cobre CRUD, derivação de preço/total, paginação por cursor, cifragem em repouso e isolamento
 * total entre usuários. Nenhuma importação automática é oferecida ou presumida.
 */
@AutoConfigureMockMvc
class InvestmentIT extends PostgresTestBase {

  private static final String ALICE = "alice.invest";
  private static final String BOB = "bob.invest";
  private static final String PASSWORD = "cavalo correto bateria grampo";

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
  void criaPosicoesComPrecoOuValorTotalEDerivaOsTotais() throws Exception {
    JsonNode byPrice =
        create(
            alice,
            request(
                "XP",
                "Petrobras PN",
                "PETR4",
                "10",
                "12.50",
                null,
                "2026-10-20",
                "Posição informada manualmente"));

    assertThat(byPrice.path("broker").asString()).isEqualTo("XP");
    assertThat(byPrice.path("assetName").asString()).isEqualTo("Petrobras PN");
    assertThat(byPrice.path("ticker").asString()).isEqualTo("PETR4");
    assertThat(byPrice.path("quantity").asString()).isEqualTo("10");
    assertThat(byPrice.path("referencePrice").asString()).isEqualTo("12.5");
    assertThat(byPrice.path("total").asString()).isEqualTo("125.00");
    assertThat(byPrice.path("referenceDate").asString()).isEqualTo("2026-10-20");
    assertThat(byPrice.path("origin").asString()).isEqualTo("MANUAL");

    JsonNode byTotal =
        create(
            alice,
            request(
                "SANTANDER",
                "Fundo imobiliário",
                "HGLG11",
                "3",
                null,
                "100.00",
                "2026-10-21",
                null));

    assertThat(byTotal.path("referencePrice").asString()).isEqualTo("33.33333333");
    assertThat(byTotal.path("total").asString()).isEqualTo("100.00");

    JsonNode listed = list(alice, null, 50);
    assertThat(listed.path("items")).hasSize(2);
    assertThat(listed.path("hasMore").asBoolean()).isFalse();

    java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
    for (JsonNode item : listed.path("items")) {
      sum = sum.add(new java.math.BigDecimal(item.path("total").asString()));
    }
    assertThat(sum.toPlainString()).isEqualTo("225.00");

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_event where event_type = 'INVESTMENT_POSITION_CREATED'",
                Long.class))
        .isEqualTo(2L);
  }

  @Test
  void posicoesSaoCifradasEmRepouso() throws Exception {
    JsonNode created =
        create(
            alice,
            request(
                "XP",
                "Tesouro Selic 2029",
                "TESOURO",
                "5",
                "150.25",
                null,
                "2026-10-22",
                "Observação sensível do titular"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            select asset_name_enc, ticker_enc, quantity_enc, reference_price_enc, notes_enc
              from investment_position
             where id = ?
            """,
            UUID.fromString(created.path("id").asString()));

    for (String column :
        List.of(
            "asset_name_enc", "ticker_enc", "quantity_enc", "reference_price_enc", "notes_enc")) {
      byte[] ciphertext = (byte[]) row.get(column);
      assertThat(ciphertext).as(column).isNotNull();
      String raw = new String(ciphertext, StandardCharsets.ISO_8859_1);
      assertThat(raw)
          .as(column)
          .doesNotContain("Tesouro Selic")
          .doesNotContain("TESOURO")
          .doesNotContain("150.25")
          .doesNotContain("Observação sensível");
    }
  }

  @Test
  void atualizaEExcluiPosicao() throws Exception {
    String id =
        create(alice, request("OTHER", "Fundo", "FUNDO", "2", "10.00", null, "2026-10-20", null))
            .path("id")
            .asString();

    JsonNode updated =
        json(
            alice
                .patch(
                    "/api/v1/investments/positions/" + id,
                    request(
                        "OTHER",
                        "Fundo atualizado",
                        "FUNDO",
                        "20",
                        "10.00",
                        null,
                        "2026-10-23",
                        "Corrigido"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(updated.path("assetName").asString()).isEqualTo("Fundo atualizado");
    assertThat(updated.path("quantity").asString()).isEqualTo("20");
    assertThat(updated.path("total").asString()).isEqualTo("200.00");
    assertThat(updated.path("referenceDate").asString()).isEqualTo("2026-10-23");

    alice.delete("/api/v1/investments/positions/" + id).andExpect(status().isNoContent());
    alice.get("/api/v1/investments/positions/" + id).andExpect(status().isNotFound());
    assertThat(list(alice, null, 50).path("items")).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_event where event_type = 'INVESTMENT_POSITION_DELETED'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  void isolaPosicoesEntreUsuarios() throws Exception {
    String aliceId =
        create(
                alice,
                request("XP", "Ações Alice", "ALICE3", "1", "50.00", null, "2026-10-20", null))
            .path("id")
            .asString();

    assertThat(list(bob, null, 50).path("items")).isEmpty();
    bob.get("/api/v1/investments/positions/" + aliceId).andExpect(status().isNotFound());
    bob.patch(
            "/api/v1/investments/positions/" + aliceId,
            request("XP", "Invasão", "BOB3", "1", "1.00", null, "2026-10-20", null))
        .andExpect(status().isNotFound());
    bob.delete("/api/v1/investments/positions/" + aliceId).andExpect(status().isNotFound());

    JsonNode aliceItems = list(alice, null, 50).path("items");
    assertThat(aliceItems).hasSize(1);
    assertThat(aliceItems.get(0).path("assetName").asString()).isEqualTo("Ações Alice");
  }

  @Test
  void validaCamposEObrigatoriedadeDePrecoOuValorTotal() throws Exception {
    alice
        .post(
            "/api/v1/investments/positions",
            request("XP", "Ativo", "ATV3", "0", "10.00", null, "2026-10-20", null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("quantity"));

    alice
        .post(
            "/api/v1/investments/positions",
            request("XP", "Ativo", "ATV3", "1", null, null, "2026-10-20", null))
        .andExpect(status().isUnprocessableEntity());

    alice
        .post(
            "/api/v1/investments/positions",
            request("XP", "Ativo", "ATV3", "1", "10.00", "10.00", "2026-10-20", null))
        .andExpect(status().isUnprocessableEntity());

    alice
        .post(
            "/api/v1/investments/positions",
            request("BROKER_INVALIDO", "Ativo", "ATV3", "1", "10.00", null, "2026-10-20", null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("broker"));

    alice
        .post(
            "/api/v1/investments/positions",
            request("XP", "   ", "ATV3", "1", "10.00", null, "2026-10-20", null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("assetName"));

    assertThat(list(alice, null, 50).path("items")).isEmpty();
  }

  @Test
  void paginaPorCursorNaOrdemMaisRecentePrimeiro() throws Exception {
    create(alice, request("XP", "Ativo 1", "A1", "1", "1.00", null, "2026-10-20", null));
    create(alice, request("XP", "Ativo 2", "A2", "1", "1.00", null, "2026-10-21", null));
    create(alice, request("XP", "Ativo 3", "A3", "1", "1.00", null, "2026-10-22", null));

    JsonNode first = list(alice, null, 2);
    assertThat(first.path("items")).hasSize(2);
    assertThat(first.path("items").get(0).path("assetName").asString()).isEqualTo("Ativo 3");
    assertThat(first.path("items").get(1).path("assetName").asString()).isEqualTo("Ativo 2");
    assertThat(first.path("hasMore").asBoolean()).isTrue();
    assertThat(first.path("nextCursor").asString()).isNotBlank();

    JsonNode second = list(alice, first.path("nextCursor").asString(), 2);
    assertThat(second.path("items")).hasSize(1);
    assertThat(second.path("items").get(0).path("assetName").asString()).isEqualTo("Ativo 1");
    assertThat(second.path("hasMore").asBoolean()).isFalse();

    alice
        .get("/api/v1/investments/positions?cursor=invalido")
        .andExpect(status().isUnprocessableEntity());
  }

  private JsonNode create(AuthTestClient client, Map<String, Object> request) throws Exception {
    return json(
        client
            .post("/api/v1/investments/positions", request)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode list(AuthTestClient client, String cursor, int limit) throws Exception {
    String query = "/api/v1/investments/positions?limit=" + limit;
    if (cursor != null) {
      query += "&cursor=" + java.net.URLEncoder.encode(cursor, StandardCharsets.UTF_8);
    }
    return json(
        client
            .get(query)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static Map<String, Object> request(
      String broker,
      String assetName,
      String ticker,
      String quantity,
      String referencePrice,
      String totalValue,
      String referenceDate,
      String notes) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("broker", broker);
    body.put("assetName", assetName);
    body.put("ticker", ticker);
    body.put("quantity", quantity);
    if (referencePrice != null) {
      body.put("referencePrice", referencePrice);
    }
    if (totalValue != null) {
      body.put("totalValue", totalValue);
    }
    body.put("referenceDate", referenceDate);
    if (notes != null) {
      body.put("notes", notes);
    }
    return body;
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
