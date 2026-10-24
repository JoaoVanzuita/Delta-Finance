package com.deltafinance.goals.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Histórico diário de progresso das metas. */
public interface GoalProgressSnapshotRepository extends JpaRepository<GoalProgressSnapshot, UUID> {

  Optional<GoalProgressSnapshot> findByUserIdAndGoalIdAndReferenceDate(
      UUID userId, UUID goalId, LocalDate referenceDate);

  Optional<GoalProgressSnapshot> findFirstByUserIdAndGoalIdOrderByReferenceDateDescIdDesc(
      UUID userId, UUID goalId);

  List<GoalProgressSnapshot> findByUserIdAndGoalIdOrderByReferenceDateDescIdDesc(
      UUID userId, UUID goalId);

  void deleteByUserIdAndGoalId(UUID userId, UUID goalId);
}
