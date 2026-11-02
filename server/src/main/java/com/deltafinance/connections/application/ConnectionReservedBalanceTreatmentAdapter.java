package com.deltafinance.connections.application;

import com.deltafinance.accounts.application.ConnectionReservedBalanceTreatmentPort;
import com.deltafinance.accounts.domain.ReservedBalanceTreatment;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementa a porta de tratamento padrão de saldos reservados por conexão. */
@Component
public class ConnectionReservedBalanceTreatmentAdapter
    implements ConnectionReservedBalanceTreatmentPort {

  private final PluggyConnectionRepository connectionRepository;

  public ConnectionReservedBalanceTreatmentAdapter(
      PluggyConnectionRepository connectionRepository) {
    this.connectionRepository = connectionRepository;
  }

  @Override
  public ReservedBalanceTreatment treatmentOf(UUID userId, UUID connectionId) {
    if (connectionId == null) {
      return ReservedBalanceTreatment.UNKNOWN;
    }
    return connectionRepository
        .findByIdAndUserId(connectionId, userId)
        .map(connection -> connection.getReservedBalanceTreatment())
        .orElse(ReservedBalanceTreatment.UNKNOWN);
  }
}
