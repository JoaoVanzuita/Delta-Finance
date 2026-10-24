package com.deltafinance.perf;

import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.crypto.KeyRing;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Semeador sintético do cenário de referência (T174/T198).
 *
 * <p>Executado por {@code deploy/scripts/perf-seed.sh} contra um banco de desenvolvimento. Cria um
 * usuário, uma conta manual, uma categoria e 10.000 transações com valores cifrados pelo key ring
 * real. Nunca use em produção.
 */
public final class PerfDataSeeder {

  private static final int DEFAULT_TRANSACTIONS = 10_000;

  private PerfDataSeeder() {}

  public static void main(String[] args) throws Exception {
    String url = required("DELTA_DB_URL");
    String dbUser = required("DELTA_DB_USER");
    String password = required("DELTA_DB_PASSWORD");
    String keyFile = required("DELTA_CRYPTO_KEY_FILE");
    String username =
        System.getenv()
            .getOrDefault("PERF_USER", "perf.dashboard")
            .toLowerCase(java.util.Locale.ROOT);
    String userPassword =
        System.getenv().getOrDefault("PERF_PASSWORD", "montanha verde silencio longo");
    int transactions =
        Integer.parseInt(
            System.getenv()
                .getOrDefault("PERF_TRANSACTIONS", String.valueOf(DEFAULT_TRANSACTIONS)));

    KeyRing keyRing = KeyRing.load(Path.of(keyFile), 1, 1);
    CryptoService crypto = new CryptoService(keyRing);
    IndexHmacService hmac = new IndexHmacService(keyRing);
    PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);

    try (Connection connection = DriverManager.getConnection(url, dbUser, password)) {
      connection.setAutoCommit(false);
      UUID userId = UUID.randomUUID();
      UUID accountId = UUID.randomUUID();
      UUID categoryId = UUID.randomUUID();
      Instant now = Instant.now();

      try (PreparedStatement delete =
          connection.prepareStatement(
              "delete from financial_account where user_id ="
                  + " (select id from app_user where username_normalized = ?)")) {
        delete.setString(1, username);
        delete.executeUpdate();
      }
      try (PreparedStatement delete =
          connection.prepareStatement("delete from app_user where username_normalized = ?")) {
        delete.setString(1, username);
        delete.executeUpdate();
      }

      try (PreparedStatement insert =
          connection.prepareStatement(
              """
              insert into app_user
                (id, username_normalized, username_display, password_hash, status,
                 created_at, updated_at, version)
              values (?, ?, ?, ?, 'ACTIVE', ?, ?, 0)
              """)) {
        insert.setObject(1, userId);
        insert.setString(2, username);
        insert.setString(3, username);
        insert.setString(4, encoder.encode(userPassword));
        insert.setTimestamp(5, Timestamp.from(now));
        insert.setTimestamp(6, Timestamp.from(now));
        insert.executeUpdate();
      }

      try (PreparedStatement insert =
          connection.prepareStatement(
              """
              insert into category
                (id, user_id, name_enc, normalized_name_hash, kind, active,
                 created_at, updated_at, version)
              values (?, ?, ?, ?, 'EXPENSE', true, ?, ?, 0)
              """)) {
        insert.setObject(1, categoryId);
        insert.setObject(2, userId);
        insert.setBytes(
            3,
            crypto.encrypt(
                "Categoria de performance",
                new CryptoContext(userId, "category", "name", categoryId)));
        insert.setBytes(4, hmac.hmac("perf:category", "categoria-performance"));
        insert.setTimestamp(5, Timestamp.from(now));
        insert.setTimestamp(6, Timestamp.from(now));
        insert.executeUpdate();
      }

      try (PreparedStatement insert =
          connection.prepareStatement(
              """
              insert into financial_account
                (id, user_id, origin, type, subtype, alias_enc, currency, balance_enc,
                 balance_reference_at, status, notes_enc, created_at, updated_at, version)
              values (?, ?, 'MANUAL', 'BANK', 'CHECKING_ACCOUNT', ?, 'BRL', ?, ?, 'ACTIVE', ?, ?, ?, 0)
              """)) {
        insert.setObject(1, accountId);
        insert.setObject(2, userId);
        insert.setBytes(
            3,
            crypto.encrypt(
                "Conta de performance",
                new CryptoContext(userId, "financial_account", "alias", accountId)));
        insert.setBytes(
            4,
            crypto.encrypt(
                "1000.00", new CryptoContext(userId, "financial_account", "balance", accountId)));
        insert.setTimestamp(5, Timestamp.from(now));
        insert.setBytes(
            6,
            crypto.encrypt(
                "Cenário sintético de performance",
                new CryptoContext(userId, "financial_account", "notes", accountId)));
        insert.setTimestamp(7, Timestamp.from(now));
        insert.setTimestamp(8, Timestamp.from(now));
        insert.executeUpdate();
      }

      seedTransactions(connection, crypto, userId, accountId, categoryId, transactions, now);
      connection.commit();
      System.out.printf(
          "Perf seed concluído: usuario=%s conta=%s transacoes=%d%n",
          username, accountId, transactions);
    }
  }

  private static void seedTransactions(
      Connection connection,
      CryptoService crypto,
      UUID userId,
      UUID accountId,
      UUID categoryId,
      int total,
      Instant now)
      throws SQLException {
    LocalDate start = LocalDate.of(2025, 1, 1);
    ZoneId zone = ZoneId.of("America/Sao_Paulo");
    boolean previousAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);

    try (PreparedStatement insert =
        connection.prepareStatement(
            """
            insert into transaction
              (id, user_id, account_id, category_id, source, type, status, posted_on,
               currency, amount_source_enc, amount_effective_enc, description_source_enc,
               description_effective_enc, created_at, updated_at, version)
            values (?, ?, ?, ?, 'MANUAL', ?, 'POSTED', ?, 'BRL', ?, ?, ?, ?, ?, ?, 0)
            """)) {
      for (int index = 0; index < total; index++) {
        UUID transactionId = UUID.randomUUID();
        boolean debit = index % 3 != 0;
        String amount = debit ? "-25.50" : "1500.00";
        LocalDate postedOn = start.plusDays(index % 640L);
        String description = (debit ? "Compra sintética " : "Receita sintética ") + index;
        insert.setObject(1, transactionId);
        insert.setObject(2, userId);
        insert.setObject(3, accountId);
        insert.setObject(4, categoryId);
        insert.setString(5, debit ? "DEBIT" : "CREDIT");
        insert.setDate(6, java.sql.Date.valueOf(postedOn));
        insert.setBytes(
            7,
            crypto.encrypt(
                amount, new CryptoContext(userId, "transaction", "amount_source", transactionId)));
        insert.setBytes(
            8,
            crypto.encrypt(
                amount,
                new CryptoContext(userId, "transaction", "amount_effective", transactionId)));
        insert.setBytes(
            9,
            crypto.encrypt(
                description,
                new CryptoContext(userId, "transaction", "description_source", transactionId)));
        insert.setBytes(
            10,
            crypto.encrypt(
                description,
                new CryptoContext(userId, "transaction", "description_effective", transactionId)));
        insert.setTimestamp(11, Timestamp.from(now));
        insert.setTimestamp(12, Timestamp.from(now.plusSeconds(index)));
        insert.addBatch();
        if ((index + 1) % 500 == 0) {
          insert.executeBatch();
          connection.commit();
        }
      }
      insert.executeBatch();
      connection.commit();
    } finally {
      connection.setAutoCommit(previousAutoCommit);
    }
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Variável de ambiente obrigatória: " + name);
    }
    return value;
  }
}
