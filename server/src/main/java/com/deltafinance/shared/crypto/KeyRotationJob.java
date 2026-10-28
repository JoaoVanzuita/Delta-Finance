package com.deltafinance.shared.crypto;

import com.deltafinance.shared.time.Clock;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rotação online da chave de dados (T176).
 *
 * <p>Percorre todos os campos cifrados conhecidos e regrava com a versão ativa do key ring. O
 * ciphertext carrega a versão da chave no segundo byte, portanto a consulta é incremental e pode
 * ser interrompida sem perda: cada linha é regravada individualmente.
 *
 * <p>Pré-requisitos operacionais:
 *
 * <ul>
 *   <li>adicionar a nova chave ao key ring e reiniciar com {@code delta.crypto.active-version=N};
 *   <li>manter a chave antiga no key ring até a rotação terminar;
 *   <li>executar o job uma vez por versão; ele é idempotente.
 * </ul>
 *
 * <p>A rotação da chave de índice HMAC é diferente: exige reindexação e está documentada em {@code
 * docs/runbooks/key-rotation.md}; este job não altera HMACs.
 */
@Component
public class KeyRotationJob {

  private static final Logger LOG = LoggerFactory.getLogger(KeyRotationJob.class);
  private static final int BATCH_SIZE = 100;

  /** Campos cifrados e o nome lógico usado no AAD. */
  private static final List<Field> FIELDS =
      List.of(
          new Field("pluggy_integration", "client_id"),
          new Field("pluggy_integration", "client_secret"),
          new Field("pluggy_connection", "external_item_id"),
          new Field("pluggy_connection", "institution_name"),
          new Field("category", "name"),
          new Field("financial_account", "external_account_id"),
          new Field("financial_account", "institution_name"),
          new Field("financial_account", "alias"),
          new Field("financial_account", "balance"),
          new Field("financial_account", "notes"),
          new Field("card_profile", "credit_limit"),
          new Field("card_profile", "credit_balance"),
          new Field("card_profile", "last4"),
          new Field("card_profile", "available_credit_limit"),
          new Field("transaction", "amount_source"),
          new Field("transaction", "amount_effective"),
          new Field("transaction", "description_source"),
          new Field("transaction", "description_effective"),
          new Field("transaction", "external_transaction_id"),
          new Field("transaction", "adjustment_reason"),
          new Field("transaction", "notes"),
          new Field("external_identity_history", "external_id"),
          new Field("transaction_correction", "old_value"),
          new Field("transaction_correction", "new_value"),
          new Field("invoice", "total"),
          new Field("invoice", "payments_total"),
          new Field("invoice", "outstanding"),
          new Field("invoice", "applied_credit"),
          new Field("invoice", "minimum_payment"),
          new Field("invoice_item", "description"),
          new Field("invoice_item", "amount"),
          new Field("invoice_payment", "amount"),
          new Field("provider_bill", "total"),
          new Field("provider_bill", "minimum_payment"),
          new Field("provider_bill", "payments_total"),
          new Field("provider_bill_payment", "amount"),
          new Field("provider_investment", "external_investment_id"),
          new Field("provider_investment", "name"),
          new Field("provider_investment", "code"),
          new Field("provider_investment", "isin"),
          new Field("provider_investment", "owner"),
          new Field("provider_investment", "institution"),
          new Field("provider_investment", "issuer"),
          new Field("provider_investment", "quantity"),
          new Field("provider_investment", "unit_value"),
          new Field("provider_investment", "amount"),
          new Field("provider_investment", "balance"),
          new Field("provider_investment", "amount_original"),
          new Field("provider_investment", "amount_profit"),
          new Field("provider_investment", "amount_withdrawal"),
          new Field("provider_investment", "taxes"),
          new Field("provider_investment", "taxes2"),
          new Field("provider_investment", "rate"),
          new Field("provider_investment", "fixed_annual_rate"),
          new Field("provider_investment", "last_month_rate"),
          new Field("provider_investment", "annual_rate"),
          new Field("provider_investment", "last_twelve_months_rate"),
          new Field("provider_investment", "manual_cost_basis"),
          new Field("provider_investment_transaction", "external_transaction_id"),
          new Field("provider_investment_transaction", "quantity"),
          new Field("provider_investment_transaction", "unit_value"),
          new Field("provider_investment_transaction", "amount"),
          new Field("provider_investment_transaction", "net_amount"),
          new Field("provider_investment_transaction", "description"),
          new Field("provider_investment_transaction", "brokerage_number"),
          new Field("provider_investment_transaction", "expenses"),
          new Field("investment_income", "amount"),
          new Field("investment_income", "description"),
          new Field("invoice_item_correction", "old_value"),
          new Field("invoice_item_correction", "new_value"),
          new Field("invoice_item_correction", "reason"),
          new Field("investment_position", "asset_name"),
          new Field("investment_position", "ticker"),
          new Field("investment_position", "quantity"),
          new Field("investment_position", "reference_price"),
          new Field("investment_position", "notes"),
          new Field("manual_asset_liability", "description"),
          new Field("manual_asset_liability", "reference_value"),
          new Field("manual_asset_liability", "notes"),
          new Field("financial_goal", "name"),
          new Field("financial_goal", "description"),
          new Field("financial_goal", "notes"),
          new Field("financial_goal", "target_amount"),
          new Field("goal_allocation", "allocated_amount"),
          new Field("goal_progress_snapshot", "current_value"),
          new Field("goal_progress_snapshot", "remaining"),
          // Respostas de replay têm TTL de 24 h, mas ainda podem existir durante a rotação.
          new Field("idempotency_key", "response_body", false));

  private final JdbcTemplate jdbcTemplate;
  private final CryptoService cryptoService;
  private final Clock clock;

  public KeyRotationJob(JdbcTemplate jdbcTemplate, CryptoService cryptoService, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  /**
   * Regrava os campos de todas as tabelas com a versão ativa. Devolve contagens sanitizadas.
   *
   * <p>Não abre uma única transação gigante: cada lote é atômico. Interromper no meio deixa o banco
   * consistente (parte na versão antiga, parte na nova) e a próxima execução continua de onde
   * parou.
   */
  public RotationResult rotate() {
    int targetVersion = cryptoService.activeKeyVersion();
    int rotated = 0;
    int failed = 0;
    for (Field field : FIELDS) {
      RotationResult partial = rotateField(field, targetVersion);
      rotated += partial.rotated();
      failed += partial.failed();
    }
    RotationResult result = new RotationResult(targetVersion, rotated, failed);
    LOG.info(
        "Rotação de chave concluída: targetVersion={} rotated={} failed={}",
        result.targetVersion(),
        result.rotated(),
        result.failed());
    return result;
  }

  private RotationResult rotateField(Field field, int targetVersion) {
    String column = field.column() + "_enc";
    String select =
        "select id, user_id, "
            + column
            + " from "
            + field.table()
            + " where "
            + column
            + " is not null and get_byte("
            + column
            + ", 1) < ? and id > ? order by id limit ?";
    int rotated = 0;
    int failed = 0;
    UUID lastId = new UUID(0L, 0L);
    while (true) {
      List<Row> rows =
          jdbcTemplate.query(
              select,
              (rs, rowNum) ->
                  new Row(
                      rs.getObject("id", UUID.class),
                      rs.getObject("user_id", UUID.class),
                      rs.getBytes(column)),
              targetVersion,
              lastId,
              BATCH_SIZE);
      if (rows.isEmpty()) {
        break;
      }
      for (Row row : rows) {
        lastId = row.id();
        try {
          CryptoContext context =
              new CryptoContext(row.userId(), field.table(), field.column(), row.id());
          String plaintext = cryptoService.decryptString(row.payload(), context);
          byte[] reencrypted = cryptoService.encrypt(plaintext, context);
          String update =
              "update "
                  + field.table()
                  + " set "
                  + column
                  + " = ?"
                  + (field.hasUpdatedAt() ? ", updated_at = ?" : "")
                  + " where id = ? and user_id = ?";
          if (field.hasUpdatedAt()) {
            jdbcTemplate.update(
                update, reencrypted, Timestamp.from(clock.now()), row.id(), row.userId());
          } else {
            jdbcTemplate.update(update, reencrypted, row.id(), row.userId());
          }
          rotated++;
        } catch (RuntimeException exception) {
          failed++;
          LOG.error(
              "Falha ao rotacionar campo: table={} column={} record={} error={}",
              field.table(),
              field.column(),
              row.id(),
              exception.getClass().getSimpleName());
        }
      }
    }
    return new RotationResult(targetVersion, rotated, failed);
  }

  private record Field(String table, String column, boolean hasUpdatedAt) {
    private Field(String table, String column) {
      this(table, column, true);
    }

    private Field {
      if (!table.matches("[a-z0-9_]{1,64}") || !column.matches("[a-z0-9_]{1,64}")) {
        throw new IllegalArgumentException("Identificador inválido para rotação de chave");
      }
    }
  }

  private record Row(UUID id, UUID userId, byte[] payload) {}

  /** Resultado sanitizado da rotação. */
  public record RotationResult(int targetVersion, int rotated, int failed) {}
}
