package com.deltafinance.invoices.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderBillRepository extends JpaRepository<ProviderBill, UUID> {

  Optional<ProviderBill> findByIdAndUserId(UUID id, UUID userId);

  Optional<ProviderBill> findByUserIdAndCardAccountIdAndExternalBillIdHash(
      UUID userId, UUID cardAccountId, byte[] externalBillIdHash);

  List<ProviderBill> findByUserIdAndCardAccountIdOrderByDueDateDesc(
      UUID userId, UUID cardAccountId);
}
