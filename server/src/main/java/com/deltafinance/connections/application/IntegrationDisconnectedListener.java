package com.deltafinance.connections.application;

import com.deltafinance.connections.domain.ConnectionStatus;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.integrations.application.IntegrationDisconnectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reage à desconexão da integração sem criar dependência integrations → connections. */
@Component
public class IntegrationDisconnectedListener {

  private final PluggyConnectionRepository connectionRepository;

  public IntegrationDisconnectedListener(PluggyConnectionRepository connectionRepository) {
    this.connectionRepository = connectionRepository;
  }

  @EventListener
  public void onDisconnected(IntegrationDisconnectedEvent event) {
    for (var connection :
        connectionRepository.findByIntegrationIdOrderByCreatedAtAsc(event.integrationId())) {
      if (connection.getStatus() != ConnectionStatus.ITEM_UNAVAILABLE) {
        connection.markUnavailable();
        connectionRepository.save(connection);
      }
    }
  }
}
