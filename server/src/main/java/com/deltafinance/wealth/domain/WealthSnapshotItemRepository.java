package com.deltafinance.wealth.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WealthSnapshotItemRepository extends JpaRepository<WealthSnapshotItem, UUID> {

  List<WealthSnapshotItem> findBySnapshotIdAndUserIdOrderBySourceTypeAscIdAsc(
      UUID snapshotId, UUID userId);

  List<WealthSnapshotItem> findByUserIdAndSnapshotIdIn(UUID userId, Collection<UUID> snapshotIds);
}
