package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Isolamento entre dois usuários (FR-004 a FR-007): leitura, escrita e exclusão de identidade não
 * podem atravessar o limite do usuário, mesmo com IDs, query strings ou headers manipulados.
 */
@AutoConfigureMockMvc
class IsolationIT extends PostgresTestBase {

  private static final String ALICE = "alice.familia";
  private static final String BOB = "bob.familia";
  private static final String ALICE_PASSWORD = "cavalo correto bateria grampo";
  private static final String BOB_PASSWORD = "janela azul montanha serena";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private AuthTestClient alice;
  private AuthTestClient bob;
  private UUID aliceId;
  private UUID bobId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();

    alice.register(ALICE, ALICE_PASSWORD).login(ALICE, ALICE_PASSWORD);
    bob.register(BOB, BOB_PASSWORD).login(BOB, BOB_PASSWORD);

    aliceId = userId(ALICE);
    bobId = userId(BOB);
  }

  @Test
  void meRetornaApenasOProprioUsuarioMesmoComIdsManipulados() throws Exception {
    alice
        .get("/api/v1/auth/me")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(ALICE));
    bob.get("/api/v1/auth/me")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(BOB));

    mockMvc
        .perform(
            alice
                .withCookies(request(HttpMethod.GET, "/api/v1/auth/me"))
                .param("userId", bobId.toString())
                .header("X-User-Id", bobId.toString())
                .header("X-User", BOB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(aliceId.toString()))
        .andExpect(jsonPath("$.username").value(ALICE))
        .andExpect(
            content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(BOB))));
  }

  @Test
  void escritaComUserIdDeOutroUsuarioNaoAlteraAOutraConta() throws Exception {
    String novaSenhaAlice = "montanha verde silencio longo";
    alice
        .put(
            "/api/v1/auth/password",
            Map.of(
                "currentPassword",
                ALICE_PASSWORD,
                "newPassword",
                novaSenhaAlice,
                "userId",
                bobId.toString()))
        .andExpect(status().isNoContent());

    // Bob continua com a senha e a sessão originais.
    bob.get("/api/v1/auth/me")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(bobId.toString()));
    AuthTestClient bobNovo = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bobNovo.login(BOB, BOB_PASSWORD);

    // Alice usa a nova senha.
    AuthTestClient aliceNovo = new AuthTestClient(mockMvc, jsonMapper).csrf();
    aliceNovo.login(ALICE, novaSenhaAlice);
  }

  @Test
  void logoutDeUmUsuarioNaoEncerraSessaoDoOutro() throws Exception {
    alice.logout();

    alice.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
    bob.get("/api/v1/auth/me")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(bobId.toString()));
  }

  @Test
  void exclusaoDeIdentidadeNaoEhPermitidaENaoAfetaAConta() throws Exception {
    alice.delete("/api/v1/auth/me").andExpect(status().isMethodNotAllowed());
    alice.get("/api/v1/auth/me").andExpect(status().isOk());

    bob.delete("/api/v1/users/" + aliceId).andExpect(status().isNotFound());
    alice.get("/api/v1/auth/me").andExpect(status().isOk());
  }

  @Test
  void rotaDeIdentidadePorIdDeOutroUsuarioNaoRevelaDados() throws Exception {
    alice
        .get("/api/v1/users/" + bobId)
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(
            content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(BOB))));
  }

  @Test
  void auditoriaAtribuiEventosAoUsuarioCorreto() throws Exception {
    Integer eventosAlice =
        jdbcTemplate.queryForObject(
            "select count(*) from audit_event where user_id = ? and event_type = 'LOGIN_SUCCESS'",
            Integer.class,
            aliceId);
    Integer eventosBob =
        jdbcTemplate.queryForObject(
            "select count(*) from audit_event where user_id = ? and event_type = 'LOGIN_SUCCESS'",
            Integer.class,
            bobId);

    assertThat(eventosAlice).isEqualTo(1);
    assertThat(eventosBob).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_event where user_id = ? and event_type = 'LOGOUT'",
                Integer.class,
                bobId))
        .isZero();
  }

  @Test
  void trocaDeSenhaDeUmUsuarioNaoAlteraOHashDoOutro() throws Exception {
    String hashBobAntes =
        jdbcTemplate.queryForObject(
            "select password_hash from app_user where id = ?", String.class, bobId);

    alice
        .put(
            "/api/v1/auth/password",
            Map.of(
                "currentPassword", ALICE_PASSWORD, "newPassword", "montanha verde silencio longo"))
        .andExpect(status().isNoContent());

    String hashBobDepois =
        jdbcTemplate.queryForObject(
            "select password_hash from app_user where id = ?", String.class, bobId);
    assertThat(hashBobDepois).isEqualTo(hashBobAntes);
  }

  @Test
  void credenciaisDeOutroUsuarioNaoConcedemAcesso() throws Exception {
    AuthTestClient tentativa = new AuthTestClient(mockMvc, jsonMapper).csrf();

    tentativa
        .post("/api/v1/auth/login", Map.of("username", BOB, "password", ALICE_PASSWORD))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(BOB_PASSWORD))));
  }

  private UUID userId(String username) {
    return jdbcTemplate.queryForObject(
        "select id from app_user where username_normalized = ?", UUID.class, username);
  }
}
