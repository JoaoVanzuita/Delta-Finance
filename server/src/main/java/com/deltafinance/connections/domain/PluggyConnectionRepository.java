package com.deltafinance.connections.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluggyConnectionRepository extends JpaRepository<PluggyConnection, UUID> {

  List<PluggyConnection> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<PluggyConnection> findByIdAndUserId(UUID id, UUID userId);

  Optional<PluggyConnection> findByIntegrationIdAndExternalItemIdHash(
      UUID integrationId, byte[] externalItemIdHash);

  List<PluggyConnection> findByIntegrationIdOrderByCreatedAtAsc(UUID integrationId);

  long countByIntegrationId(UUID integrationId);

  long countByIntegrationIdAndStatusNot(UUID integrationId, ConnectionStatus status);
}
