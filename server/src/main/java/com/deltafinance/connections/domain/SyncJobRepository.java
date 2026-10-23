package com.deltafinance.connections.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

  Optional<SyncJob> findByIdAndUserId(UUID id, UUID userId);

  List<SyncJob> findByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<SyncJob> findFirstByUserIdAndConnectionIdAndJobTypeAndStatusInOrderByCreatedAtAsc(
      UUID userId, UUID connectionId, SyncJobType jobType, List<SyncJobStatus> statuses);

  @Query(
      """
      select j from SyncJob j
      where j.userId = :userId
        and j.status in (com.deltafinance.connections.domain.SyncJobStatus.QUEUED,
                         com.deltafinance.connections.domain.SyncJobStatus.RUNNING,
                         com.deltafinance.connections.domain.SyncJobStatus.RETRY)
      order by j.runAfter asc
      """)
  List<SyncJob> findActiveByUserId(@Param("userId") UUID userId);

  @Query(
      """
      select j from SyncJob j
      where j.integrationId = :integrationId
        and j.status in (com.deltafinance.connections.domain.SyncJobStatus.QUEUED,
                         com.deltafinance.connections.domain.SyncJobStatus.RUNNING,
                         com.deltafinance.connections.domain.SyncJobStatus.RETRY)
      """)
  List<SyncJob> findActiveByIntegrationId(@Param("integrationId") UUID integrationId);
}
