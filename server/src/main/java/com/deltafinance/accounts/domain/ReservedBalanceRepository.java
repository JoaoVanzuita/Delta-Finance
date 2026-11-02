package com.deltafinance.accounts.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservedBalanceRepository extends JpaRepository<ReservedBalance, UUID> {

  Optional<ReservedBalance> findByIdAndUserId(UUID id, UUID userId);

  Optional<ReservedBalance> findByUserIdAndAccountIdAndProviderIdentificationHash(
      UUID userId, UUID accountId, byte[] providerIdentificationHash);

  List<ReservedBalance> findByUserIdAndAccountIdAndRemovedAtIsNullOrderByCreatedAtAsc(
      UUID userId, UUID accountId);

  List<ReservedBalance> findByUserIdAndAccountIdOrderByCreatedAtAsc(UUID userId, UUID accountId);
}
