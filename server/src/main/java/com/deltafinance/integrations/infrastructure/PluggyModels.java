package com.deltafinance.integrations.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
      String number,
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

  public record PluggyAccountPage(List<PluggyAccount> items, boolean complete) {}

  public record PluggyTransactionPage(
      List<PluggyTransaction> items, String next, boolean complete) {}

  public record PluggyBill(
      String externalId,
      LocalDate closingDate,
      LocalDate dueDate,
      BigDecimal totalAmount,
      BigDecimal minimumPaymentAmount,
      String currency,
      List<PluggyBillPayment> payments) {}

  /**
   * Pagamento oficial de uma Bill.
   *
   * <p>Pela semântica do provedor, os pagamentos da Bill N+1 liquidam a fatura N. O serviço de
   * conciliação aplica essa regra ao comparar com a fatura local.
   */
  public record PluggyBillPayment(
      String externalId,
      LocalDate paymentDate,
      BigDecimal amount,
      String paymentMode,
      String valueType,
      String currency) {}

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

  /**
   * Data civil de campos que a Pluggy envia à meia-noite UTC (ex.: faturas).
   *
   * <p>Converter o instante para o fuso local recuaria um dia; o prefixo ISO já representa a data
   * do calendário do provedor.
   */
  static LocalDate civilDate(JsonNode node, String field) {
    String text = text(node, field);
    if (text == null || text.isBlank()) {
      return null;
    }
    String datePart = text.length() >= 10 ? text.substring(0, 10) : text;
    try {
      return LocalDate.parse(datePart);
    } catch (RuntimeException exception) {
      return date(node, field);
    }
  }
}
