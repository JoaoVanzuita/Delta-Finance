package com.deltafinance.reconciliation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalMatchRepository extends JpaRepository<InternalMatch, UUID> {

  Optional<InternalMatch> findByIdAndUserId(UUID id, UUID userId);

  List<InternalMatch> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<InternalMatch> findByUserIdAndStatusOrderByScoreDescCreatedAtAsc(
      UUID userId, InternalMatchStatus status);

  boolean existsByUserIdAndKindAndDebitTransactionIdAndCreditTransactionId(
      UUID userId, InternalMatchKind kind, UUID debitTransactionId, UUID creditTransactionId);
}
