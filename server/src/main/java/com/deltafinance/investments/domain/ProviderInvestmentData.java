package com.deltafinance.investments.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Dados de uma posição importada já cifrados e normalizados.
 *
 * <p>O record mantém a entidade independente do formato bruto do provedor. Campos opcionais podem
 * ser nulos; ausência é exibida ao usuário, nunca estimada.
 */
public record ProviderInvestmentData(
    byte[] externalInvestmentIdEnc,
    byte[] nameEnc,
    byte[] codeEnc,
    byte[] isinEnc,
    byte[] ownerEnc,
    byte[] institutionEnc,
    byte[] issuerEnc,
    String type,
    String subtype,
    String status,
    String currency,
    byte[] quantityEnc,
    byte[] unitValueEnc,
    byte[] amountEnc,
    byte[] balanceEnc,
    byte[] amountOriginalEnc,
    byte[] amountProfitEnc,
    byte[] amountWithdrawalEnc,
    byte[] taxesEnc,
    byte[] taxes2Enc,
    byte[] rateEnc,
    String rateType,
    byte[] fixedAnnualRateEnc,
    byte[] lastMonthRateEnc,
    byte[] annualRateEnc,
    byte[] lastTwelveMonthsRateEnc,
    LocalDate referenceDate,
    LocalDate purchaseDate,
    LocalDate dueDate,
    LocalDate gracePeriodDate,
    Instant providerUpdatedAt) {}
