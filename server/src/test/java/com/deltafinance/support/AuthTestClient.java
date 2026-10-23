package com.deltafinance.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de teste que simula um navegador: mantém cookie de sessão e token CSRF.
 *
 * <p>Cada instância representa um usuário/sessão independente, permitindo testar isolamento e
 * revogação de sessão com dois clientes no mesmo teste.
 */
public final class AuthTestClient {

  public static final String SESSION_COOKIE = "__Host-delta_session";
  public static final String CSRF_COOKIE = "XSRF-TOKEN";
  public static final String CSRF_HEADER = "X-XSRF-TOKEN";

  private final MockMvc mockMvc;
  private final JsonMapper jsonMapper;
  private String sessionId;
  private String csrfToken;

  public AuthTestClient(MockMvc mockMvc, JsonMapper jsonMapper) {
    this.mockMvc = mockMvc;
    this.jsonMapper = jsonMapper;
  }

  /** Obtém/renova o cookie CSRF. */
  public AuthTestClient csrf() throws Exception {
    MvcResult result =
        mockMvc
            .perform(request(HttpMethod.GET, "/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn();
    csrfToken = cookieValue(result, CSRF_COOKIE);
    if (csrfToken == null) {
      throw new AssertionError("Endpoint CSRF não emitiu o cookie " + CSRF_COOKIE);
    }
    return this;
  }

  public AuthTestClient register(String username, String password) throws Exception {
    post("/api/v1/auth/register", java.util.Map.of("username", username, "password", password))
        .andExpect(status().isCreated());
    return this;
  }

  public AuthTestClient login(String username, String password) throws Exception {
    MvcResult result =
        post("/api/v1/auth/login", java.util.Map.of("username", username, "password", password))
            .andExpect(status().isOk())
            .andReturn();
    sessionId = cookieValue(result, SESSION_COOKIE);
    if (sessionId == null) {
      throw new AssertionError("Login não emitiu o cookie de sessão " + SESSION_COOKIE);
    }
    return this;
  }

  public AuthTestClient logout() throws Exception {
    post("/api/v1/auth/logout", null).andExpect(status().isNoContent());
    sessionId = null;
    csrfToken = null;
    return this;
  }

  public ResultActions get(String path) throws Exception {
    return mockMvc.perform(withCookies(request(HttpMethod.GET, path)));
  }

  public ResultActions post(String path, Object body) throws Exception {
    return mockMvc.perform(withBody(withCookies(request(HttpMethod.POST, path)), body));
  }

  public ResultActions put(String path, Object body) throws Exception {
    return mockMvc.perform(withBody(withCookies(request(HttpMethod.PUT, path)), body));
  }

  public ResultActions delete(String path) throws Exception {
    return mockMvc.perform(withCookies(request(HttpMethod.DELETE, path)));
  }

  public String sessionId() {
    return sessionId;
  }

  public String csrfToken() {
    return csrfToken;
  }

  /** Anexa um cookie de sessão já existente (para simular reutilização/roubo de cookie). */
  public AuthTestClient withSession(String cookieValue) {
    this.sessionId = cookieValue;
    return this;
  }

  public AuthTestClient withoutSession() {
    this.sessionId = null;
    return this;
  }

  public MockHttpServletRequestBuilder withCookies(MockHttpServletRequestBuilder builder) {
    if (sessionId != null) {
      builder.cookie(new Cookie(SESSION_COOKIE, sessionId));
    }
    if (csrfToken != null) {
      // O navegador envia o cookie e o header; o repositório carrega o token do cookie.
      builder.cookie(new Cookie(CSRF_COOKIE, csrfToken));
      builder.header(CSRF_HEADER, csrfToken);
    }
    return builder;
  }

  /** Lê o valor de um cookie a partir dos headers Set-Cookie (ResponseCookie não usa addCookie). */
  public static String cookieValue(MvcResult result, String name) {
    for (String header : result.getResponse().getHeaders("Set-Cookie")) {
      if (header.startsWith(name + "=")) {
        String value = header.substring(name.length() + 1);
        int separator = value.indexOf(';');
        return separator >= 0 ? value.substring(0, separator) : value;
      }
    }
    return null;
  }

  private MockHttpServletRequestBuilder withBody(
      MockHttpServletRequestBuilder builder, Object body) {
    if (body == null) {
      return builder;
    }
    return builder
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonMapper.writeValueAsString(body));
  }
}
