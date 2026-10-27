package com.deltafinance.connections.application;

import com.deltafinance.accounts.application.ConnectionStatusPort;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementa a porta de estado de conexão usada pelas contas/cartões. */
@Component
public class ConnectionStatusAdapter implements ConnectionStatusPort {

  private final PluggyConnectionRepository connectionRepository;

  public ConnectionStatusAdapter(PluggyConnectionRepository connectionRepository) {
    this.connectionRepository = connectionRepository;
  }

  @Override
  public String statusOf(UUID userId, UUID connectionId) {
    if (connectionId == null) {
      return null;
    }
    return connectionRepository
        .findByIdAndUserId(connectionId, userId)
        .map(connection -> connection.getStatus().name())
        .orElse(null);
  }
}
