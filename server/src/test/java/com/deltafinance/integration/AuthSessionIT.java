package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.security.SessionAttributes;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Sessão, expiração, logout, troca de senha e limite de tentativas (FR-003/008/009/010). */
@AutoConfigureMockMvc
class AuthSessionIT extends PostgresTestBase {

  private static final String USERNAME = "alice.familia";
  private static final String PASSWORD = "cavalo correto bateria grampo";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JdbcIndexedSessionRepository sessionRepository;

  private AuthTestClient alice;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
  }

  @Test
  void loginEmiteCookieDeSessaoComAtributosSeguros() throws Exception {
    alice.register(USERNAME, PASSWORD);

    var result =
        alice
            .post("/api/v1/auth/login", Map.of("username", USERNAME, "password", PASSWORD))
            .andExpect(status().isOk())
            .andReturn();

    String cookieHeader =
        result.getResponse().getHeaders("Set-Cookie").stream()
            .filter(value -> value.startsWith(AuthTestClient.SESSION_COOKIE + "="))
            .findFirst()
            .orElseThrow();

    assertThat(cookieHeader)
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Lax")
        .contains("Path=/");
  }

  @Test
  void sessaoSemCookieEhRejeitada() throws Exception {
    alice.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }

  @Test
  void sessaoInativaPorMaisDeTrintaMinutosEhRejeitada() throws Exception {
    alice.register(USERNAME, PASSWORD).login(USERNAME, PASSWORD);
    String sessionId = unicaSessao(USERNAME);

    expirarSessao(sessionId);

    alice
        .get("/api/v1/auth/me")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void sessaoComMaisDeDozeHorasEhRejeitadaMesmoComAtividadeRecente() throws Exception {
    alice.register(USERNAME, PASSWORD).login(USERNAME, PASSWORD);
    String sessionId = unicaSessao(USERNAME);

    Session session = sessionRepository.findById(sessionId);
    assertThat(session).isNotNull();
    session.setAttribute(
        SessionAttributes.STARTED_AT,
        Instant.now().minus(Duration.ofHours(12)).minus(Duration.ofMinutes(1)));
    @SuppressWarnings({"rawtypes", "unchecked"})
    org.springframework.session.SessionRepository rawRepository = sessionRepository;
    rawRepository.save(session);

    alice.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRemoveASessaoDoRepositorioEInvalidaOCookie() throws Exception {
    alice.register(USERNAME, PASSWORD).login(USERNAME, PASSWORD);
    String sessionId = unicaSessao(USERNAME);

    alice.logout();

    assertThat(sessionRepository.findById(sessionId)).isNull();
    alice.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }

  @Test
  void trocaDeSenhaEncerraAsDemaisSessoesDoMesmoUsuario() throws Exception {
    alice.register(USERNAME, PASSWORD).login(USERNAME, PASSWORD);
    AuthTestClient segundaSessao = new AuthTestClient(mockMvc, jsonMapper).csrf();
    segundaSessao.login(USERNAME, PASSWORD);
    segundaSessao.get("/api/v1/auth/me").andExpect(status().isOk());

    String novaSenha = "janela azul montanha serena";
    alice
        .put("/api/v1/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", novaSenha))
        .andExpect(status().isNoContent());

    alice.get("/api/v1/auth/me").andExpect(status().isOk());
    segundaSessao.get("/api/v1/auth/me").andExpect(status().isUnauthorized());

    segundaSessao.csrf();
    segundaSessao
        .post("/api/v1/auth/login", Map.of("username", USERNAME, "password", PASSWORD))
        .andExpect(status().isUnauthorized());
    segundaSessao.login(USERNAME, novaSenha);
  }

  @Test
  void limiteDeTentativasDeLoginRetorna429ComRetryAfter() throws Exception {
    alice.register(USERNAME, PASSWORD);
    for (int attempt = 1; attempt <= 5; attempt++) {
      alice
          .post(
              "/api/v1/auth/login",
              Map.of("username", USERNAME, "password", "senha errada bem longa"))
          .andExpect(status().isUnauthorized());
    }

    alice
        .post("/api/v1/auth/login", Map.of("username", USERNAME, "password", PASSWORD))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void bloqueioPersistidoSobreviveAoFimDaJanelaDeslizante() throws Exception {
    alice.register(USERNAME, PASSWORD);
    for (int attempt = 1; attempt <= 5; attempt++) {
      alice
          .post(
              "/api/v1/auth/login",
              Map.of("username", USERNAME, "password", "senha errada bem longa"))
          .andExpect(status().isUnauthorized());
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from login_attempt where blocked_until is not null",
                Integer.class))
        .isEqualTo(5);
    jdbcTemplate.update(
        "update login_attempt set attempted_at = now() - interval '20 minutes'"
            + " where success = false");

    alice
        .post("/api/v1/auth/login", Map.of("username", USERNAME, "password", PASSWORD))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void bloqueioExpiradoPermiteNovoLogin() throws Exception {
    alice.register(USERNAME, PASSWORD);
    for (int attempt = 1; attempt <= 5; attempt++) {
      alice
          .post(
              "/api/v1/auth/login",
              Map.of("username", USERNAME, "password", "senha errada bem longa"))
          .andExpect(status().isUnauthorized());
    }
    jdbcTemplate.update(
        "update login_attempt set blocked_until = now() - interval '1 minute'"
            + " where success = false");

    alice.withoutSession().login(USERNAME, PASSWORD);
    alice.get("/api/v1/auth/me").andExpect(status().isOk());
  }

  @Test
  void bloqueioDeUmIpNaoAfetaOutroIp() throws Exception {
    alice.register(USERNAME, PASSWORD);
    for (int attempt = 1; attempt <= 5; attempt++) {
      alice
          .post(
              "/api/v1/auth/login",
              Map.of("username", USERNAME, "password", "senha errada bem longa"))
          .andExpect(status().isUnauthorized());
    }

    alice
        .postFromIp(
            "/api/v1/auth/login", Map.of("username", USERNAME, "password", PASSWORD), "203.0.113.9")
        .andExpect(status().isOk());
  }

  @Test
  void cookieDeSessaoInvalidoNaoConcedeAcesso() throws Exception {
    alice
        .withSession("sessao-inexistente")
        .get("/api/v1/auth/me")
        .andExpect(status().isUnauthorized());
  }

  @Test
  void sessaoExpiradaPorInatividadePermiteNovoLogin() throws Exception {
    alice.register(USERNAME, PASSWORD).login(USERNAME, PASSWORD);
    String sessionId = unicaSessao(USERNAME);
    expirarSessao(sessionId);
    alice.get("/api/v1/auth/me").andExpect(status().isUnauthorized());

    alice.withoutSession().login(USERNAME, PASSWORD);
    alice.get("/api/v1/auth/me").andExpect(status().isOk());
  }

  private void expirarSessao(String sessionId) {
    long agora = System.currentTimeMillis();
    jdbcTemplate.update(
        "update spring_session set last_access_time = ?, expiry_time = ? where session_id = ?",
        agora - Duration.ofMinutes(31).toMillis(),
        agora - 60_000,
        sessionId);
  }

  private String unicaSessao(String username) {
    var sessions = sessionRepository.findByPrincipalName(username);
    assertThat(sessions).hasSize(1);
    return sessions.keySet().iterator().next();
  }
}
