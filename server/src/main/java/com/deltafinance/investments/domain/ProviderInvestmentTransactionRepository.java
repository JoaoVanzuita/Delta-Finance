package com.deltafinance.investments.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderInvestmentTransactionRepository
    extends JpaRepository<ProviderInvestmentTransaction, UUID> {

  Optional<ProviderInvestmentTransaction> findByUserIdAndProviderInvestmentIdAndDedupKey(
      UUID userId, UUID providerInvestmentId, byte[] dedupKey);

  List<ProviderInvestmentTransaction> findByUserIdAndProviderInvestmentId(
      UUID userId, UUID providerInvestmentId);

  List<ProviderInvestmentTransaction> findByUserIdAndTypeAndRemovedAtIsNull(
      UUID userId, String type);
}
