package com.deltafinance.connections.application;

import com.deltafinance.connections.domain.ConnectionStatus;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.integrations.application.ConnectionCounter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementação da porta de contagem usada pela integração. */
@Component
public class ConnectionCounterAdapter implements ConnectionCounter {

  private final PluggyConnectionRepository connectionRepository;

  public ConnectionCounterAdapter(PluggyConnectionRepository connectionRepository) {
    this.connectionRepository = connectionRepository;
  }

  @Override
  public long countByIntegration(UUID integrationId) {
    return connectionRepository.countByIntegrationIdAndStatusNot(
        integrationId, ConnectionStatus.ITEM_UNAVAILABLE);
  }
}
