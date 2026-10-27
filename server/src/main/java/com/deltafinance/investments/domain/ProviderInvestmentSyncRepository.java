package com.deltafinance.investments.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderInvestmentSyncRepository
    extends JpaRepository<ProviderInvestmentSync, UUID> {

  Optional<ProviderInvestmentSync> findByUserIdAndConnectionId(UUID userId, UUID connectionId);

  List<ProviderInvestmentSync> findByUserIdOrderByLastSyncedAtDesc(UUID userId);
}
