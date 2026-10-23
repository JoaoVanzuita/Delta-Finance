package com.deltafinance.integrations.application;

import java.util.UUID;

/** Evento local: integração desconectada; conexões devem ser marcadas indisponíveis. */
public record IntegrationDisconnectedEvent(UUID userId, UUID integrationId) {}
