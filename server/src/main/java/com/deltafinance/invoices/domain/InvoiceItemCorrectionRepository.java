package com.deltafinance.invoices.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemCorrectionRepository
    extends JpaRepository<InvoiceItemCorrection, UUID> {

  List<InvoiceItemCorrection> findByUserIdAndInvoiceItemIdOrderByChangedAtAsc(
      UUID userId, UUID invoiceItemId);

  long countByUserIdAndInvoiceItemId(UUID userId, UUID invoiceItemId);
}
