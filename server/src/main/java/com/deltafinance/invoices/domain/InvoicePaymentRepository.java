package com.deltafinance.invoices.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {

  Optional<InvoicePayment> findByIdAndUserId(UUID id, UUID userId);

  Optional<InvoicePayment> findByUserIdAndSourceTransactionId(
      UUID userId, UUID sourceTransactionId);

  Optional<InvoicePayment> findByUserIdAndDedupKey(UUID userId, byte[] dedupKey);

  List<InvoicePayment> findByUserIdAndInvoiceIdOrderByPaidOnAscCreatedAtAsc(
      UUID userId, UUID invoiceId);
}
