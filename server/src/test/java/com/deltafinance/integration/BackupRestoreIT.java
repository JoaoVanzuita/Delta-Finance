package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.KeyRing;
import com.deltafinance.support.PostgresTestBase;
import com.deltafinance.support.TestDataFactory;
import com.deltafinance.support.TwoUserFixture.TestUser;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.Container.ExecResult;

/**
 * Ensaio de backup/restauração em banco limpo (T175).
 *
 * <p>Gera um dump com {@code pg_dump -Fc}, restaura em um banco vazio e prova que (a) as contagens
 * e o histórico Flyway sobrevivem, (b) os campos protegidos continuam cifrados e (c) sem o key ring
 * correto o conteúdo não é legível.
 */
class BackupRestoreIT extends PostgresTestBase {

  private static final String RESTORE_DB = "delta_restore_check";
  private static final String PLAINTEXT = "Categoria sigilosa de backup";
  private static final String DUMP_FILE = "/tmp/delta-backup-check.dump";

  @Autowired private CryptoService cryptoService;
  @Autowired private PasswordEncoder passwordEncoder;

  private TestUser user;
  private UUID categoryId;

  @BeforeEach
  void setUp() {
    cleanIdentityData();
    user = TestDataFactory.createUser(jdbcTemplate, passwordEncoder, "backup");
    categoryId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        """
        insert into category
          (id, user_id, name_enc, normalized_name_hash, kind, active, created_at, updated_at, version)
        values (?, ?, ?, ?, 'EXPENSE', true, ?, ?, 0)
        """,
        categoryId,
        user.id(),
        cryptoService.encrypt(
            PLAINTEXT, new CryptoContext(user.id(), "category", "name", categoryId)),
        new byte[] {1, 2, 3, 4},
        now,
        now);
  }

  @Test
  void dumpRestauradoEmBancoLimpoPreservaDadosEContinuaIlegivelSemKeyRing() throws Exception {
    dropRestoreDatabase();
    exec("psql", "-U", "delta", "-d", "delta_finance", "-c", "create database " + RESTORE_DB);
    exec("pg_dump", "-U", "delta", "-d", "delta_finance", "-Fc", "-f", DUMP_FILE);
    exec("pg_restore", "-U", "delta", "-d", RESTORE_DB, "--no-owner", DUMP_FILE);

    Integer categories =
        queryRestore("select count(*) from category where id = '" + categoryId + "'");
    Integer users = queryRestore("select count(*) from app_user where id = '" + user.id() + "'");
    Integer baselineMigrations =
        queryRestore(
            "select count(*) from flyway_schema_history"
                + " where success = true and version = '001' and description = 'baseline'");

    assertThat(users).isEqualTo(1);
    assertThat(categories).isEqualTo(1);
    assertThat(baselineMigrations).isEqualTo(1);

    byte[] restored =
        jdbcTemplate.queryForObject(
            "select name_enc from category where id = ?", byte[].class, categoryId);
    assertThat(restored).isNotNull();
    assertThat(new String(restored, StandardCharsets.UTF_8)).doesNotContain(PLAINTEXT);
    assertThat(
            cryptoService.decryptString(
                restored, new CryptoContext(user.id(), "category", "name", categoryId)))
        .isEqualTo(PLAINTEXT);

    // Um key ring diferente (mesmo formato e versão) não decifra o dump restaurado.
    KeyRing wrongRing = new KeyRing(Map.of(1, randomKey()), 1, Map.of(1, randomKey()), 1);
    CryptoService withoutOriginalKeys = new CryptoService(wrongRing);
    assertThatThrownBy(
            () ->
                withoutOriginalKeys.decryptString(
                    restored, new CryptoContext(user.id(), "category", "name", categoryId)))
        .isInstanceOf(CryptoException.class);

    dropRestoreDatabase();
  }

  private Integer queryRestore(String sql) throws Exception {
    ExecResult result =
        postgres().execInContainer("psql", "-U", "delta", "-d", RESTORE_DB, "-tAc", sql);
    if (result.getExitCode() != 0) {
      throw new IllegalStateException("Consulta no banco restaurado falhou: " + result.getStderr());
    }
    return Integer.parseInt(result.getStdout().trim());
  }

  private void exec(String... command) throws Exception {
    ExecResult result = postgres().execInContainer(command);
    if (result.getExitCode() != 0) {
      throw new IllegalStateException(
          "Comando de backup/restauração falhou ("
              + result.getExitCode()
              + "): "
              + String.join(" ", List.of(command))
              + "\n"
              + result.getStderr());
    }
  }

  private void dropRestoreDatabase() throws Exception {
    ExecResult result =
        postgres()
            .execInContainer(
                "psql",
                "-U",
                "delta",
                "-d",
                "postgres",
                "-c",
                "drop database if exists " + RESTORE_DB + " with (force)");
    if (result.getExitCode() != 0) {
      throw new IllegalStateException(
          "Falha ao remover banco de restauração: " + result.getStderr());
    }
  }

  private static SecretKey randomKey() {
    byte[] material = new byte[32];
    new SecureRandom().nextBytes(material);
    return new SecretKeySpec(material, "AES");
  }
}
