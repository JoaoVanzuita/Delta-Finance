package com.deltafinance.auth.application;

import java.util.UUID;

/** Resultado público de operações de identidade (nunca inclui hash ou segredo). */
public record UserView(UUID id, String username, java.time.Instant createdAt) {}
