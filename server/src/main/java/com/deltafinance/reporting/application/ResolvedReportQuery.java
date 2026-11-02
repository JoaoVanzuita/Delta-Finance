package com.deltafinance.reporting.application;

import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.transactions.domain.TransactionSource;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Filtros de relatório já validados e enriquecidos com contas/categorias do usuário. */
record ResolvedReportQuery(
    LocalDate from,
    LocalDate to,
    LocalDate toExclusive,
    ReportPeriod period,
    Set<UUID> accountIds,
    UUID categoryId,
    TransactionType type,
    TransactionSource origin,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    String direction,
    List<AccountView> accounts,
    Map<UUID, String> categoryNames,
    Map<UUID, CategoryKind> categoryKinds,
    Set<UUID> transferCategoryIds,
    UUID outrosCategoryId,
    Map<UUID, UUID> connectionIdsByAccount) {}
