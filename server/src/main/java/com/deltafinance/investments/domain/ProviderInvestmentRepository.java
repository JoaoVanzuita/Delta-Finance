package com.deltafinance.investments.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderInvestmentRepository extends JpaRepository<ProviderInvestment, UUID> {

  Optional<ProviderInvestment> findByIdAndUserId(UUID id, UUID userId);

  Optional<ProviderInvestment> findByUserIdAndConnectionIdAndExternalInvestmentIdHash(
      UUID userId, UUID connectionId, byte[] externalInvestmentIdHash);

  List<ProviderInvestment> findByUserIdAndConnectionId(UUID userId, UUID connectionId);

  List<ProviderInvestment> findByUserIdOrderByStatusAscTypeAscIdAsc(UUID userId);
}
