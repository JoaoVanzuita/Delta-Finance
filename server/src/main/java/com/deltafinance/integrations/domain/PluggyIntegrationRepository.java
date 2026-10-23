package com.deltafinance.integrations.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluggyIntegrationRepository extends JpaRepository<PluggyIntegration, UUID> {

  Optional<PluggyIntegration> findByUserIdAndProvider(UUID userId, String provider);

  Optional<PluggyIntegration> findByIdAndUserId(UUID id, UUID userId);

  default Optional<PluggyIntegration> findMeuPluggy(UUID userId) {
    return findByUserIdAndProvider(userId, PluggyIntegration.PROVIDER);
  }
}
