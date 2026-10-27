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
  private UUID investmentId;
  private UUID manualItemId;
  private UUID goalId;
  private UUID allocationId;
  private UUID snapshotId;
  private UUID idempotencyId;

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
    investmentId = UUID.randomUUID();
    manualItemId = UUID.randomUUID();
    goalId = UUID.randomUUID();
    allocationId = UUID.randomUUID();
    snapshotId = UUID.randomUUID();
    idempotencyId = UUID.randomUUID();
    insertPostMvpRows(now);
  }

  private void insertPostMvpRows(Timestamp now) {
    jdbcTemplate.update(
        """
        insert into investment_position
          (id, user_id, broker, asset_name_enc, ticker_enc, quantity_enc, reference_price_enc,
           reference_date, notes_enc, origin, created_at, updated_at, version)
        values (?, ?, 'XP', ?, ?, ?, ?, '2026-10-01', ?, 'MANUAL', ?, ?, 0)
        """,
        investmentId,
        user.id(),
        encrypt(investmentId, "investment_position", "asset_name", "Posição rotacionada"),
        encrypt(investmentId, "investment_position", "ticker", "TESOURO"),
        encrypt(investmentId, "investment_position", "quantity", "1"),
        encrypt(investmentId, "investment_position", "reference_price", "500.00"),
        encrypt(investmentId, "investment_position", "notes", "Nota"),
        now,
        now);
    jdbcTemplate.update(
        """
        insert into manual_asset_liability
          (id, user_id, kind, type, description_enc, reference_value_enc, reference_date,
           notes_enc, created_at, updated_at, version)
        values (?, ?, 'ASSET', 'IMOVEL', ?, ?, '2026-10-01', ?, ?, ?, 0)
        """,
        manualItemId,
        user.id(),
        encrypt(manualItemId, "manual_asset_liability", "description", "Item rotacionado"),
        encrypt(manualItemId, "manual_asset_liability", "reference_value", "100000.00"),
        encrypt(manualItemId, "manual_asset_liability", "notes", "Nota"),
        now,
        now);
    jdbcTemplate.update(
        """
        insert into financial_goal
          (id, user_id, name_enc, description_enc, notes_enc, type, target_amount_enc,
           status, progress_rule, created_at, updated_at, version)
        values (?, ?, ?, ?, ?, 'TRAVEL', ?, 'ACTIVE', 'ALLOCATIONS', ?, ?, 0)
        """,
        goalId,
        user.id(),
        encrypt(goalId, "financial_goal", "name", "Meta rotacionada"),
        encrypt(goalId, "financial_goal", "description", "Descrição"),
        encrypt(goalId, "financial_goal", "notes", "Nota"),
        encrypt(goalId, "financial_goal", "target_amount", "1000.00"),
        now,
        now);
    jdbcTemplate.update(
        """
        insert into goal_allocation
          (id, user_id, goal_id, ordinal, source_type, allocation_mode, allocated_amount_enc,
           effective_from, created_at, updated_at, version)
        values (?, ?, ?, 0, 'MANUAL', 'FIXED_AMOUNT', ?, '2026-10-01', ?, ?, 0)
        """,
        allocationId,
        user.id(),
        goalId,
        encrypt(allocationId, "goal_allocation", "allocated_amount", "100.00"),
        now,
        now);
    jdbcTemplate.update(
        """
        insert into goal_progress_snapshot
          (id, user_id, goal_id, reference_date, current_value_enc, percentage, remaining_enc,
           rule_version, created_at, updated_at, version)
        values (?, ?, ?, '2026-10-01', ?, 10.00, ?, 'v1', ?, ?, 0)
        """,
        snapshotId,
        user.id(),
        goalId,
        encrypt(snapshotId, "goal_progress_snapshot", "current_value", "100.00"),
        encrypt(snapshotId, "goal_progress_snapshot", "remaining", "900.00"),
        now,
        now);
    jdbcTemplate.update(
        """
        insert into idempotency_key
          (id, user_id, key, method, path, request_hash, response_status, response_body_enc,
           created_at, expires_at)
        values (?, ?, 'idem-rotation-01', 'POST', '/api/v1/transactions', ?, 200, ?, ?, ?)
        """,
        idempotencyId,
        user.id(),
        new byte[] {4, 5, 6},
        encrypt(idempotencyId, "idempotency_key", "response_body", "{}"),
        now,
        Timestamp.from(now.toInstant().plusSeconds(86_400)));
  }

  private byte[] encrypt(UUID recordId, String table, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(user.id(), table, column, recordId));
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
    assertThat(first.rotated()).isGreaterThanOrEqualTo(18);

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
    assertRotated(
        investmentId,
        "investment_position",
        "asset_name",
        "ticker",
        "quantity",
        "reference_price",
        "notes");
    assertRotated(
        manualItemId, "manual_asset_liability", "description", "reference_value", "notes");
    assertRotated(goalId, "financial_goal", "name", "description", "notes", "target_amount");
    assertRotated(allocationId, "goal_allocation", "allocated_amount");
    assertRotated(snapshotId, "goal_progress_snapshot", "current_value", "remaining");
    assertRotated(idempotencyId, "idempotency_key", "response_body");

    KeyRotationJob.RotationResult second = job.rotate();
    assertThat(second.rotated()).isZero();
    assertThat(second.failed()).isZero();
  }

  private void assertRotated(UUID id, String table, String... columns) {
    for (String column : columns) {
      byte[] payload =
          jdbcTemplate.queryForObject(
              "select " + column + "_enc from " + table + " where id = ?", byte[].class, id);
      assertThat(payload[1]).as("%s.%s", table, column).isEqualTo((byte) 2);
    }
  }

  private static SecretKey randomKey() {
    byte[] material = new byte[32];
    new SecureRandom().nextBytes(material);
    return new SecretKeySpec(material, "AES");
  }
}
