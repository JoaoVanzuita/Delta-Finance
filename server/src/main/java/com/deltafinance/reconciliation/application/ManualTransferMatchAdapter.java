package com.deltafinance.reconciliation.application;

import com.deltafinance.reconciliation.domain.InternalMatch;
import com.deltafinance.reconciliation.domain.InternalMatchKind;
import com.deltafinance.reconciliation.domain.InternalMatchRepository;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.InternalMatchPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Registra a transferência manual como correspondência confirmada. */
@Component
public class ManualTransferMatchAdapter implements InternalMatchPort {

  private static final String CRITERIA_VERSION = "manual-transfer-v1";

  private final InternalMatchRepository matchRepository;
  private final Clock clock;

  public ManualTransferMatchAdapter(InternalMatchRepository matchRepository, Clock clock) {
    this.matchRepository = matchRepository;
    this.clock = clock;
  }

  @Override
  public void confirmManualTransfer(
      UUID userId, UUID debitTransactionId, UUID creditTransactionId) {
    InternalMatch match =
        new InternalMatch(
            userId,
            InternalMatchKind.TRANSFER,
            debitTransactionId,
            creditTransactionId,
            new BigDecimal("100.00"),
            CRITERIA_VERSION,
            "Transferência manual criada pelo usuário.");
    match.markConfirmedForManualTransfer(clock.now());
    matchRepository.save(match);
  }
}
