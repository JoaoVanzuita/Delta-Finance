package com.deltafinance.wealth.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Composição cifrada dos snapshots patrimoniais. */
public interface WealthSnapshotItemRepository extends JpaRepository<WealthSnapshotItem, UUID> {

  List<WealthSnapshotItem> findByUserIdAndSnapshotIdOrderBySourceTypeAscIdAsc(
      UUID userId, UUID snapshotId);

  void deleteByUserIdAndSnapshotId(UUID userId, UUID snapshotId);
}
