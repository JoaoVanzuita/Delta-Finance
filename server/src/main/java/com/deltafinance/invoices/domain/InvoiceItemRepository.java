package com.deltafinance.invoices.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

  Optional<InvoiceItem> findByIdAndUserId(UUID id, UUID userId);

  Optional<InvoiceItem> findByUserIdAndTransactionIdAndRemovedAtIsNull(
      UUID userId, UUID transactionId);

  Optional<InvoiceItem> findByUserIdAndProviderItemIdHashAndRemovedAtIsNull(
      UUID userId, byte[] providerItemIdHash);

  List<InvoiceItem> findByUserIdAndInvoiceIdAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
      UUID userId, UUID invoiceId);

  List<InvoiceItem> findByUserIdAndInvoiceIdOrderByOccurredOnAscCreatedAtAsc(
      UUID userId, UUID invoiceId);

  List<InvoiceItem> findByUserIdAndTransactionId(UUID userId, UUID transactionId);
}
