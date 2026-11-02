package com.deltafinance.accounts.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Saldo reservado pronto para leitura, com nome local e nome do provedor separados. */
public record ReservedBalanceView(
    UUID id,
    UUID accountId,
    String name,
    String providerName,
    boolean nameCustomized,
    String amount,
    String currency,
    LocalDate referenceDate,
    boolean stale,
    List<ReservedBalanceAmountView> amounts) {}
