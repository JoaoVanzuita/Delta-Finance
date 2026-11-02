package com.deltafinance.goals.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Histórico diário de progresso das metas. */
public interface GoalProgressSnapshotRepository extends JpaRepository<GoalProgressSnapshot, UUID> {

  Optional<GoalProgressSnapshot> findByUserIdAndGoalIdAndReferenceDate(
      UUID userId, UUID goalId, LocalDate referenceDate);

  /** Mesma busca com lock de escrita para serializar gravações concorrentes do mesmo dia. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s from GoalProgressSnapshot s
      where s.userId = :userId
        and s.goalId = :goalId
        and s.referenceDate = :referenceDate
      """)
  Optional<GoalProgressSnapshot> findForUpdate(
      @Param("userId") UUID userId,
      @Param("goalId") UUID goalId,
      @Param("referenceDate") LocalDate referenceDate);

  Optional<GoalProgressSnapshot> findFirstByUserIdAndGoalIdOrderByReferenceDateDescIdDesc(
      UUID userId, UUID goalId);

  List<GoalProgressSnapshot> findByUserIdAndGoalIdOrderByReferenceDateDescIdDesc(
      UUID userId, UUID goalId, Pageable pageable);

  @Query(
      """
      select s from GoalProgressSnapshot s
      where s.userId = :userId
        and s.goalId = :goalId
        and (s.referenceDate < :referenceDate
          or (s.referenceDate = :referenceDate and s.id < :id))
      order by s.referenceDate desc, s.id desc
      """)
  List<GoalProgressSnapshot> findPageBefore(
      @Param("userId") UUID userId,
      @Param("goalId") UUID goalId,
      @Param("referenceDate") LocalDate referenceDate,
      @Param("id") UUID id,
      Pageable pageable);

  void deleteByUserIdAndGoalId(UUID userId, UUID goalId);
}
