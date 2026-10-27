package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Revisão de headers, CORS, CSRF e cookies (T171).
 *
 * <p>Prova que a API responde com HSTS, CSP, nosniff, frame-ancestors e política de referência, que
 * CORS não é concedido a outra origem, que mutações sem CSRF válido falham e que o cookie de sessão
 * mantém os atributos seguros.
 */
@AutoConfigureMockMvc
class SecurityHeadersIT extends PostgresTestBase {

  private static final String USERNAME = "alice.headers";
  private static final String PASSWORD = "cavalo correto bateria grampo";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  private AuthTestClient alice;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(USERNAME, PASSWORD).login(USERNAME, PASSWORD);
  }

  @Test
  void respostasIncluemHeadersDeSeguranca() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/csrf").secure(true))
        .andExpect(status().isNoContent())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    org.hamcrest.Matchers.containsString("default-src 'self'")))
        .andExpect(
            header()
                .string(
                    "Strict-Transport-Security",
                    org.hamcrest.Matchers.containsString("includeSubDomains")))
        .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
        .andExpect(
            header()
                .string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")));
  }

  @Test
  void naoConcedeCorsParaOutraOrigem() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/auth/me")
                .header(HttpHeaders.ORIGIN, "https://atacante.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(
            result -> {
              String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
              assertThat(allowOrigin).isNull();
            });

    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header(HttpHeaders.ORIGIN, "https://atacante.example")
                .cookie(
                    new jakarta.servlet.http.Cookie(
                        AuthTestClient.SESSION_COOKIE, alice.sessionId()))
                .cookie(
                    new jakarta.servlet.http.Cookie(AuthTestClient.CSRF_COOKIE, alice.csrfToken()))
                .header(AuthTestClient.CSRF_HEADER, alice.csrfToken()))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull());
  }

  @Test
  void mutacaoSemTokenCsrfEhRejeitada() throws Exception {
    String body =
        jsonMapper.writeValueAsString(
            Map.of("currentPassword", PASSWORD, "newPassword", "montanha verde silencio longo"));
    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(
                    new jakarta.servlet.http.Cookie(
                        AuthTestClient.SESSION_COOKIE, alice.sessionId())))
        .andExpect(status().isForbidden());

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select event_type, metadata::text as metadata from audit_event"
                + " where event_type = 'CSRF_REJECTED' order by created_at desc limit 1");
    assertThat(String.valueOf(audit.get("metadata")))
        .contains("POST")
        .contains("/api/v1/auth/password");
  }

  @Test
  void csrfAnonimoTambemEhAuditadoSemUsuario() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        Map.of("username", "sem.csrf", "password", PASSWORD))))
        .andExpect(status().isForbidden());

    Integer events =
        jdbcTemplate.queryForObject(
            "select count(*) from audit_event"
                + " where event_type = 'CSRF_REJECTED' and user_id is null",
            Integer.class);
    assertThat(events).isGreaterThanOrEqualTo(1);
  }

  @Test
  void tokenCsrfDivergenteDoCookieEhRejeitado() throws Exception {
    String body =
        jsonMapper.writeValueAsString(
            Map.of("currentPassword", PASSWORD, "newPassword", "montanha verde silencio longo"));
    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(
                    new jakarta.servlet.http.Cookie(
                        AuthTestClient.SESSION_COOKIE, alice.sessionId()))
                .cookie(
                    new jakarta.servlet.http.Cookie(AuthTestClient.CSRF_COOKIE, alice.csrfToken()))
                .header(AuthTestClient.CSRF_HEADER, "token-forjado"))
        .andExpect(status().isForbidden());
  }
}
