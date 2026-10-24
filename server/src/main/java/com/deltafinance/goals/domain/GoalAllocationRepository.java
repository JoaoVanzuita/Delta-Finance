package com.deltafinance.goals.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Alocações explícitas das metas. */
public interface GoalAllocationRepository extends JpaRepository<GoalAllocation, UUID> {

  List<GoalAllocation> findByUserIdAndGoalIdOrderByOrdinalAscIdAsc(UUID userId, UUID goalId);

  List<GoalAllocation> findByUserIdAndSourceTypeAndSourceId(
      UUID userId, AllocationSourceType sourceType, UUID sourceId);

  void deleteByUserIdAndGoalId(UUID userId, UUID goalId);
}
