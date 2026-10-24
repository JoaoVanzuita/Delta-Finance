package com.deltafinance.wealth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Ativos e passivos manuais, sempre escopados pelo usuário da sessão. */
public interface ManualAssetLiabilityRepository extends JpaRepository<ManualAssetLiability, UUID> {

  Optional<ManualAssetLiability> findByIdAndUserId(UUID id, UUID userId);

  List<ManualAssetLiability> findByUserIdOrderByReferenceDateDescIdDesc(UUID userId);
}
