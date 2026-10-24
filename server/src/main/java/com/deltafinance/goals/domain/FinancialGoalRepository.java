package com.deltafinance.goals.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Metas financeiras, sempre escopadas pelo usuário da sessão. */
public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, UUID> {

  Optional<FinancialGoal> findByIdAndUserId(UUID id, UUID userId);

  List<FinancialGoal> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
