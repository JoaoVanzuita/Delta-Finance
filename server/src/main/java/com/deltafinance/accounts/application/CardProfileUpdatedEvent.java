package com.deltafinance.accounts.application;

import java.util.UUID;

/** Publicado depois que o ciclo/limite de um cartão é configurado ou alterado. */
public record CardProfileUpdatedEvent(UUID userId, UUID cardAccountId) {}
