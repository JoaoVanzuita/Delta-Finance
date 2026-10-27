package com.deltafinance.integration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Rate limit de cadastro por IP (C03) com limite reduzido para o teste. */
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "delta.security.registration.max-attempts=3",
      "delta.security.registration.window=1h"
    })
class RegistrationRateLimitIT extends PostgresTestBase {

  private static final String PASSWORD = "cavalo correto bateria grampo";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  private AuthTestClient client;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    client = new AuthTestClient(mockMvc, jsonMapper).csrf();
  }

  @Test
  void rajadaAcimaDoLimiteRetorna429ComRetryAfter() throws Exception {
    for (int index = 1; index <= 3; index++) {
      client
          .postFromIp(
              "/api/v1/auth/register",
              Map.of("username", "cadastro" + index, "password", PASSWORD),
              "198.51.100.10")
          .andExpect(status().isCreated());
    }

    client
        .postFromIp(
            "/api/v1/auth/register",
            Map.of("username", "cadastro4", "password", PASSWORD),
            "198.51.100.10")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void ipDistintoNaoHerdamOLimiteDeOutro() throws Exception {
    for (int index = 1; index <= 3; index++) {
      client
          .postFromIp(
              "/api/v1/auth/register",
              Map.of("username", "limite" + index, "password", PASSWORD),
              "198.51.100.11")
          .andExpect(status().isCreated());
    }

    client
        .postFromIp(
            "/api/v1/auth/register",
            Map.of("username", "outro.ip", "password", PASSWORD),
            "198.51.100.12")
        .andExpect(status().isCreated());
  }
}
