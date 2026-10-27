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
  private final SyncJobService syncJobService;

  public IntegrationDisconnectedListener(
      PluggyConnectionRepository connectionRepository, SyncJobService syncJobService) {
    this.connectionRepository = connectionRepository;
    this.syncJobService = syncJobService;
  }

  @EventListener
  public void onDisconnected(IntegrationDisconnectedEvent event) {
    syncJobService.cancelActiveForIntegration(event.userId(), event.integrationId());
    for (var connection :
        connectionRepository.findByIntegrationIdOrderByCreatedAtAsc(event.integrationId())) {
      if (connection.getStatus() != ConnectionStatus.ITEM_UNAVAILABLE) {
        connection.markUnavailable();
        connectionRepository.save(connection);
      }
    }
  }
}
