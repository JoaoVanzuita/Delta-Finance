package com.deltafinance.investments.domain;

import java.time.LocalDate;

/** Dados de uma movimentação de investimento já cifrados e normalizados. */
public record ProviderInvestmentTransactionData(
    byte[] externalTransactionIdEnc,
    byte[] externalTransactionIdHash,
    String type,
    String movementType,
    LocalDate tradeDate,
    LocalDate transactionDate,
    byte[] quantityEnc,
    byte[] unitValueEnc,
    byte[] amountEnc,
    byte[] netAmountEnc,
    byte[] descriptionEnc,
    byte[] brokerageNumberEnc,
    byte[] expensesEnc) {}
