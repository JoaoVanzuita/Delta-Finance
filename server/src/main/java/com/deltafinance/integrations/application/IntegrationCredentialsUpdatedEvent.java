package com.deltafinance.integrations.application;

import java.util.UUID;

/** Publicado depois que credenciais válidas substituem as anteriores. */
public record IntegrationCredentialsUpdatedEvent(UUID userId, UUID integrationId) {}
