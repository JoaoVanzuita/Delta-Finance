package com.deltafinance.wealth.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WealthSnapshotRepository extends JpaRepository<WealthSnapshot, UUID> {

  Optional<WealthSnapshot> findByUserIdAndReferenceDate(UUID userId, LocalDate referenceDate);

  Optional<WealthSnapshot> findByIdAndUserId(UUID id, UUID userId);

  List<WealthSnapshot> findByUserIdAndReferenceDateBetweenOrderByReferenceDateDesc(
      UUID userId, LocalDate from, LocalDate to);

  List<WealthSnapshot> findByUserIdOrderByReferenceDateDesc(UUID userId);

  List<WealthSnapshot> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);
}
