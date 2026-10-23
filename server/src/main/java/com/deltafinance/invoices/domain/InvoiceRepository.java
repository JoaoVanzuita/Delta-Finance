package com.deltafinance.invoices.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  Optional<Invoice> findByIdAndUserId(UUID id, UUID userId);

  Optional<Invoice> findByUserIdAndCardAccountIdAndCycleStart(
      UUID userId, UUID cardAccountId, LocalDate cycleStart);

  List<Invoice> findByUserIdAndCardAccountIdOrderByCycleStartDesc(UUID userId, UUID cardAccountId);

  List<Invoice> findByUserIdOrderByDueDateAsc(UUID userId);

  List<Invoice> findByUserIdAndCardAccountIdOrderByDueDateAsc(UUID userId, UUID cardAccountId);
}
