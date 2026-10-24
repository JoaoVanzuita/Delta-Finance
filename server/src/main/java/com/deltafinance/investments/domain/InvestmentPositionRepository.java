package com.deltafinance.investments.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositório de posições manuais, sempre escopado pelo usuário da sessão. */
public interface InvestmentPositionRepository extends JpaRepository<InvestmentPosition, UUID> {

  Optional<InvestmentPosition> findByIdAndUserId(UUID id, UUID userId);

  List<InvestmentPosition> findByUserIdOrderByReferenceDateDescIdDesc(UUID userId);

  List<InvestmentPosition> findByUserIdOrderByReferenceDateDescIdDesc(
      UUID userId, Pageable pageable);

  /** Página seguinte ao cursor ({@code reference_date DESC, id DESC}), sem parâmetros nulos. */
  @Query(
      """
      select p from InvestmentPosition p
      where p.userId = :userId
        and (p.referenceDate < :cursorDate
             or (p.referenceDate = :cursorDate and p.id < :cursorId))
      order by p.referenceDate desc, p.id desc
      """)
  List<InvestmentPosition> findPageAfter(
      @Param("userId") UUID userId,
      @Param("cursorDate") LocalDate cursorDate,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);
}
