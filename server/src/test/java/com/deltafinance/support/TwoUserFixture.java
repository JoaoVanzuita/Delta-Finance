package com.deltafinance.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria dois usuários independentes para os testes de isolamento (T042+).
 *
 * <p>Os usuários são inseridos diretamente no banco para não dependerem da API de cadastro. As
 * senhas são conhecidas e não pertencem à lista de senhas comuns.
 */
public final class TwoUserFixture {

  public record TestUser(UUID id, String username, String password) {}

  public static final String ALICE_USERNAME = "alice.familia";
  public static final String ALICE_PASSWORD = "cavalo correto bateria grampo";
  public static final String BOB_USERNAME = "bob.familia";
  public static final String BOB_PASSWORD = "janela azul montanha serena";

  private TwoUserFixture() {}

  public static TestUser alice() {
    return new TestUser(UUID.randomUUID(), ALICE_USERNAME, ALICE_PASSWORD);
  }

  public static TestUser bob() {
    return new TestUser(UUID.randomUUID(), BOB_USERNAME, BOB_PASSWORD);
  }

  /** Insere os dois usuários e devolve os registros criados. */
  public static List<TestUser> create(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
    TestUser alice = alice();
    TestUser bob = bob();
    insert(jdbcTemplate, passwordEncoder, alice);
    insert(jdbcTemplate, passwordEncoder, bob);
    return List.of(alice, bob);
  }

  /** Remove usuários e seus efeitos colaterais (idempotência; auditoria fica com user_id nulo). */
  public static void delete(JdbcTemplate jdbcTemplate, TestUser... users) {
    for (TestUser user : users) {
      jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", user.id());
    }
  }

  private static void insert(
      JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, TestUser user) {
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        """
        INSERT INTO app_user
          (id, username_normalized, username_display, password_hash, status,
           created_at, updated_at, version)
        VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0)
        """,
        user.id(),
        user.username(),
        user.username(),
        passwordEncoder.encode(user.password()),
        now,
        now);
  }
}
