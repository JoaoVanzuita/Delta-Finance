package com.deltafinance.reconciliation.application;

import com.deltafinance.reconciliation.domain.InternalMatch;
import com.deltafinance.reconciliation.domain.InternalMatchRepository;
import com.deltafinance.reconciliation.domain.InternalMatchStatus;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.ReconciliationState;
import com.deltafinance.transactions.domain.TransactionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirmação/rejeição explícita de uma sugestão de movimento interno. */
@Service
public class InternalMatchDecisionService {

  private final InternalMatchRepository matchRepository;
  private final TransactionRepository transactionRepository;
  private final AuditService auditService;
  private final Clock clock;

  public InternalMatchDecisionService(
      InternalMatchRepository matchRepository,
      TransactionRepository transactionRepository,
      AuditService auditService,
      Clock clock) {
    this.matchRepository = matchRepository;
    this.transactionRepository = transactionRepository;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional
  public InternalMatchView confirm(UUID userId, UUID matchId) {
    InternalMatch match = requirePending(userId, matchId);
    match.confirm(clock.now());
    matchRepository.save(match);
    markMatched(userId, match.getDebitTransactionId());
    markMatched(userId, match.getCreditTransactionId());
    auditService.record(
        userId, AuditEventType.INTERNAL_MATCH_CONFIRMED, "internal_match", match.getId());
    return InternalMatchView.from(match);
  }

  @Transactional
  public InternalMatchView reject(UUID userId, UUID matchId) {
    InternalMatch match = requirePending(userId, matchId);
    match.reject(clock.now());
    matchRepository.save(match);
    auditService.record(
        userId, AuditEventType.INTERNAL_MATCH_REJECTED, "internal_match", match.getId());
    return InternalMatchView.from(match);
  }

  private InternalMatch requirePending(UUID userId, UUID matchId) {
    InternalMatch match =
        matchRepository.findByIdAndUserId(matchId, userId).orElseThrow(NotFoundException::new);
    if (match.getStatus() != InternalMatchStatus.PENDING) {
      throw new ConflictException(
          "MATCH_ALREADY_DECIDED", "Esta sugestão já foi confirmada ou rejeitada.");
    }
    return match;
  }

  private void markMatched(UUID userId, UUID transactionId) {
    transactionRepository
        .findByIdAndUserId(transactionId, userId)
        .ifPresent(
            transaction -> {
              transaction.setReconciliationState(ReconciliationState.MATCHED);
              transactionRepository.save(transaction);
            });
  }
}
