package com.deltafinance.reporting.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Parâmetros brutos do dashboard e do drill-down, validados pelo serviço. */
public record ReportQuery(
    LocalDate from,
    LocalDate to,
    String period,
    List<UUID> accountIds,
    UUID categoryId,
    String type,
    String origin,
    String minAmount,
    String maxAmount) {}
