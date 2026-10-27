package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.auth.application.AssistedPasswordResetService;
import com.deltafinance.auth.domain.AppUser;
import com.deltafinance.auth.domain.AppUserRepository;
import com.deltafinance.support.PostgresTestBase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Recuperação assistida sem contexto web (C25).
 *
 * <p>O runner operacional roda com {@code web-application-type=none}. Este teste garante que o
 * serviço inicia e revoga sessões via JDBC sem depender de um bean de sessão servlet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PasswordResetNonWebIT extends PostgresTestBase {

  private static final String USERNAME = "alice.nonweb";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String TOKEN = "test-operator-reset-token";

  @Autowired private AssistedPasswordResetService resetService;
  @Autowired private AppUserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private AppUser user;

  @BeforeEach
  void setUp() {
    cleanIdentityData();
    user =
        userRepository.save(
            new AppUser(USERNAME, "Alice NonWeb", passwordEncoder.encode(PASSWORD)));
    long now = System.currentTimeMillis();
    jdbcTemplate.update(
        "insert into spring_session"
            + " (primary_id, session_id, creation_time, last_access_time, max_inactive_interval,"
            + " expiry_time, principal_name) values (?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        now,
        now,
        1800,
        now + 1_800_000,
        USERNAME);
  }

  @Test
  void contextoNaoWebRedefineSenhaERevogaSessoesSemRepositorioServlet() {
    assertThat(resetService.isConfigured()).isTrue();

    AssistedPasswordResetService.ResetResult result = resetService.reset(USERNAME, TOKEN, "teste");

    assertThat(result.sessionsRevoked()).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from spring_session where principal_name = ?",
                Integer.class,
                USERNAME))
        .isZero();
    assertThat(
            passwordEncoder.matches(
                result.temporaryPassword(),
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash()))
        .isTrue();
  }
}
