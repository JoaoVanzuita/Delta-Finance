package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.KeyRing;
import com.deltafinance.shared.crypto.KeyRotationJob;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.support.PostgresTestBase;
import com.deltafinance.support.TestDataFactory;
import com.deltafinance.support.TwoUserFixture.TestUser;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Rotação online da chave de dados (T176): regrava com a nova versão e é idempotente. */
class KeyRotationIT extends PostgresTestBase {

  @Autowired private CryptoService cryptoService;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private Clock clock;

  private TestUser user;
  private UUID categoryId;
  private UUID accountId;

  @BeforeEach
  void setUp() {
    cleanIdentityData();
    user = TestDataFactory.createUser(jdbcTemplate, passwordEncoder, "rotation");
    categoryId = UUID.randomUUID();
    accountId = UUID.randomUUID();
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
            "Categoria rotacionada", new CryptoContext(user.id(), "category", "name", categoryId)),
        new byte[] {9, 9, 9},
        now,
        now);
    jdbcTemplate.update(
        """
        insert into financial_account
          (id, user_id, origin, type, alias_enc, currency, status, created_at, updated_at, version)
        values (?, ?, 'MANUAL', 'CASH', ?, 'BRL', 'ACTIVE', ?, ?, 0)
        """,
        accountId,
        user.id(),
        cryptoService.encrypt(
            "Carteira rotacionada",
            new CryptoContext(user.id(), "financial_account", "alias", accountId)),
        now,
        now);
  }

  @Test
  void regravaCamposCifradosComNovaVersaoEPodeSerExecutadaNovamente() throws Exception {
    KeyRing configured = KeyRing.load(Path.of(testResourcePath("crypto-keys-test.json")), 1, 1);
    SecretKey dataV1 = configured.dataKey(1);
    SecretKey indexV1 = configured.indexKey(1);
    SecretKey dataV2 = randomKey();
    SecretKey indexV2 = randomKey();
    KeyRing rotatedRing =
        new KeyRing(Map.of(1, dataV1, 2, dataV2), 2, Map.of(1, indexV1, 2, indexV2), 2);
    CryptoService rotatedCrypto = new CryptoService(rotatedRing);
    KeyRotationJob job = new KeyRotationJob(jdbcTemplate, rotatedCrypto, clock);

    KeyRotationJob.RotationResult first = job.rotate();
    assertThat(first.targetVersion()).isEqualTo(2);
    assertThat(first.failed()).isZero();
    assertThat(first.rotated()).isGreaterThanOrEqualTo(2);

    byte[] categoryBytes =
        jdbcTemplate.queryForObject(
            "select name_enc from category where id = ?", byte[].class, categoryId);
    byte[] accountBytes =
        jdbcTemplate.queryForObject(
            "select alias_enc from financial_account where id = ?", byte[].class, accountId);
    assertThat(categoryBytes[1]).isEqualTo((byte) 2);
    assertThat(accountBytes[1]).isEqualTo((byte) 2);
    assertThat(
            rotatedCrypto.decryptString(
                categoryBytes, new CryptoContext(user.id(), "category", "name", categoryId)))
        .isEqualTo("Categoria rotacionada");
    assertThat(
            rotatedCrypto.decryptString(
                accountBytes,
                new CryptoContext(user.id(), "financial_account", "alias", accountId)))
        .isEqualTo("Carteira rotacionada");

    KeyRotationJob.RotationResult second = job.rotate();
    assertThat(second.rotated()).isZero();
    assertThat(second.failed()).isZero();
  }

  private static SecretKey randomKey() {
    byte[] material = new byte[32];
    new SecureRandom().nextBytes(material);
    return new SecretKeySpec(material, "AES");
  }
}
