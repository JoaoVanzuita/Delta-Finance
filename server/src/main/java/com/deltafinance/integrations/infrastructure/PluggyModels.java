package com.deltafinance.integrations.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import tools.jackson.databind.JsonNode;

/** Modelos normalizados da API Pluggy, independentes do formato bruto. */
public final class PluggyModels {

  private PluggyModels() {}

  public record PluggyItem(
      String externalId,
      String status,
      Instant consentExpiresAt,
      Instant lastUpdatedAt,
      Instant nextAutoSyncAt,
      String institutionName,
      String errorCode) {}

  public record PluggyAccount(
      String externalId,
      String type,
      String subtype,
      String name,
      BigDecimal balance,
      String currency,
      JsonNode creditData) {}

  public record PluggyTransaction(
      String externalId,
      String accountExternalId,
      String description,
      BigDecimal amount,
      LocalDate date,
      String status,
      String category,
      String type,
      String currency,
      JsonNode creditCardMetadata) {}

  public record PluggyTransactionPage(java.util.List<PluggyTransaction> items, String next) {}

  static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString(null);
  }

  static BigDecimal decimal(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (value.isNumber()) {
      return value.decimalValue();
    }
    String text = value.asString(null);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(text.trim());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  static Instant instant(JsonNode node, String field) {
    String text = text(node, field);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(text);
    } catch (RuntimeException exception) {
      try {
        return LocalDate.parse(text).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
      } catch (RuntimeException ignored) {
        return null;
      }
    }
  }

  static LocalDate date(JsonNode node, String field) {
    String text = text(node, field);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(text);
    } catch (RuntimeException exception) {
      try {
        return Instant.parse(text).atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
      } catch (RuntimeException ignored) {
        return null;
      }
    }
  }
}
