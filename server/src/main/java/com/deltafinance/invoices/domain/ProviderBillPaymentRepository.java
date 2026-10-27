package com.deltafinance.invoices.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderBillPaymentRepository extends JpaRepository<ProviderBillPayment, UUID> {

  Optional<ProviderBillPayment> findByUserIdAndProviderBillIdAndExternalPaymentIdHash(
      UUID userId, UUID providerBillId, byte[] externalPaymentIdHash);

  List<ProviderBillPayment> findByUserIdAndProviderBillIdOrderByPaymentDateAscCreatedAtAsc(
      UUID userId, UUID providerBillId);
}
