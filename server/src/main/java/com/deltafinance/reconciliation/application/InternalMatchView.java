package com.deltafinance.reconciliation.application;

import com.deltafinance.reconciliation.domain.InternalMatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Visão pública de correspondência interna. */
public record InternalMatchView(
    UUID id,
    String kind,
    UUID debitTransactionId,
    UUID creditTransactionId,
    BigDecimal score,
    String status,
    String reason,
    Instant decidedAt,
    String criteriaVersion) {

  public static InternalMatchView from(InternalMatch match) {
    return new InternalMatchView(
        match.getId(),
        match.getKind().name(),
        match.getDebitTransactionId(),
        match.getCreditTransactionId(),
        match.getScore(),
        match.getStatus().name(),
        match.getReason(),
        match.getDecidedAt(),
        match.getCriteriaVersion());
  }
}
