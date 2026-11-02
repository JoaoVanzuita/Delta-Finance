package com.deltafinance.transactions.application;

import java.util.Set;
import java.util.UUID;

/** Pede um único recálculo por cartão ao final de um lote de importação. */
public record CardRecalculationRequestedEvent(UUID userId, Set<UUID> cardAccountIds) {}
