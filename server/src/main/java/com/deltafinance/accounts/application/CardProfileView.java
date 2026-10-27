package com.deltafinance.accounts.application;

import java.util.UUID;

/** Metadados e valores do perfil de cartão. */
public record CardProfileView(
    UUID accountId,
    int closingDay,
    int dueDay,
    String creditLimit,
    String creditBalance,
    String brand,
    String level,
    String last4,
    String availableCreditLimit) {}
