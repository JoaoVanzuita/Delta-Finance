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
  private UUID integrationId;
  private UUID connectionId;
  private UUID providerInvestmentId;
  private UUID providerTransactionId;
  private UUID providerSyncId;
  private UUID investmentIncomeId;

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
    integrationId = UUID.randomUUID();
    connectionId = UUID.randomUUID();
    providerInvestmentId = UUID.randomUUID();
    providerTransactionId = UUID.randomUUID();
    providerSyncId = UUID.randomUUID();
    investmentIncomeId = UUID.randomUUID();
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
    jdbcTemplate.update(
        """
        insert into pluggy_integration
          (id, user_id, provider, status, created_at, updated_at, version)
        values (?, ?, 'MEU_PLUGGY', 'ACTIVE', ?, ?, 0)
        """,
        integrationId,
        user.id(),
        now,
        now);
    jdbcTemplate.update(
        """
        insert into pluggy_connection
          (id, user_id, integration_id, status, created_at, updated_at, version)
        values (?, ?, ?, 'ACTIVE', ?, ?, 0)
        """,
        connectionId,
        user.id(),
        integrationId,
        now,
        now);
    jdbcTemplate.update(
        """
        insert into provider_investment
          (id, user_id, connection_id, external_investment_id_enc,
           external_investment_id_hash, name_enc, code_enc, isin_enc, owner_enc,
           institution_enc, issuer_enc, type, subtype, status, currency, quantity_enc,
           unit_value_enc, amount_enc, balance_enc, amount_original_enc, amount_profit_enc,
           amount_withdrawal_enc, taxes_enc, taxes2_enc, rate_enc, rate_type,
           fixed_annual_rate_enc, last_month_rate_enc, annual_rate_enc,
           last_twelve_months_rate_enc, reference_date, provider_updated_at, synced_at,
           manual_cost_basis_enc, user_asset_class, created_at, updated_at, version)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EQUITY', 'STOCK', 'ACTIVE', 'BRL',
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CDI', ?, ?, ?, ?, '2026-10-01', ?, ?, ?, 'FII',
                ?, ?, 0)
        """,
        providerInvestmentId,
        user.id(),
        connectionId,
        encrypt(providerInvestmentId, "provider_investment", "external_investment_id", "inv-1"),
        new byte[] {7, 7, 7},
        encrypt(providerInvestmentId, "provider_investment", "name", "FII rotacionado"),
        encrypt(providerInvestmentId, "provider_investment", "code", "AAAA11"),
        encrypt(providerInvestmentId, "provider_investment", "isin", "BRAAAA"),
        encrypt(providerInvestmentId, "provider_investment", "owner", "Titular"),
        encrypt(providerInvestmentId, "provider_investment", "institution", "Instituição"),
        encrypt(providerInvestmentId, "provider_investment", "issuer", "Emissor"),
        encrypt(providerInvestmentId, "provider_investment", "quantity", "10"),
        encrypt(providerInvestmentId, "provider_investment", "unit_value", "100.00"),
        encrypt(providerInvestmentId, "provider_investment", "amount", "1000.00"),
        encrypt(providerInvestmentId, "provider_investment", "balance", "1000.00"),
        encrypt(providerInvestmentId, "provider_investment", "amount_original", "900.00"),
        encrypt(providerInvestmentId, "provider_investment", "amount_profit", "100.00"),
        encrypt(providerInvestmentId, "provider_investment", "amount_withdrawal", "1000.00"),
        encrypt(providerInvestmentId, "provider_investment", "taxes", "0.00"),
        encrypt(providerInvestmentId, "provider_investment", "taxes2", "0.00"),
        encrypt(providerInvestmentId, "provider_investment", "rate", "100"),
        encrypt(providerInvestmentId, "provider_investment", "fixed_annual_rate", "10.00"),
        encrypt(providerInvestmentId, "provider_investment", "last_month_rate", "1.00"),
        encrypt(providerInvestmentId, "provider_investment", "annual_rate", "12.00"),
        encrypt(providerInvestmentId, "provider_investment", "last_twelve_months_rate", "12.00"),
        now,
        now,
        encrypt(providerInvestmentId, "provider_investment", "manual_cost_basis", "900.00"),
        now,
        now);
    jdbcTemplate.update(
        """
        insert into provider_investment_transaction
          (id, user_id, provider_investment_id, external_transaction_id_enc,
           external_transaction_id_hash, dedup_key, type, movement_type, trade_date,
           transaction_date, quantity_enc, unit_value_enc, amount_enc, net_amount_enc,
           description_enc, brokerage_number_enc, expenses_enc, last_seen_at,
           created_at, updated_at, version)
        values (?, ?, ?, ?, ?, ?, 'INTEREST', 'CREDIT', '2026-10-01', '2026-10-01',
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
        """,
        providerTransactionId,
        user.id(),
        providerInvestmentId,
        encrypt(
            providerTransactionId,
            "provider_investment_transaction",
            "external_transaction_id",
            "tx-1"),
        new byte[] {8, 8, 8},
        new byte[] {9, 9, 9},
        encrypt(providerTransactionId, "provider_investment_transaction", "quantity", "0"),
        encrypt(providerTransactionId, "provider_investment_transaction", "unit_value", "1.00"),
        encrypt(providerTransactionId, "provider_investment_transaction", "amount", "12.50"),
        encrypt(providerTransactionId, "provider_investment_transaction", "net_amount", "12.50"),
        encrypt(
            providerTransactionId, "provider_investment_transaction", "description", "Rendimento"),
        encrypt(
            providerTransactionId, "provider_investment_transaction", "brokerage_number", "123"),
        encrypt(providerTransactionId, "provider_investment_transaction", "expenses", "{}"),
        now,
        now,
        now);
    jdbcTemplate.update(
        """
        insert into investment_income
          (id, user_id, provider_investment_id, income_type, income_date, amount_enc,
           description_enc, source, created_at, updated_at, version)
        values (?, ?, ?, 'DIVIDEND', '2026-10-01', ?, ?, 'MANUAL', ?, ?, 0)
        """,
        investmentIncomeId,
        user.id(),
        providerInvestmentId,
        encrypt(investmentIncomeId, "investment_income", "amount", "10.00"),
        encrypt(investmentIncomeId, "investment_income", "description", "Provento rotacionado"),
        now,
        now);
    jdbcTemplate.update(
        """
        insert into provider_investment_sync
          (id, user_id, connection_id, scope, resource_count, available_resource_count,
           pending_resource_count, unavailable_resource_count, resources_collected_at,
           last_synced_at, created_at, updated_at, version)
        values (?, ?, ?, 'AVAILABLE', 1, 1, 0, 0, ?, ?, ?, ?, 0)
        """,
        providerSyncId,
        user.id(),
        connectionId,
        now,
        now,
        now,
        now);
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
    assertRotated(
        providerInvestmentId,
        "provider_investment",
        "external_investment_id",
        "name",
        "code",
        "isin",
        "owner",
        "institution",
        "issuer",
        "quantity",
        "unit_value",
        "amount",
        "balance",
        "amount_original",
        "amount_profit",
        "amount_withdrawal",
        "taxes",
        "taxes2",
        "rate",
        "fixed_annual_rate",
        "last_month_rate",
        "annual_rate",
        "last_twelve_months_rate",
        "manual_cost_basis");
    assertRotated(investmentIncomeId, "investment_income", "amount", "description");
    assertRotated(
        providerTransactionId,
        "provider_investment_transaction",
        "external_transaction_id",
        "quantity",
        "unit_value",
        "amount",
        "net_amount",
        "description",
        "brokerage_number",
        "expenses");

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
