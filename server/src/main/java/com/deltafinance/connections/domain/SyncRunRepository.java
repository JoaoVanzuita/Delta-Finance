package com.deltafinance.connections.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

  Optional<SyncRun> findByIdAndUserId(UUID id, UUID userId);

  Optional<SyncRun> findFirstByUserIdAndConnectionIdOrderByStartedAtDesc(
      UUID userId, UUID connectionId);
}
