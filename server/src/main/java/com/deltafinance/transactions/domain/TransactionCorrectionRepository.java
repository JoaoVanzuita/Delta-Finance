package com.deltafinance.transactions.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCorrectionRepository
    extends JpaRepository<TransactionCorrection, UUID> {

  List<TransactionCorrection> findByUserIdAndTransactionIdOrderByChangedAtAsc(
      UUID userId, UUID transactionId);
}
