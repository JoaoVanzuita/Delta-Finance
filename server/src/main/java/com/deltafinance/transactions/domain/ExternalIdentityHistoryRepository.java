package com.deltafinance.transactions.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityHistoryRepository
    extends JpaRepository<ExternalIdentityHistory, UUID> {

  Optional<ExternalIdentityHistory> findByUserIdAndSourceAndAccountIdAndExternalIdHash(
      UUID userId, String source, UUID accountId, byte[] externalIdHash);

  List<ExternalIdentityHistory> findByUserIdAndTransactionId(UUID userId, UUID transactionId);
}
