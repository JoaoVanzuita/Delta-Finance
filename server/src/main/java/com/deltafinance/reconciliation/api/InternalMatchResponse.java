package com.deltafinance.reconciliation.api;

import com.deltafinance.reconciliation.application.InternalMatchView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InternalMatchResponse(
    UUID id,
    String kind,
    UUID debitTransactionId,
    UUID creditTransactionId,
    BigDecimal score,
    String status,
    String reason,
    Instant decidedAt,
    String criteriaVersion) {

  public static InternalMatchResponse from(InternalMatchView view) {
    return new InternalMatchResponse(
        view.id(),
        view.kind(),
        view.debitTransactionId(),
        view.creditTransactionId(),
        view.score(),
        view.status(),
        view.reason(),
        view.decidedAt(),
        view.criteriaVersion());
  }
}
