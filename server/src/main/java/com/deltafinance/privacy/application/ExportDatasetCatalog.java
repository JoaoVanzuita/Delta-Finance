package com.deltafinance.privacy.application;

import com.deltafinance.shared.crypto.EncryptedValueReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Catálogo declarativo dos dados do titular.
 *
 * <p>Cada dataset tem SQL escopado por usuário, colunas públicas estáveis e um mapper que decifra
 * apenas os campos necessários. Mantido separado do writer do ZIP para que inclusão de um dataset
 * não obrigue a mexer no streaming/CSV.
 */
@Component
class ExportDatasetCatalog {

  private final EncryptedValueReader encryptedValueReader;

  ExportDatasetCatalog(EncryptedValueReader encryptedValueReader) {
    this.encryptedValueReader = encryptedValueReader;
  }

  List<ExportDataset> datasets() {
    return List.of(
        new ExportDataset(
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
        new ExportDataset(
            "accounts",
            List.of(
                "id",
                "type",
                "subtype",
                "origin",
                "institutionName",
                "alias",
                "providerName",
                "displayName",
                "currency",
                "currentBalance",
                "balanceReferenceAt",
                "status",
                "lastSyncAt",
                "hasReservedBalance",
                "reservedBalanceCheckedAt",
                "reservedBalanceTreatment",
                "notes",
                "createdAt",
                "updatedAt"),
            "select id, user_id, type, subtype, origin, institution_name_enc, alias_enc,"
                + " display_name_enc, currency, balance_enc, balance_reference_at, status,"
                + " last_sync_at, has_reserved_balance, reserved_balance_checked_at,"
                + " reserved_balance_treatment, notes_enc, created_at, updated_at"
                + " from financial_account where user_id = ? order by created_at, id",
            rs -> {
              UUID id = uuid(rs, "id");
              String origin = rs.getString("origin");
              String alias =
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "financial_account",
                      "alias",
                      id,
                      rs.getBytes("alias_enc"));
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("type", rs.getString("type"));
              row.put("subtype", rs.getString("subtype"));
              row.put("origin", origin);
              row.put(
                  "institutionName",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "financial_account",
                      "institution_name",
                      id,
                      rs.getBytes("institution_name_enc")));
              row.put("alias", alias);
              row.put("providerName", "IMPORTED".equals(origin) ? alias : null);
              row.put(
                  "displayName",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "financial_account",
                      "display_name",
                      id,
                      rs.getBytes("display_name_enc")));
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
              row.put("hasReservedBalance", rs.getBoolean("has_reserved_balance"));
              row.put("reservedBalanceCheckedAt", instant(rs, "reserved_balance_checked_at"));
              row.put("reservedBalanceTreatment", rs.getString("reserved_balance_treatment"));
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
        new ExportDataset(
            "reserved_balances",
            List.of(
                "id",
                "accountId",
                "providerIdentification",
                "providerName",
                "displayName",
                "currency",
                "amount",
                "details",
                "referenceDate",
                "lastSeenAt",
                "removedAt",
                "createdAt",
                "updatedAt"),
            "select id, user_id, account_id, provider_identification_enc, provider_name_enc,"
                + " display_name_enc, currency, amount_enc, details_enc, reference_date,"
                + " last_seen_at, removed_at, created_at, updated_at from reserved_balance"
                + " where user_id = ? order by created_at, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("accountId", uuid(rs, "account_id"));
              row.put(
                  "providerIdentification",
                  decrypt(
                      userId,
                      "reserved_balance",
                      "provider_identification",
                      id,
                      rs.getBytes("provider_identification_enc")));
              row.put(
                  "providerName",
                  decrypt(
                      userId,
                      "reserved_balance",
                      "provider_name",
                      id,
                      rs.getBytes("provider_name_enc")));
              row.put(
                  "displayName",
                  decrypt(
                      userId,
                      "reserved_balance",
                      "display_name",
                      id,
                      rs.getBytes("display_name_enc")));
              row.put("currency", rs.getString("currency"));
              row.put(
                  "amount",
                  decrypt(userId, "reserved_balance", "amount", id, rs.getBytes("amount_enc")));
              row.put(
                  "details",
                  decrypt(userId, "reserved_balance", "details", id, rs.getBytes("details_enc")));
              row.put("referenceDate", date(rs, "reference_date"));
              row.put("lastSeenAt", instant(rs, "last_seen_at"));
              row.put("removedAt", instant(rs, "removed_at"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
            "card_profiles",
            List.of(
                "id",
                "accountId",
                "closingDay",
                "dueDay",
                "creditLimit",
                "creditBalance",
                "brand",
                "level",
                "last4",
                "availableCreditLimit",
                "source",
                "createdAt",
                "updatedAt"),
            "select id, user_id, account_id, closing_day, due_day, credit_limit_enc,"
                + " credit_balance_enc, brand, level, last4_enc, available_credit_limit_enc,"
                + " source, created_at, updated_at from card_profile where user_id = ?",
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
              row.put("brand", rs.getString("brand"));
              row.put("level", rs.getString("level"));
              row.put(
                  "last4", decrypt(userId, "card_profile", "last4", id, rs.getBytes("last4_enc")));
              row.put(
                  "availableCreditLimit",
                  decrypt(
                      userId,
                      "card_profile",
                      "available_credit_limit",
                      id,
                      rs.getBytes("available_credit_limit_enc")));
              row.put("source", rs.getString("source"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
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
        new ExportDataset(
            "wealth_snapshots",
            List.of(
                "id",
                "referenceDate",
                "capturedAt",
                "trigger",
                "completeness",
                "grossAssets",
                "liabilities",
                "netWorth",
                "coverage",
                "createdAt",
                "updatedAt"),
            "select id, user_id, reference_date, captured_at, capture_trigger, completeness,"
                + " gross_assets_enc, liabilities_enc, net_worth_enc, coverage, created_at,"
                + " updated_at from wealth_snapshot where user_id = ?"
                + " order by reference_date desc, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("referenceDate", date(rs, "reference_date"));
              row.put("capturedAt", instant(rs, "captured_at"));
              row.put("trigger", rs.getString("capture_trigger"));
              row.put("completeness", rs.getString("completeness"));
              row.put(
                  "grossAssets",
                  decrypt(
                      userId,
                      "wealth_snapshot",
                      "gross_assets",
                      id,
                      rs.getBytes("gross_assets_enc")));
              row.put(
                  "liabilities",
                  decrypt(
                      userId,
                      "wealth_snapshot",
                      "liabilities",
                      id,
                      rs.getBytes("liabilities_enc")));
              row.put(
                  "netWorth",
                  decrypt(
                      userId, "wealth_snapshot", "net_worth", id, rs.getBytes("net_worth_enc")));
              row.put("coverage", rs.getString("coverage"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
            "wealth_snapshot_items",
            List.of(
                "id",
                "snapshotId",
                "sourceType",
                "sourceId",
                "assetClass",
                "description",
                "value",
                "currency",
                "included",
                "exclusionReason",
                "referenceDate",
                "stale",
                "createdAt",
                "updatedAt"),
            "select id, snapshot_id, user_id, source_type, source_id, asset_class,"
                + " description_enc, value_enc, currency, included, exclusion_reason,"
                + " source_reference_date, stale, created_at, updated_at"
                + " from wealth_snapshot_item where user_id = ?"
                + " order by snapshot_id, source_type, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("snapshotId", uuid(rs, "snapshot_id"));
              row.put("sourceType", rs.getString("source_type"));
              row.put("sourceId", uuid(rs, "source_id"));
              row.put("assetClass", rs.getString("asset_class"));
              row.put(
                  "description",
                  decrypt(
                      userId,
                      "wealth_snapshot_item",
                      "description",
                      id,
                      rs.getBytes("description_enc")));
              row.put(
                  "value",
                  decrypt(userId, "wealth_snapshot_item", "value", id, rs.getBytes("value_enc")));
              row.put("currency", rs.getString("currency"));
              row.put("included", rs.getBoolean("included"));
              row.put("exclusionReason", rs.getString("exclusion_reason"));
              row.put("referenceDate", date(rs, "source_reference_date"));
              row.put("stale", rs.getBoolean("stale"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
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
                "providerBillMonth",
                "createdAt",
                "updatedAt"),
            "select id, user_id, account_id, category_id, source, type, status, posted_on,"
                + " source_posted_on, currency, amount_source_enc, amount_effective_enc,"
                + " description_source_enc, description_effective_enc, related_transaction_id,"
                + " invoice_id, adjustment_reason_enc, notes_enc, edited_fields,"
                + " reconciliation_state, installment_number, total_installments,"
                + " provider_bill_month, created_at, updated_at from transaction"
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
              row.put("providerBillMonth", date(rs, "provider_bill_month"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
            "provider_bills",
            List.of(
                "id",
                "cardAccountId",
                "closingDate",
                "dueDate",
                "total",
                "minimumPayment",
                "paymentsTotal",
                "currency",
                "syncedAt",
                "createdAt",
                "updatedAt"),
            "select id, user_id, card_account_id, closing_date, due_date, total_enc,"
                + " minimum_payment_enc, payments_total_enc, currency, synced_at, created_at,"
                + " updated_at from provider_bill where user_id = ? order by due_date, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("cardAccountId", uuid(rs, "card_account_id"));
              row.put("closingDate", date(rs, "closing_date"));
              row.put("dueDate", date(rs, "due_date"));
              row.put(
                  "total", decrypt(userId, "provider_bill", "total", id, rs.getBytes("total_enc")));
              row.put(
                  "minimumPayment",
                  decrypt(
                      userId,
                      "provider_bill",
                      "minimum_payment",
                      id,
                      rs.getBytes("minimum_payment_enc")));
              row.put(
                  "paymentsTotal",
                  decrypt(
                      userId,
                      "provider_bill",
                      "payments_total",
                      id,
                      rs.getBytes("payments_total_enc")));
              row.put("currency", rs.getString("currency"));
              row.put("syncedAt", instant(rs, "synced_at"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
            "provider_bill_payments",
            List.of(
                "id",
                "providerBillId",
                "paymentDate",
                "amount",
                "paymentMode",
                "valueType",
                "currency",
                "createdAt",
                "updatedAt"),
            "select id, user_id, provider_bill_id, payment_date, amount_enc, payment_mode,"
                + " value_type, currency, created_at, updated_at from provider_bill_payment"
                + " where user_id = ? order by payment_date, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("providerBillId", uuid(rs, "provider_bill_id"));
              row.put("paymentDate", date(rs, "payment_date"));
              row.put(
                  "amount",
                  decrypt(
                      userId, "provider_bill_payment", "amount", id, rs.getBytes("amount_enc")));
              row.put("paymentMode", rs.getString("payment_mode"));
              row.put("valueType", rs.getString("value_type"));
              row.put("currency", rs.getString("currency"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
            "provider_investments",
            List.of(
                "id",
                "connectionId",
                "name",
                "code",
                "isin",
                "owner",
                "institution",
                "issuer",
                "type",
                "subtype",
                "status",
                "currency",
                "quantity",
                "unitValue",
                "amount",
                "balance",
                "amountOriginal",
                "amountProfit",
                "amountWithdrawal",
                "taxes",
                "taxes2",
                "rate",
                "rateType",
                "fixedAnnualRate",
                "lastMonthRate",
                "annualRate",
                "lastTwelveMonthsRate",
                "referenceDate",
                "purchaseDate",
                "dueDate",
                "gracePeriodDate",
                "providerUpdatedAt",
                "syncedAt",
                "unavailableAt",
                "manualCostBasis",
                "manualCostBasisUpdatedAt",
                "userAssetClass",
                "createdAt",
                "updatedAt"),
            "select id, user_id, connection_id, name_enc, code_enc,"
                + " isin_enc, owner_enc, institution_enc, issuer_enc, type, subtype, status,"
                + " currency, quantity_enc, unit_value_enc, amount_enc, balance_enc,"
                + " amount_original_enc, amount_profit_enc, amount_withdrawal_enc, taxes_enc,"
                + " taxes2_enc, rate_enc, rate_type, fixed_annual_rate_enc, last_month_rate_enc,"
                + " annual_rate_enc, last_twelve_months_rate_enc, reference_date, purchase_date,"
                + " due_date, grace_period_date, provider_updated_at, synced_at, unavailable_at,"
                + " manual_cost_basis_enc, manual_cost_basis_updated_at, user_asset_class,"
                + " created_at, updated_at from provider_investment where user_id = ?"
                + " order by status, type, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("connectionId", uuid(rs, "connection_id"));
              row.put(
                  "name",
                  decrypt(userId, "provider_investment", "name", id, rs.getBytes("name_enc")));
              row.put(
                  "code",
                  decrypt(userId, "provider_investment", "code", id, rs.getBytes("code_enc")));
              row.put(
                  "isin",
                  decrypt(userId, "provider_investment", "isin", id, rs.getBytes("isin_enc")));
              row.put(
                  "owner",
                  decrypt(userId, "provider_investment", "owner", id, rs.getBytes("owner_enc")));
              row.put(
                  "institution",
                  decrypt(
                      userId,
                      "provider_investment",
                      "institution",
                      id,
                      rs.getBytes("institution_enc")));
              row.put(
                  "issuer",
                  decrypt(userId, "provider_investment", "issuer", id, rs.getBytes("issuer_enc")));
              row.put("type", rs.getString("type"));
              row.put("subtype", rs.getString("subtype"));
              row.put("status", rs.getString("status"));
              row.put("currency", rs.getString("currency"));
              row.put(
                  "quantity",
                  decrypt(
                      userId, "provider_investment", "quantity", id, rs.getBytes("quantity_enc")));
              row.put(
                  "unitValue",
                  decrypt(
                      userId,
                      "provider_investment",
                      "unit_value",
                      id,
                      rs.getBytes("unit_value_enc")));
              row.put(
                  "amount",
                  decrypt(userId, "provider_investment", "amount", id, rs.getBytes("amount_enc")));
              row.put(
                  "balance",
                  decrypt(
                      userId, "provider_investment", "balance", id, rs.getBytes("balance_enc")));
              row.put(
                  "amountOriginal",
                  decrypt(
                      userId,
                      "provider_investment",
                      "amount_original",
                      id,
                      rs.getBytes("amount_original_enc")));
              row.put(
                  "amountProfit",
                  decrypt(
                      userId,
                      "provider_investment",
                      "amount_profit",
                      id,
                      rs.getBytes("amount_profit_enc")));
              row.put(
                  "amountWithdrawal",
                  decrypt(
                      userId,
                      "provider_investment",
                      "amount_withdrawal",
                      id,
                      rs.getBytes("amount_withdrawal_enc")));
              row.put(
                  "taxes",
                  decrypt(userId, "provider_investment", "taxes", id, rs.getBytes("taxes_enc")));
              row.put(
                  "taxes2",
                  decrypt(userId, "provider_investment", "taxes2", id, rs.getBytes("taxes2_enc")));
              row.put(
                  "rate",
                  decrypt(userId, "provider_investment", "rate", id, rs.getBytes("rate_enc")));
              row.put("rateType", rs.getString("rate_type"));
              row.put(
                  "fixedAnnualRate",
                  decrypt(
                      userId,
                      "provider_investment",
                      "fixed_annual_rate",
                      id,
                      rs.getBytes("fixed_annual_rate_enc")));
              row.put(
                  "lastMonthRate",
                  decrypt(
                      userId,
                      "provider_investment",
                      "last_month_rate",
                      id,
                      rs.getBytes("last_month_rate_enc")));
              row.put(
                  "annualRate",
                  decrypt(
                      userId,
                      "provider_investment",
                      "annual_rate",
                      id,
                      rs.getBytes("annual_rate_enc")));
              row.put(
                  "lastTwelveMonthsRate",
                  decrypt(
                      userId,
                      "provider_investment",
                      "last_twelve_months_rate",
                      id,
                      rs.getBytes("last_twelve_months_rate_enc")));
              row.put("referenceDate", date(rs, "reference_date"));
              row.put("purchaseDate", date(rs, "purchase_date"));
              row.put("dueDate", date(rs, "due_date"));
              row.put("gracePeriodDate", date(rs, "grace_period_date"));
              row.put("providerUpdatedAt", instant(rs, "provider_updated_at"));
              row.put("syncedAt", instant(rs, "synced_at"));
              row.put("unavailableAt", instant(rs, "unavailable_at"));
              row.put(
                  "manualCostBasis",
                  decrypt(
                      userId,
                      "provider_investment",
                      "manual_cost_basis",
                      id,
                      rs.getBytes("manual_cost_basis_enc")));
              row.put("manualCostBasisUpdatedAt", instant(rs, "manual_cost_basis_updated_at"));
              row.put("userAssetClass", rs.getString("user_asset_class"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
            "provider_investment_transactions",
            List.of(
                "id",
                "providerInvestmentId",
                "type",
                "movementType",
                "tradeDate",
                "transactionDate",
                "quantity",
                "unitValue",
                "amount",
                "netAmount",
                "description",
                "brokerageNumber",
                "expenses",
                "lastSeenAt",
                "removedAt",
                "createdAt",
                "updatedAt"),
            "select id, user_id, provider_investment_id, type,"
                + " movement_type, trade_date, transaction_date, quantity_enc, unit_value_enc,"
                + " amount_enc, net_amount_enc, description_enc, brokerage_number_enc,"
                + " expenses_enc, last_seen_at, removed_at, created_at, updated_at"
                + " from provider_investment_transaction where user_id = ?"
                + " order by transaction_date desc, created_at desc, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("providerInvestmentId", uuid(rs, "provider_investment_id"));
              row.put("type", rs.getString("type"));
              row.put("movementType", rs.getString("movement_type"));
              row.put("tradeDate", date(rs, "trade_date"));
              row.put("transactionDate", date(rs, "transaction_date"));
              row.put(
                  "quantity",
                  decrypt(
                      userId,
                      "provider_investment_transaction",
                      "quantity",
                      id,
                      rs.getBytes("quantity_enc")));
              row.put(
                  "unitValue",
                  decrypt(
                      userId,
                      "provider_investment_transaction",
                      "unit_value",
                      id,
                      rs.getBytes("unit_value_enc")));
              row.put(
                  "amount",
                  decrypt(
                      userId,
                      "provider_investment_transaction",
                      "amount",
                      id,
                      rs.getBytes("amount_enc")));
              row.put(
                  "netAmount",
                  decrypt(
                      userId,
                      "provider_investment_transaction",
                      "net_amount",
                      id,
                      rs.getBytes("net_amount_enc")));
              row.put(
                  "description",
                  decrypt(
                      userId,
                      "provider_investment_transaction",
                      "description",
                      id,
                      rs.getBytes("description_enc")));
              row.put(
                  "brokerageNumber",
                  decrypt(
                      userId,
                      "provider_investment_transaction",
                      "brokerage_number",
                      id,
                      rs.getBytes("brokerage_number_enc")));
              row.put(
                  "expenses",
                  decrypt(
                      userId,
                      "provider_investment_transaction",
                      "expenses",
                      id,
                      rs.getBytes("expenses_enc")));
              row.put("lastSeenAt", instant(rs, "last_seen_at"));
              row.put("removedAt", instant(rs, "removed_at"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
            "provider_investment_syncs",
            List.of(
                "id",
                "connectionId",
                "scope",
                "resourceCount",
                "availableResourceCount",
                "pendingResourceCount",
                "unavailableResourceCount",
                "resourcesCollectedAt",
                "lastSyncedAt",
                "errorCode",
                "createdAt",
                "updatedAt"),
            "select id, user_id, connection_id, scope, resource_count,"
                + " available_resource_count, pending_resource_count, unavailable_resource_count,"
                + " resources_collected_at, last_synced_at, error_code, created_at, updated_at"
                + " from provider_investment_sync where user_id = ? order by last_synced_at desc, id",
            rs -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", uuid(rs, "id"));
              row.put("connectionId", uuid(rs, "connection_id"));
              row.put("scope", rs.getString("scope"));
              row.put("resourceCount", rs.getInt("resource_count"));
              row.put("availableResourceCount", rs.getInt("available_resource_count"));
              row.put("pendingResourceCount", rs.getInt("pending_resource_count"));
              row.put("unavailableResourceCount", rs.getInt("unavailable_resource_count"));
              row.put("resourcesCollectedAt", instant(rs, "resources_collected_at"));
              row.put("lastSyncedAt", instant(rs, "last_synced_at"));
              row.put("errorCode", rs.getString("error_code"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
            "investment_incomes",
            List.of(
                "id",
                "providerInvestmentId",
                "incomeType",
                "incomeDate",
                "amount",
                "description",
                "source",
                "createdAt",
                "updatedAt"),
            "select id, user_id, provider_investment_id, income_type,"
                + " income_date, amount_enc, description_enc, source, created_at, updated_at"
                + " from investment_income where user_id = ? order by income_date desc, id",
            rs -> {
              UUID id = uuid(rs, "id");
              UUID userId = rs.getObject("user_id", UUID.class);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", id);
              row.put("providerInvestmentId", uuid(rs, "provider_investment_id"));
              row.put("incomeType", rs.getString("income_type"));
              row.put("incomeDate", date(rs, "income_date"));
              row.put(
                  "amount",
                  decrypt(userId, "investment_income", "amount", id, rs.getBytes("amount_enc")));
              row.put(
                  "description",
                  decrypt(
                      userId,
                      "investment_income",
                      "description",
                      id,
                      rs.getBytes("description_enc")));
              row.put("source", rs.getString("source"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
            "connections",
            List.of(
                "id",
                "integrationId",
                "institutionName",
                "displayName",
                "status",
                "consentExpiresAt",
                "nextAutoSyncAt",
                "lastSyncAt",
                "lastErrorCode",
                "reservedBalanceTreatment",
                "createdAt",
                "updatedAt"),
            "select id, user_id, integration_id, institution_name_enc, display_name_enc, status,"
                + " consent_expires_at, next_auto_sync_at, last_sync_at, last_error_code,"
                + " reserved_balance_treatment, created_at, updated_at from pluggy_connection"
                + " where user_id = ?",
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
              row.put(
                  "displayName",
                  decrypt(
                      rs.getObject("user_id", UUID.class),
                      "pluggy_connection",
                      "display_name",
                      id,
                      rs.getBytes("display_name_enc")));
              row.put("status", rs.getString("status"));
              row.put("consentExpiresAt", instant(rs, "consent_expires_at"));
              row.put("nextAutoSyncAt", instant(rs, "next_auto_sync_at"));
              row.put("lastSyncAt", instant(rs, "last_sync_at"));
              row.put("lastErrorCode", rs.getString("last_error_code"));
              row.put("reservedBalanceTreatment", rs.getString("reserved_balance_treatment"));
              row.put("createdAt", instant(rs, "created_at"));
              row.put("updatedAt", instant(rs, "updated_at"));
              return row;
            }),
        new ExportDataset(
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
        new ExportDataset(
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
        new ExportDataset(
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

  private String decrypt(UUID userId, String table, String column, UUID recordId, byte[] value) {
    return encryptedValueReader.read(userId, table, column, recordId, value);
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
      JsonNode node = JsonMapper.shared().readTree(value);
      return node;
    } catch (RuntimeException exception) {
      return value;
    }
  }
}
