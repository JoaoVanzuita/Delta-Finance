package com.deltafinance.integrations.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

  Optional<WebhookEvent> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Busca idempotente por evento do provedor.
   *
   * <p>A resolução ocorre antes de conhecermos o usuário, a partir da inscrição autenticada.
   */
  Optional<WebhookEvent> findByIntegrationIdAndProviderEventId(
      UUID integrationId, String providerEventId);

  List<WebhookEvent> findByConnectionIdAndStatusOrderByReceivedAtAsc(
      UUID connectionId, WebhookEventStatus status);

  List<WebhookEvent> findByConnectionIdAndStatusInOrderByReceivedAtAsc(
      UUID connectionId, List<WebhookEventStatus> statuses);

  List<WebhookEvent> findByUserIdOrderByReceivedAtDesc(UUID userId);
}
