package com.deltafinance.integrations.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

  Optional<WebhookSubscription> findByIdAndUserId(UUID id, UUID userId);

  List<WebhookSubscription> findByUserIdOrderByCreatedAtAsc(UUID userId);

  List<WebhookSubscription> findByIntegrationIdOrderByCreatedAtAsc(UUID integrationId);

  /** Resolve a inscrição pelo token HMAC do path (consulta pública de webhook). */
  @org.springframework.data.jpa.repository.Query(
      "select s from WebhookSubscription s where s.providerSubscriptionIdHash = :hash")
  Optional<WebhookSubscription> resolveByProviderSubscriptionIdHash(
      @org.springframework.data.repository.query.Param("hash") byte[] hash);

  java.util.Optional<WebhookSubscription> findFirstByIntegrationIdAndUserIdOrderByCreatedAtAsc(
      UUID integrationId, UUID userId);
}
