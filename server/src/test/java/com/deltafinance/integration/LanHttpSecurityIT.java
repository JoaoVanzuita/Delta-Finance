package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** Garante que o modo lan-http emite cookie utilizável em HTTP e não envia HSTS. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "delta.deployment.mode=lan-http")
class LanHttpSecurityIT extends PostgresTestBase {

  private static final String USERNAME = "alice.lan-http";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String SESSION_COOKIE = "delta_session";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() {
    cleanIdentityData();
  }

  @Test
  void modoLanHttpUsaCookieCompativelComHttpESemHsts() throws Exception {
    MvcResult csrf =
        mockMvc
            .perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andExpect(header().doesNotExist("Strict-Transport-Security"))
            .andReturn();
    String csrfToken = AuthTestClient.cookieValue(csrf, AuthTestClient.CSRF_COOKIE);
    assertThat(csrfToken).isNotBlank();

    String csrfSetCookie =
        csrf.getResponse().getHeaders("Set-Cookie").stream()
            .filter(cookie -> cookie.startsWith(AuthTestClient.CSRF_COOKIE + "="))
            .findFirst()
            .orElseThrow();
    assertThat(csrfSetCookie).doesNotContain("Secure");

    String credentials =
        jsonMapper.writeValueAsString(Map.of("username", USERNAME, "password", PASSWORD));
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials)
                .cookie(new Cookie(AuthTestClient.CSRF_COOKIE, csrfToken))
                .header(AuthTestClient.CSRF_HEADER, csrfToken))
        .andExpect(status().isCreated());

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials)
                    .cookie(new Cookie(AuthTestClient.CSRF_COOKIE, csrfToken))
                    .header(AuthTestClient.CSRF_HEADER, csrfToken))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Strict-Transport-Security"))
            .andReturn();

    String sessionSetCookie =
        login.getResponse().getHeaders("Set-Cookie").stream()
            .filter(cookie -> cookie.startsWith(SESSION_COOKIE + "="))
            .findFirst()
            .orElseThrow();
    assertThat(sessionSetCookie).doesNotContain("Secure");
    assertThat(login.getResponse().getHeaders("Set-Cookie"))
        .noneMatch(cookie -> cookie.startsWith(AuthTestClient.SESSION_COOKIE + "="));

    String sessionId = AuthTestClient.cookieValue(login, SESSION_COOKIE);
    assertThat(sessionId).isNotBlank();
    mockMvc
        .perform(get("/api/v1/auth/me").cookie(new Cookie(SESSION_COOKIE, sessionId)))
        .andExpect(status().isOk());
  }
}
