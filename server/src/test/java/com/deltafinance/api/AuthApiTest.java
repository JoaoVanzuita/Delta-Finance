package com.deltafinance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Contrato dos endpoints de identidade (FR-001 a FR-011). */
@AutoConfigureMockMvc
class AuthApiTest extends PostgresTestBase {

  private static final String USERNAME = "alice.familia";
  private static final String VALID_PASSWORD = "cavalo correto bateria grampo";
  private static final String COMMON_PASSWORD = "unbelievable";
  private static final String PASSWORD_128 = "Zx9!".repeat(32);

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private AuthTestClient client;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    client = new AuthTestClient(mockMvc, jsonMapper).csrf();
  }

  @Test
  void csrfEndpointEmiteCookieESemCorpo() throws Exception {
    mockMvc
        .perform(request(HttpMethod.GET, "/api/v1/auth/csrf"))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Set-Cookie"));
  }

  @Test
  void cadastroCriaContaSemExporSenhaNemHash() throws Exception {
    client
        .post(
            "/api/v1/auth/register",
            Map.of("username", "Alice.Familia", "password", VALID_PASSWORD))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.username").value("Alice.Familia"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    String hash =
        jdbcTemplate.queryForObject(
            "select password_hash from app_user where username_normalized = ?",
            String.class,
            USERNAME);
    assertThat(hash).startsWith("$argon2id$").isNotEqualTo(VALID_PASSWORD);
  }

  @Test
  void cadastroRecusaNomeDuplicadoSemRevelarDadosDaOutraConta() throws Exception {
    client.register(USERNAME, VALID_PASSWORD);

    client
        .post(
            "/api/v1/auth/register",
            Map.of("username", "ALICE.FAMILIA", "password", "outra senha bem longa"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.createdAt").doesNotExist());
  }

  @Test
  void cadastroRejeitaSenhaComMenosDeDozeCaracteres() throws Exception {
    client
        .post("/api/v1/auth/register", Map.of("username", USERNAME, "password", "curta123456"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  void cadastroRejeitaSenhaComMaisDe128Caracteres() throws Exception {
    client
        .post("/api/v1/auth/register", Map.of("username", USERNAME, "password", PASSWORD_128 + "a"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  void cadastroAceitaFraseSenhaDe128Caracteres() throws Exception {
    client
        .post("/api/v1/auth/register", Map.of("username", USERNAME, "password", PASSWORD_128))
        .andExpect(status().isCreated());
  }

  @Test
  void cadastroRejeitaSenhaDaListaLocalDeSenhasComuns() throws Exception {
    client
        .post("/api/v1/auth/register", Map.of("username", USERNAME, "password", COMMON_PASSWORD))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(jsonPath("$.errors[0].message").value("Escolha uma senha menos comum."));
  }

  @Test
  void cadastroRejeitaSenhaQueContemONomeDeUsuario() throws Exception {
    client
        .post(
            "/api/v1/auth/register",
            Map.of("username", USERNAME, "password", "Alice.Familia-2026!"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(
            jsonPath("$.errors[0].message").value("A senha não pode conter o nome de usuário."));
  }

  @Test
  void loginUsaMensagemGenericaParaUsuarioInexistenteESenhaIncorreta() throws Exception {
    client.register(USERNAME, VALID_PASSWORD);

    var senhaErrada =
        client
            .post(
                "/api/v1/auth/login",
                Map.of("username", USERNAME, "password", "senha errada bem longa"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();

    var usuarioInexistente =
        client
            .post(
                "/api/v1/auth/login",
                Map.of("username", "ninguem.existe", "password", "senha errada bem longa"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();

    String senhaErradaBody = senhaErrada.getResponse().getContentAsString();
    String usuarioInexistenteBody = usuarioInexistente.getResponse().getContentAsString();

    assertThat(senhaErradaBody).doesNotContain(USERNAME).doesNotContain("ninguem.existe");
    assertThat(usuarioInexistenteBody).doesNotContain(USERNAME).doesNotContain("ninguem.existe");
    assertThat(jsonMapper.readTree(usuarioInexistenteBody).path("detail").asString())
        .isEqualTo(jsonMapper.readTree(senhaErradaBody).path("detail").asString());
  }

  @Test
  void bloqueiaAposCincoFalhasEmQuinzeMinutos() throws Exception {
    client.register(USERNAME, VALID_PASSWORD);

    for (int attempt = 1; attempt <= 5; attempt++) {
      client
          .post(
              "/api/v1/auth/login",
              Map.of("username", USERNAME, "password", "senha errada bem longa"))
          .andExpect(status().isUnauthorized());
    }

    client
        .post("/api/v1/auth/login", Map.of("username", USERNAME, "password", VALID_PASSWORD))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void loginBemSucedidoZeraOContadorDeFalhas() throws Exception {
    client.register(USERNAME, VALID_PASSWORD);
    for (int attempt = 1; attempt <= 4; attempt++) {
      client
          .post(
              "/api/v1/auth/login",
              Map.of("username", USERNAME, "password", "senha errada bem longa"))
          .andExpect(status().isUnauthorized());
    }

    client.login(USERNAME, VALID_PASSWORD);
    client.logout();
    client.csrf();

    for (int attempt = 1; attempt <= 5; attempt++) {
      client
          .post(
              "/api/v1/auth/login",
              Map.of("username", USERNAME, "password", "senha errada bem longa"))
          .andExpect(status().isUnauthorized());
    }
    client
        .post("/api/v1/auth/login", Map.of("username", USERNAME, "password", VALID_PASSWORD))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void meExigeSessaoERetornaApenasOProprioUsuario() throws Exception {
    client.register(USERNAME, VALID_PASSWORD);
    client.get("/api/v1/auth/me").andExpect(status().isUnauthorized());

    client.login(USERNAME, VALID_PASSWORD);
    client
        .get("/api/v1/auth/me")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(USERNAME));
  }

  @Test
  void logoutExigeSessaoEInvalidaAAcesso() throws Exception {
    client.post("/api/v1/auth/logout", null).andExpect(status().isUnauthorized());

    client.register(USERNAME, VALID_PASSWORD).login(USERNAME, VALID_PASSWORD);
    client.logout();
    client.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }

  @Test
  void trocaDeSenhaExigeSenhaAtualAplicaPoliticaEInvalidaASenhaAntiga() throws Exception {
    client.register(USERNAME, VALID_PASSWORD).login(USERNAME, VALID_PASSWORD);

    client
        .put(
            "/api/v1/auth/password",
            Map.of("currentPassword", "senha atual errada", "newPassword", "nova senha bem longa"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

    client
        .put(
            "/api/v1/auth/password",
            Map.of("currentPassword", VALID_PASSWORD, "newPassword", "curta123456"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

    String newPassword = "janela azul montanha serena";
    client
        .put(
            "/api/v1/auth/password",
            Map.of("currentPassword", VALID_PASSWORD, "newPassword", newPassword))
        .andExpect(status().isNoContent());

    client.logout();
    client.csrf();
    client
        .post("/api/v1/auth/login", Map.of("username", USERNAME, "password", VALID_PASSWORD))
        .andExpect(status().isUnauthorized());
    client.login(USERNAME, newPassword);
  }

  @Test
  void errosUsamProblemJsonComRequestId() throws Exception {
    client
        .get("/api/v1/auth/me")
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }
}
