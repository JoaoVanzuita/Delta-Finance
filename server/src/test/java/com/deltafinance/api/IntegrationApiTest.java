package com.deltafinance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PostgresTestBase;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Contrato dos endpoints de integração, conexões, contas e transações (US2). */
@AutoConfigureMockMvc
class IntegrationApiTest extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String USERNAME = "alice.familia";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT_ID = "client-alice";
  private static final String CLIENT_SECRET = "secret-alice";

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

  private AuthTestClient client;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    PLUGGY.registerClient(CLIENT_ID, CLIENT_SECRET);
    client = new AuthTestClient(mockMvc, jsonMapper).csrf();
    client.register(USERNAME, PASSWORD).login(USERNAME, PASSWORD);
  }

  @Test
  void integracaoNaoConfiguradaRetornaIncompletaSemSegredos() throws Exception {
    client
        .get("/api/v1/integrations/pluggy")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.provider").value("MEU_PLUGGY"))
        .andExpect(jsonPath("$.status").value("INCOMPLETE"))
        .andExpect(jsonPath("$.connectionCount").value(0))
        .andExpect(jsonPath("$.clientIdMasked").doesNotExist());
  }

  @Test
  void salvaCredenciaisValidasComClientIdMascarado() throws Exception {
    client
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT_ID, "clientSecret", CLIENT_SECRET))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INCOMPLETE"))
        .andExpect(jsonPath("$.clientIdMasked").value("clie…lice"))
        .andExpect(jsonPath("$.connectionCount").value(0));

    String body =
        client.get("/api/v1/integrations/pluggy").andReturn().getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain(CLIENT_SECRET)
        .doesNotContain("apiKey")
        .doesNotContain("connect_token")
        .doesNotContain("connectToken");
  }

  @Test
  void credenciaisInvalidasNaoSubstituemConfiguracaoAnterior() throws Exception {
    client.put(
        "/api/v1/integrations/pluggy",
        Map.of("clientId", CLIENT_ID, "clientSecret", CLIENT_SECRET));

    client
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", "client-invalido", "clientSecret", "segredo-invalido"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_FAILED"));

    client
        .get("/api/v1/integrations/pluggy")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientIdMasked").value("clie…lice"));
  }

  @Test
  void credenciaisRecusadasCom400DoProvedorRetornamAuthFailed() throws Exception {
    // A Pluggy real responde 400 quando o Client ID não é um UUID; isso é falha de
    // credencial, não indisponibilidade do provedor.
    PLUGGY.failPath("/auth", 400, 1);

    client
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", "client-invalido", "clientSecret", "segredo-invalido"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_FAILED"));
  }

  @Test
  void validaCredenciaisSemPersistirSegredo() throws Exception {
    client
        .post(
            "/api/v1/integrations/pluggy/validate",
            Map.of("clientId", CLIENT_ID, "clientSecret", CLIENT_SECRET))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.itemValidated").value(false));
  }

  @Test
  void endpointsExigemSessao() throws Exception {
    AuthTestClient anonymous = new AuthTestClient(mockMvc, jsonMapper);
    anonymous.get("/api/v1/integrations/pluggy").andExpect(status().isUnauthorized());
    anonymous.get("/api/v1/connections").andExpect(status().isUnauthorized());
    anonymous.get("/api/v1/accounts").andExpect(status().isUnauthorized());
    anonymous.get("/api/v1/transactions").andExpect(status().isUnauthorized());
  }

  @Test
  void listasVaziasSaoRetornadasAntesDeConectar() throws Exception {
    client
        .get("/api/v1/accounts")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    client
        .get("/api/v1/transactions")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.hasMore").value(false));
    client
        .get("/api/v1/connections")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void cadastroDeCredenciaisVaziasRetorna422() throws Exception {
    client
        .put("/api/v1/integrations/pluggy", Map.of("clientId", " ", "clientSecret", " "))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
