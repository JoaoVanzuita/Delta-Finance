package com.deltafinance.privacy.application;

import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.time.Clock;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exportação portátil dos dados ativos do titular.
 *
 * <p>Gera um ZIP em streaming com JSON Lines e CSV por conjunto de dados. Valores financeiros são
 * decifrados para o titular; segredos, credenciais, hashes de senha, tokens, chaves, HMACs e
 * identificadores externos não são exportados. Nenhum dado de outro usuário entra na consulta.
 */
@Service
public class ExportService {

  private static final int FORMAT_VERSION = 1;

  private final JdbcTemplate jdbcTemplate;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final Clock clock;
  private final JsonMapper jsonMapper = JsonMapper.shared();

  public ExportService(
      JdbcTemplate jdbcTemplate,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public ExportResult export(UUID userId, OutputStream output) throws IOException {
    auditService.record(
        userId,
        AuditEventType.EXPORT_REQUESTED,
        "app_user",
        userId,
        Map.of("format", "zip", "content", "jsonl+csv", "version", FORMAT_VERSION));

    ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
    Map<String, Integer> files = new LinkedHashMap<>();
    int totalRecords = 0;
    for (Dataset dataset : datasets()) {
      int count = writeJsonLines(zip, dataset, userId);
      writeCsv(zip, dataset, userId);
      files.put(dataset.name() + ".jsonl", count);
      files.put(dataset.name() + ".csv", count);
      totalRecords += count;
    }

    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("format", "delta-finance-export");
    manifest.put("version", FORMAT_VERSION);
    manifest.put("exportedAt", clock.now().toString());
    manifest.put("timezone", "America/Sao_Paulo");
    manifest.put("currency", "BRL");
    manifest.put("recordCount", totalRecords);
    manifest.put("files", files);
    manifest.put(
        "notes",
        List.of(
            "Dados ativos e históricos do titular da conta.",
            "Segredos, credenciais, API Keys, hashes de senha, tokens, chaves e HMACs não são exportados.",
            "Documentação do formato e política de retenção: docs/security.md."));
    zip.putNextEntry(new ZipEntry("manifest.json"));
    zip.write(jsonMapper.writeValueAsBytes(manifest));
    zip.closeEntry();
    zip.finish();

    return new ExportResult(totalRecords, files.size() + 1);
  }

  private List<Dataset> datasets() {
    return List.of(
        new Dataset(
            "profile",
            List.of("id", "username", "status", "deletionRequestedAt", "deletedAt", "createdAt"),
            "select id, username_display, status, deletion_requested_at, deleted_at, created_at"
                + " from app_user where id = ?",
            rs -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", uuid(rs, "id"));
              row.put("username", rs.getString("username_display"));
              row.put("status", rs.getString("status"));
              row.put("deletionRequestedAt", instant(rs, "deletion_requested_at"));
              row.put("deletedAt", instant(rs, "deleted_at"));
              row.put("createdAt", instant(rs, "created_at"));
              return row;
            }),
        new Dataset(
            "accounts",
            List.of(
                "id",
                "type",
                "subtype",
                "origin",
                "institutionName",
                "alias",
                "currency",
                "currentBalance",
                "balanceReferenceAt",
                "status",
                "lastSyncAt",
                "notes",
                "createdAt",
                "updatedAt"),
            "select id, user_id, type, subtype, origin, institution_name_enc, alias_enc, currency,"
                + " balance_enc, balance_reference_at, status, last_sync_at, notes_enc, created_at,"
                + " updated_at from financial_account where user_id = ? order by created_at, id",
            rs -> {
              UUID id = uuid(rs, "id");
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("type", rs.getString("type"));
              row.put("subtype", rs.getString("subtype"));
              row.put("origin", rs.getString("origin"));
              row.put(
                  "institutionName",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "financial_account",
                      "institution_name",
                      id,
                      rs.getBytes("institution_name_enc")));
              row.put(
                  "alias",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "financial_account",
                      "alias",
                      id,
                      rs.getBytes("alias_enc")));
              row.put("currency", rs.getString("currency"));
              row.put(
                  "currentBalance",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "financial_account",
                      "balance",
                      id,
                      rs.getBytes("balance_enc")));
              row.put("balanceReferenceAt", instant(rs, "balance_reference_at"));
              row.put("status", rs.getString("status"));
              row.put("lastSyncAt", instant(rs, "last_sync_at"));
              row.put(
                  "notes",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "financial_account",
                      "notes",
                      id,
                      rs.getBytes("notes_enc")));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "card_profiles",
            List.of(
                "id",
                "accountId",
                "closingDay",
                "dueDay",
                "creditLimit",
                "creditBalance",
                "source",
                "createdAt",
                "updatedAt"),
            "select id, user_id, account_id, closing_day, due_day, credit_limit_enc,"
                + " credit_balance_enc, source, created_at, updated_at from card_profile"
                + " where user_id = ?",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("accountId", uuid(rs, "account_id"));
              row.put("closingDay", rs.getInt("closing_day"));
              row.put("dueDay", rs.getInt("due_day"));
              row.put(
                  "creditLimit",
                  decrypt(
                      userId, "card_profile", "credit_limit", id, rs.getBytes("credit_limit_enc")));
              row.put(
                  "creditBalance",
                  decrypt(
                      userId,
                      "card_profile",
                      "credit_balance",
                      id,
                      rs.getBytes("credit_balance_enc")));
              row.put("source", rs.getString("source"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "investment_positions",
            List.of(
                "id",
                "broker",
                "assetName",
                "ticker",
                "quantity",
                "referencePrice",
                "referenceDate",
                "notes",
                "origin",
                "createdAt",
                "updatedAt"),
            "select id, user_id, broker, asset_name_enc, ticker_enc, quantity_enc,"
                + " reference_price_enc, reference_date, notes_enc, origin, created_at, updated_at"
                + " from investment_position where user_id = ? order by reference_date desc, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("broker", rs.getString("broker"));
              row.put(
                  "assetName",
                  decrypt(
                      userId,
                      "investment_position",
                      "asset_name",
                      id,
                      rs.getBytes("asset_name_enc")));
              row.put(
                  "ticker",
                  decrypt(userId, "investment_position", "ticker", id, rs.getBytes("ticker_enc")));
              row.put(
                  "quantity",
                  decrypt(
                      userId, "investment_position", "quantity", id, rs.getBytes("quantity_enc")));
              row.put(
                  "referencePrice",
                  decrypt(
                      userId,
                      "investment_position",
                      "reference_price",
                      id,
                      rs.getBytes("reference_price_enc")));
              row.put("referenceDate", date(rs, "reference_date"));
              row.put(
                  "notes",
                  decrypt(userId, "investment_position", "notes", id, rs.getBytes("notes_enc")));
              row.put("origin", rs.getString("origin"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "manual_asset_liabilities",
            List.of(
                "id",
                "kind",
                "type",
                "description",
                "referenceValue",
                "referenceDate",
                "notes",
                "createdAt",
                "updatedAt"),
            "select id, user_id, kind, type, description_enc, reference_value_enc,"
                + " reference_date, notes_enc, created_at, updated_at from manual_asset_liability"
                + " where user_id = ? order by reference_date desc, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("kind", rs.getString("kind"));
              row.put("type", rs.getString("type"));
              row.put(
                  "description",
                  decrypt(
                      userId,
                      "manual_asset_liability",
                      "description",
                      id,
                      rs.getBytes("description_enc")));
              row.put(
                  "referenceValue",
                  decrypt(
                      userId,
                      "manual_asset_liability",
                      "reference_value",
                      id,
                      rs.getBytes("reference_value_enc")));
              row.put("referenceDate", date(rs, "reference_date"));
              row.put(
                  "notes",
                  decrypt(userId, "manual_asset_liability", "notes", id, rs.getBytes("notes_enc")));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "financial_goals",
            List.of(
                "id",
                "name",
                "description",
                "notes",
                "type",
                "targetAmount",
                "targetDate",
                "status",
                "progressRule",
                "changedAt",
                "createdAt",
                "updatedAt"),
            "select id, user_id, name_enc, description_enc, notes_enc, type, target_amount_enc,"
                + " target_date, status, progress_rule, changed_at, created_at, updated_at"
                + " from financial_goal where user_id = ? order by created_at, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put(
                  "name", decrypt(userId, "financial_goal", "name", id, rs.getBytes("name_enc")));
              row.put(
                  "description",
                  decrypt(
                      userId, "financial_goal", "description", id, rs.getBytes("description_enc")));
              row.put(
                  "notes",
                  decrypt(userId, "financial_goal", "notes", id, rs.getBytes("notes_enc")));
              row.put("type", rs.getString("type"));
              row.put(
                  "targetAmount",
                  decrypt(
                      userId,
                      "financial_goal",
                      "target_amount",
                      id,
                      rs.getBytes("target_amount_enc")));
              row.put("targetDate", date(rs, "target_date"));
              row.put("status", rs.getString("status"));
              row.put("progressRule", rs.getString("progress_rule"));
              row.put("changedAt", instant(rs, "changed_at"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "goal_allocations",
            List.of(
                "id",
                "goalId",
                "ordinal",
                "sourceType",
                "sourceId",
                "allocationMode",
                "allocatedAmount",
                "allocationRatio",
                "effectiveFrom",
                "effectiveTo",
                "createdAt",
                "updatedAt"),
            "select id, user_id, goal_id, ordinal, source_type, source_id, allocation_mode,"
                + " allocated_amount_enc, allocation_ratio, effective_from, effective_to,"
                + " created_at, updated_at from goal_allocation where user_id = ?"
                + " order by goal_id, ordinal, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("goalId", uuid(rs, "goal_id"));
              row.put("ordinal", rs.getInt("ordinal"));
              row.put("sourceType", rs.getString("source_type"));
              row.put("sourceId", uuid(rs, "source_id"));
              row.put("allocationMode", rs.getString("allocation_mode"));
              row.put(
                  "allocatedAmount",
                  decrypt(
                      userId,
                      "goal_allocation",
                      "allocated_amount",
                      id,
                      rs.getBytes("allocated_amount_enc")));
              row.put("allocationRatio", rs.getBigDecimal("allocation_ratio"));
              row.put("effectiveFrom", date(rs, "effective_from"));
              row.put("effectiveTo", date(rs, "effective_to"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "goal_progress_snapshots",
            List.of(
                "id",
                "goalId",
                "referenceDate",
                "currentValue",
                "percentage",
                "remaining",
                "ruleVersion",
                "createdAt",
                "updatedAt"),
            "select id, user_id, goal_id, reference_date, current_value_enc, percentage,"
                + " remaining_enc, rule_version, created_at, updated_at from goal_progress_snapshot"
                + " where user_id = ? order by reference_date desc, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("goalId", uuid(rs, "goal_id"));
              row.put("referenceDate", date(rs, "reference_date"));
              row.put(
                  "currentValue",
                  decrypt(
                      userId,
                      "goal_progress_snapshot",
                      "current_value",
                      id,
                      rs.getBytes("current_value_enc")));
              row.put("percentage", rs.getBigDecimal("percentage"));
              row.put(
                  "remaining",
                  decrypt(
                      userId,
                      "goal_progress_snapshot",
                      "remaining",
                      id,
                      rs.getBytes("remaining_enc")));
              row.put("ruleVersion", rs.getString("rule_version"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "categories",
            List.of("id", "name", "kind", "predefinedKey", "active", "createdAt", "updatedAt"),
            "select id, user_id, name_enc, kind, predefined_key, active, created_at, updated_at"
                + " from category where user_id = ? order by created_at, id",
            rs -> {
              UUID id = uuid(rs, "id");
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put(
                  "name",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "category",
                      "name",
                      id,
                      rs.getBytes("name_enc")));
              row.put("kind", rs.getString("kind"));
              row.put("predefinedKey", rs.getString("predefined_key"));
              row.put("active", rs.getBoolean("active"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "transactions",
            List.of(
                "id",
                "accountId",
                "categoryId",
                "source",
                "type",
                "status",
                "postedOn",
                "sourcePostedOn",
                "currency",
                "amountSource",
                "amountEffective",
                "descriptionSource",
                "descriptionEffective",
                "relatedTransactionId",
                "invoiceId",
                "adjustmentReason",
                "notes",
                "editedFields",
                "reconciliationState",
                "installmentNumber",
                "totalInstallments",
                "createdAt",
                "updatedAt"),
            "select id, user_id, account_id, category_id, source, type, status, posted_on,"
                + " source_posted_on, currency, amount_source_enc, amount_effective_enc,"
                + " description_source_enc, description_effective_enc, related_transaction_id,"
                + " invoice_id, adjustment_reason_enc, notes_enc, edited_fields,"
                + " reconciliation_state, installment_number, total_installments, created_at,"
                + " updated_at from transaction"
                + " where user_id = ? order by posted_on, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("accountId", uuid(rs, "account_id"));
              row.put("categoryId", uuid(rs, "category_id"));
              row.put("source", rs.getString("source"));
              row.put("type", rs.getString("type"));
              row.put("status", rs.getString("status"));
              row.put("postedOn", date(rs, "posted_on"));
              row.put("sourcePostedOn", date(rs, "source_posted_on"));
              row.put("currency", rs.getString("currency"));
              row.put(
                  "amountSource",
                  decrypt(
                      userId,
                      "transaction",
                      "amount_source",
                      id,
                      rs.getBytes("amount_source_enc")));
              row.put(
                  "amountEffective",
                  decrypt(
                      userId,
                      "transaction",
                      "amount_effective",
                      id,
                      rs.getBytes("amount_effective_enc")));
              row.put(
                  "descriptionSource",
                  decrypt(
                      userId,
                      "transaction",
                      "description_source",
                      id,
                      rs.getBytes("description_source_enc")));
              row.put(
                  "descriptionEffective",
                  decrypt(
                      userId,
                      "transaction",
                      "description_effective",
                      id,
                      rs.getBytes("description_effective_enc")));
              row.put("relatedTransactionId", uuid(rs, "related_transaction_id"));
              row.put("invoiceId", uuid(rs, "invoice_id"));
              row.put(
                  "adjustmentReason",
                  decrypt(
                      userId,
                      "transaction",
                      "adjustment_reason",
                      id,
                      rs.getBytes("adjustment_reason_enc")));
              row.put(
                  "notes", decrypt(userId, "transaction", "notes", id, rs.getBytes("notes_enc")));
              row.put("editedFields", stringArray(rs, "edited_fields"));
              row.put("reconciliationState", rs.getString("reconciliation_state"));
              row.put("installmentNumber", rs.getObject("installment_number", Integer.class));
              row.put("totalInstallments", rs.getObject("total_installments", Integer.class));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "invoices",
            List.of(
                "id",
                "cardAccountId",
                "cycleStart",
                "cycleEnd",
                "closingDate",
                "dueDate",
                "status",
                "hasAdjustments",
                "adjustmentCount",
                "total",
                "paymentsTotal",
                "outstanding",
                "appliedCredit",
                "minimumPayment",
                "source",
                "createdAt",
                "updatedAt"),
            "select id, user_id, card_account_id, cycle_start, cycle_end, closing_date, due_date,"
                + " status, has_adjustments, adjustment_count, total_enc, payments_total_enc,"
                + " outstanding_enc, applied_credit_enc, minimum_payment_enc, source, created_at,"
                + " updated_at from invoice where user_id = ? order by cycle_start, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("cardAccountId", uuid(rs, "card_account_id"));
              row.put("cycleStart", date(rs, "cycle_start"));
              row.put("cycleEnd", date(rs, "cycle_end"));
              row.put("closingDate", date(rs, "closing_date"));
              row.put("dueDate", date(rs, "due_date"));
              row.put("status", rs.getString("status"));
              row.put("hasAdjustments", rs.getBoolean("has_adjustments"));
              row.put("adjustmentCount", rs.getInt("adjustment_count"));
              row.put("total", decrypt(userId, "invoice", "total", id, rs.getBytes("total_enc")));
              row.put(
                  "paymentsTotal",
                  decrypt(
                      userId, "invoice", "payments_total", id, rs.getBytes("payments_total_enc")));
              row.put(
                  "outstanding",
                  decrypt(userId, "invoice", "outstanding", id, rs.getBytes("outstanding_enc")));
              row.put(
                  "appliedCredit",
                  decrypt(
                      userId, "invoice", "applied_credit", id, rs.getBytes("applied_credit_enc")));
              row.put(
                  "minimumPayment",
                  decrypt(
                      userId,
                      "invoice",
                      "minimum_payment",
                      id,
                      rs.getBytes("minimum_payment_enc")));
              row.put("source", rs.getString("source"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "invoice_items",
            List.of(
                "id",
                "invoiceId",
                "transactionId",
                "kind",
                "reportingEffect",
                "source",
                "description",
                "amount",
                "occurredOn",
                "installmentNumber",
                "totalInstallments",
                "createdAt",
                "updatedAt"),
            "select id, user_id, invoice_id, transaction_id, kind, reporting_effect, source,"
                + " description_enc, amount_enc, occurred_on, installment_number,"
                + " total_installments, created_at, updated_at from invoice_item"
                + " where user_id = ? and removed_at is null order by occurred_on, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("invoiceId", uuid(rs, "invoice_id"));
              row.put("transactionId", uuid(rs, "transaction_id"));
              row.put("kind", rs.getString("kind"));
              row.put("reportingEffect", rs.getString("reporting_effect"));
              row.put("source", rs.getString("source"));
              row.put(
                  "description",
                  decrypt(
                      userId, "invoice_item", "description", id, rs.getBytes("description_enc")));
              row.put(
                  "amount",
                  decrypt(userId, "invoice_item", "amount", id, rs.getBytes("amount_enc")));
              row.put("occurredOn", date(rs, "occurred_on"));
              row.put("installmentNumber", rs.getObject("installment_number", Integer.class));
              row.put("totalInstallments", rs.getObject("total_installments", Integer.class));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "invoice_payments",
            List.of(
                "id",
                "invoiceId",
                "paymentAccountId",
                "amount",
                "paidOn",
                "status",
                "source",
                "createdAt",
                "updatedAt"),
            "select id, user_id, invoice_id, payment_account_id, amount_enc, paid_on, status,"
                + " source, created_at, updated_at from invoice_payment where user_id = ?"
                + " order by paid_on, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("invoiceId", uuid(rs, "invoice_id"));
              row.put("paymentAccountId", uuid(rs, "payment_account_id"));
              row.put(
                  "amount",
                  decrypt(userId, "invoice_payment", "amount", id, rs.getBytes("amount_enc")));
              row.put("paidOn", date(rs, "paid_on"));
              row.put("status", rs.getString("status"));
              row.put("source", rs.getString("source"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "invoice_item_corrections",
            List.of(
                "id", "invoiceItemId", "fieldName", "oldValue", "newValue", "reason", "changedAt"),
            "select id, user_id, invoice_item_id, field_name, old_value_enc, new_value_enc,"
                + " reason_enc, changed_at from invoice_item_correction where user_id = ?"
                + " order by changed_at, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("invoiceItemId", uuid(rs, "invoice_item_id"));
              row.put("fieldName", rs.getString("field_name"));
              row.put(
                  "oldValue",
                  decrypt(
                      userId,
                      "invoice_item_correction",
                      "old_value",
                      id,
                      rs.getBytes("old_value_enc")));
              row.put(
                  "newValue",
                  decrypt(
                      userId,
                      "invoice_item_correction",
                      "new_value",
                      id,
                      rs.getBytes("new_value_enc")));
              row.put(
                  "reason",
                  decrypt(
                      userId, "invoice_item_correction", "reason", id, rs.getBytes("reason_enc")));
              row.put("changedAt", instant(rs, "changed_at"));
              return row;
            }),
        new Dataset(
            "transaction_corrections",
            List.of("id", "transactionId", "fieldName", "oldValue", "newValue", "changedAt"),
            "select id, user_id, transaction_id, field_name, old_value_enc, new_value_enc,"
                + " changed_at from transaction_correction where user_id = ?"
                + " order by changed_at, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("transactionId", uuid(rs, "transaction_id"));
              row.put("fieldName", rs.getString("field_name"));
              row.put(
                  "oldValue",
                  decrypt(
                      userId,
                      "transaction_correction",
                      "old_value",
                      id,
                      rs.getBytes("old_value_enc")));
              row.put(
                  "newValue",
                  decrypt(
                      userId,
                      "transaction_correction",
                      "new_value",
                      id,
                      rs.getBytes("new_value_enc")));
              row.put("changedAt", instant(rs, "changed_at"));
              return row;
            }),
        new Dataset(
            "internal_matches",
            List.of(
                "id",
                "kind",
                "debitTransactionId",
                "creditTransactionId",
                "score",
                "criteriaVersion",
                "status",
                "reason",
                "decidedAt",
                "createdAt"),
            "select id, kind, debit_transaction_id, credit_transaction_id, score,"
                + " criteria_version, status, reason, decided_at, created_at from internal_match"
                + " where user_id = ? order by created_at, id",
            rs -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", uuid(rs, "id"));
              row.put("kind", rs.getString("kind"));
              row.put("debitTransactionId", uuid(rs, "debit_transaction_id"));
              row.put("creditTransactionId", uuid(rs, "credit_transaction_id"));
              row.put("score", rs.getBigDecimal("score").toPlainString());
              row.put("criteriaVersion", rs.getString("criteria_version"));
              row.put("status", rs.getString("status"));
              row.put("reason", rs.getString("reason"));
              row.put("decidedAt", instant(rs, "decided_at"));
              row.put("createdAt", instant(rs, "created_at"));
              return row;
            }),
        new Dataset(
            "integrations",
            List.of(
                "id",
                "provider",
                "status",
                "lastSyncAttemptAt",
                "lastSuccessfulSyncAt",
                "lastErrorCode",
                "createdAt",
                "updatedAt"),
            "select id, provider, status, last_sync_attempt_at, last_successful_sync_at,"
                + " last_error_code, created_at, updated_at from pluggy_integration"
                + " where user_id = ?",
            rs -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", uuid(rs, "id"));
              row.put("provider", rs.getString("provider"));
              row.put("status", rs.getString("status"));
              row.put("lastSyncAttemptAt", instant(rs, "last_sync_attempt_at"));
              row.put("lastSuccessfulSyncAt", instant(rs, "last_successful_sync_at"));
              row.put("lastErrorCode", rs.getString("last_error_code"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "connections",
            List.of(
                "id",
                "integrationId",
                "institutionName",
                "status",
                "consentExpiresAt",
                "nextAutoSyncAt",
                "lastSyncAt",
                "lastErrorCode",
                "createdAt",
                "updatedAt"),
            "select id, user_id, integration_id, institution_name_enc, status,"
                + " consent_expires_at, next_auto_sync_at, last_sync_at, last_error_code,"
                + " created_at, updated_at from pluggy_connection where user_id = ?",
            rs -> {
              UUID id = uuid(rs, "id");
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("integrationId", uuid(rs, "integration_id"));
              row.put(
                  "institutionName",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "pluggy_connection",
                      "institution_name",
                      id,
                      rs.getBytes("institution_name_enc")));
              row.put("status", rs.getString("status"));
              row.put("consentExpiresAt", instant(rs, "consent_expires_at"));
              row.put("nextAutoSyncAt", instant(rs, "next_auto_sync_at"));
              row.put("lastSyncAt", instant(rs, "last_sync_at"));
              row.put("lastErrorCode", rs.getString("last_error_code"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new Dataset(
            "sync_runs",
            List.of(
                "id",
                "integrationId",
                "connectionId",
                "status",
                "startedAt",
                "finishedAt",
                "readCount",
                "createdCount",
                "updatedCount",
                "skippedCount",
                "failedCount",
                "durationMs",
                "errorCode"),
            "select id, integration_id, connection_id, status, started_at, finished_at,"
                + " read_count, created_count, updated_count, skipped_count, failed_count,"
                + " duration_ms, error_code from sync_run where user_id = ?"
                + " order by started_at, id",
            rs -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", uuid(rs, "id"));
              row.put("integrationId", uuid(rs, "integration_id"));
              row.put("connectionId", uuid(rs, "connection_id"));
              row.put("status", rs.getString("status"));
              row.put("startedAt", instant(rs, "started_at"));
              row.put("finishedAt", instant(rs, "finished_at"));
              row.put("readCount", rs.getInt("read_count"));
              row.put("createdCount", rs.getInt("created_count"));
              row.put("updatedCount", rs.getInt("updated_count"));
              row.put("skippedCount", rs.getInt("skipped_count"));
              row.put("failedCount", rs.getInt("failed_count"));
              row.put("durationMs", rs.getObject("duration_ms", Long.class));
              row.put("errorCode", rs.getString("error_code"));
              return row;
            }),
        new Dataset(
            "sync_jobs",
            List.of(
                "id",
                "integrationId",
                "connectionId",
                "jobType",
                "status",
                "runAfter",
                "attempts",
                "maxAttempts",
                "lastErrorCode",
                "createdAt"),
            "select id, integration_id, connection_id, job_type, status, run_after, attempts,"
                + " max_attempts, last_error_code, created_at from sync_job where user_id = ?",
            rs -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", uuid(rs, "id"));
              row.put("integrationId", uuid(rs, "integration_id"));
              row.put("connectionId", uuid(rs, "connection_id"));
              row.put("jobType", rs.getString("job_type"));
              row.put("status", rs.getString("status"));
              row.put("runAfter", instant(rs, "run_after"));
              row.put("attempts", rs.getInt("attempts"));
              row.put("maxAttempts", rs.getInt("max_attempts"));
              row.put("lastErrorCode", rs.getString("last_error_code"));
              row.put("createdAt", instant(rs, "created_at"));
              return row;
            }),
        new Dataset(
            "audit_events",
            List.of("id", "eventType", "targetType", "targetId", "metadata", "createdAt"),
            "select id, event_type, target_type, target_id, metadata::text, created_at"
                + " from audit_event where user_id = ? order by created_at, id",
            rs -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", uuid(rs, "id"));
              row.put("eventType", rs.getString("event_type"));
              row.put("targetType", rs.getString("target_type"));
              row.put("targetId", uuid(rs, "target_id"));
              row.put("metadata", parseJson(rs.getString("metadata")));
              row.put("createdAt", instant(rs, "created_at"));
              return row;
            }));
  }

  private int writeJsonLines(ZipOutputStream zip, Dataset dataset, UUID userId) throws IOException {
    zip.putNextEntry(new ZipEntry(dataset.name() + ".jsonl"));
    int[] count = {0};
    try {
      jdbcTemplate.query(
          dataset.sql(),
          (RowCallbackHandler)
              rs -> {
                Map<String, Object> row = dataset.mapper().map(rs);
                try {
                  zip.write(jsonMapper.writeValueAsBytes(row));
                  zip.write('\n');
                } catch (IOException exception) {
                  throw new UncheckedIOException(exception);
                }
                count[0]++;
              },
          userId);
    } catch (UncheckedIOException exception) {
      throw exception.getCause();
    }
    zip.closeEntry();
    return count[0];
  }

  private void writeCsv(ZipOutputStream zip, Dataset dataset, UUID userId) throws IOException {
    zip.putNextEntry(new ZipEntry(dataset.name() + ".csv"));
    writeCsvLine(zip, dataset.columns());
    try {
      jdbcTemplate.query(
          dataset.sql(),
          (RowCallbackHandler)
              rs -> {
                Map<String, Object> row = dataset.mapper().map(rs);
                List<String> values = new ArrayList<>(dataset.columns().size());
                for (String column : dataset.columns()) {
                  values.add(csvValue(row.get(column)));
                }
                try {
                  writeCsvLine(zip, values);
                } catch (IOException exception) {
                  throw new UncheckedIOException(exception);
                }
              },
          userId);
    } catch (UncheckedIOException exception) {
      throw exception.getCause();
    }
    zip.closeEntry();
  }

  private static void writeCsvLine(ZipOutputStream zip, List<String> values) throws IOException {
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        zip.write(',');
      }
      String value = values.get(index) == null ? "" : values.get(index);
      boolean quote =
          value.contains(",")
              || value.contains("\"")
              || value.contains("\n")
              || value.contains("\r");
      if (quote) {
        zip.write('"');
        zip.write(value.replace("\"", "\"\"").getBytes(StandardCharsets.UTF_8));
        zip.write('"');
      } else {
        zip.write(value.getBytes(StandardCharsets.UTF_8));
      }
    }
    zip.write('\n');
  }

  private String csvValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof java.time.temporal.TemporalAccessor
        || value instanceof String
        || value instanceof Number
        || value instanceof Boolean) {
      return String.valueOf(value);
    }
    try {
      return jsonMapper.writeValueAsString(value);
    } catch (RuntimeException exception) {
      return "";
    }
  }

  private String decrypt(UUID userId, String table, String column, UUID recordId, byte[] value) {
    if (value == null) {
      return null;
    }
    return cryptoService.decryptString(value, new CryptoContext(userId, table, column, recordId));
  }

  private static UUID uuid(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, UUID.class);
  }

  private static Object instant(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant().toString();
  }

  private static String date(ResultSet rs, String column) throws SQLException {
    java.sql.Date value = rs.getDate(column);
    return value == null ? null : value.toLocalDate().toString();
  }

  private static List<String> stringArray(ResultSet rs, String column) throws SQLException {
    java.sql.Array array = rs.getArray(column);
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof Object[] values) {
      List<String> result = new ArrayList<>(values.length);
      for (Object value : values) {
        result.add(value == null ? null : value.toString());
      }
      return result;
    }
    return List.of();
  }

  private Object parseJson(String value) {
    if (value == null || value.isBlank()) {
      return Map.of();
    }
    try {
      JsonNode node = jsonMapper.readTree(value);
      return node;
    } catch (RuntimeException exception) {
      return value;
    }
  }

  private record Dataset(String name, List<String> columns, String sql, Mapper mapper) {}

  @FunctionalInterface
  private interface Mapper {
    Map<String, Object> map(ResultSet rs) throws SQLException;
  }

  /** Contagens sanitizadas para log e resposta; não contém dados financeiros. */
  public record ExportResult(int records, int files) {}
}
