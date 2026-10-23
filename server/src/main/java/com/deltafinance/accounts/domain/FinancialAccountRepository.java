package com.deltafinance.accounts.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

  List<FinancialAccount> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<FinancialAccount> findByIdAndUserId(UUID id, UUID userId);

  Optional<FinancialAccount> findByConnectionIdAndExternalAccountIdHash(
      UUID connectionId, byte[] externalAccountIdHash);

  List<FinancialAccount> findByConnectionIdOrderByCreatedAtAsc(UUID connectionId);

  List<FinancialAccount> findByUserIdAndTypeOrderByCreatedAtAsc(UUID userId, AccountType type);
}
