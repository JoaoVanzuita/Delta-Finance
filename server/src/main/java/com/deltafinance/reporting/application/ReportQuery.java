package com.deltafinance.reporting.application;

import java.time.LocalDate;
import java.util.UUID;

/** Parâmetros brutos do dashboard e do drill-down, validados pelo serviço. */
public record ReportQuery(
    LocalDate from,
    LocalDate to,
    String period,
    UUID accountId,
    UUID categoryId,
    String type,
    String origin,
    String minAmount,
    String maxAmount) {}
