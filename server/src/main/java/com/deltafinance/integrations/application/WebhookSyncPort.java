package com.deltafinance.integrations.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta do módulo de integrações para o módulo de conexões.
 *
 * <p>Mantém a direção de dependência {@code connections → integrations}; integrações não conhecem
 * repositórios nem serviços de conexão.
 */
public interface WebhookSyncPort {

  /** Resolve a conexão local de uma integração pelo HMAC do itemId. */
  Optional<UUID> resolveConnection(UUID integrationId, byte[] itemIdHash);

  /** Enfileira o processamento assíncrono do evento para a conexão. */
  UUID enqueueWebhookProcessing(UUID userId, UUID integrationId, UUID connectionId);
}
