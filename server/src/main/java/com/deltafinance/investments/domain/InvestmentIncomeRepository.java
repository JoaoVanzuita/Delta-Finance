package com.deltafinance.investments.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentIncomeRepository extends JpaRepository<InvestmentIncome, UUID> {

  Optional<InvestmentIncome> findByIdAndUserId(UUID id, UUID userId);

  List<InvestmentIncome> findByUserIdOrderByIncomeDateDescCreatedAtDesc(UUID userId);

  List<InvestmentIncome> findByUserIdAndIncomeDateBetweenOrderByIncomeDateDescCreatedAtDesc(
      UUID userId, LocalDate from, LocalDate to);
}
