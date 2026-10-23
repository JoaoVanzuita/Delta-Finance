package com.deltafinance.accounts.application;

import java.util.UUID;

/** Perfil de cartão exposto na API. */
public record CardProfileView(
    UUID accountId, int closingDay, int dueDay, String creditLimit, String creditBalance) {}
