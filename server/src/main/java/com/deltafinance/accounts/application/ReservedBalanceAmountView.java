package com.deltafinance.accounts.application;

/** Faixa de um saldo reservado, com a remuneração contratada quando informada. */
public record ReservedBalanceAmountView(
    String amount,
    String currency,
    String indexer,
    String rateType,
    String calculation,
    String ratePeriodicity,
    String preFixedRate,
    String postFixedIndexerPercentage,
    String indexerAdditionalInfo) {}
