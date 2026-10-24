package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.auth.application.AssistedPasswordResetService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PostgresTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recuperação assistida de senha (T197/FR-139).
 *
 * <p>Prova isolamento entre usuários, exigência do token operacional, revogação de sessões, troca
 * efetiva da credencial e auditoria sanitizada sem a senha temporária.
 */
@AutoConfigureMockMvc
class PasswordResetIT extends PostgresTestBase {

  private static final String ALICE = "alice.reset";
  private static final String BOB = "bob.reset";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String OPERATOR_TOKEN = "test-operator-reset-token";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private AssistedPasswordResetService resetService;

  private AuthTestClient alice;
  private AuthTestClient bob;
  private UUID aliceId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bob = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    bob.register(BOB, PASSWORD).login(BOB, PASSWORD);
    aliceId =
        UUID.fromString(
            jsonMapper
                .readTree(
                    alice
                        .get("/api/v1/auth/me")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("id")
                .asString());
  }

  @Test
  void exigeTokenOperacionalEUsuarioExistente() {
    assertThatThrownBy(() -> resetService.reset(ALICE, "token-errado", "operador.teste"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resetService.reset("ninguem.existe", OPERATOR_TOKEN, "operador.teste"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void redefineSenhaRevogaSessoesEAuditaSemExporSegredo() throws Exception {
    AssistedPasswordResetService.ResetResult result =
        resetService.reset(ALICE, OPERATOR_TOKEN, "operador.teste");

    assertThat(result.username()).isEqualTo(ALICE);
    assertThat(result.temporaryPassword()).hasSize(24);
    assertThat(result.temporaryPassword()).isNotEqualTo(PASSWORD);
    assertThat(result.sessionsRevoked()).isGreaterThanOrEqualTo(1);

    alice.get("/api/v1/auth/me").andExpect(status().isUnauthorized());

    AuthTestClient oldPassword = new AuthTestClient(mockMvc, jsonMapper).csrf();
    oldPassword
        .post("/api/v1/auth/login", Map.of("username", ALICE, "password", PASSWORD))
        .andExpect(status().isUnauthorized());

    AuthTestClient newPassword = new AuthTestClient(mockMvc, jsonMapper).csrf();
    newPassword.login(ALICE, result.temporaryPassword());

    // O outro usuário mantém a sessão e a senha.
    bob.get("/api/v1/auth/me").andExpect(status().isOk());
    AuthTestClient bobAgain = new AuthTestClient(mockMvc, jsonMapper).csrf();
    bobAgain.login(BOB, PASSWORD);

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select user_id, metadata::text as metadata from audit_event"
                + " where event_type = 'PASSWORD_RESET_ASSISTED'");
    assertThat(audit.get("user_id")).isEqualTo(aliceId);
    assertThat(String.valueOf(audit.get("metadata")))
        .contains("operador.teste")
        .contains("sessionsRevoked")
        .doesNotContain(result.temporaryPassword());
  }
}
