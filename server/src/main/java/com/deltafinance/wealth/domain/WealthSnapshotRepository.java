package com.deltafinance.wealth.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Snapshots patrimoniais datados, únicos por usuário e dia. */
public interface WealthSnapshotRepository extends JpaRepository<WealthSnapshot, UUID> {

  Optional<WealthSnapshot> findByUserIdAndReferenceDate(UUID userId, LocalDate referenceDate);

  List<WealthSnapshot> findByUserIdOrderByReferenceDateDescIdDesc(UUID userId);
}
