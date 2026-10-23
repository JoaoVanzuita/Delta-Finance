package com.deltafinance.support;

import com.deltafinance.support.TwoUserFixture.TestUser;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Fábrica de dados sintéticos para testes.
 *
 * <p>Nunca use dados reais nem reaproveite estes valores em produção. Cada chamada gera
 * identificadores únicos para evitar interferência entre testes.
 */
public final class TestDataFactory {

  public static final String DEFAULT_PASSWORD = "montanha verde silencio longo";

  private TestDataFactory() {}

  public static String uniqueUsername(String prefix) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    return (prefix + "." + suffix).toLowerCase(Locale.ROOT);
  }

  public static TestUser createUser(
      JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, String prefix) {
    return createUser(jdbcTemplate, passwordEncoder, uniqueUsername(prefix), DEFAULT_PASSWORD);
  }

  public static TestUser createUser(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      String username,
      String password) {
    TestUser user = new TestUser(UUID.randomUUID(), username, password);
    java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
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
    return user;
  }

  public static void deleteUser(JdbcTemplate jdbcTemplate, TestUser user) {
    jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", user.id());
  }
}
