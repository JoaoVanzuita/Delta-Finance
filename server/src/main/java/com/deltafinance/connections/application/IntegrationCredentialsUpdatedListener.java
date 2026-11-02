package com.deltafinance.connections.application;

import com.deltafinance.connections.domain.ConnectionStatus;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.connections.domain.SyncJobType;
import com.deltafinance.integrations.application.IntegrationCredentialsUpdatedEvent;
import com.deltafinance.shared.time.Clock;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reativa conexões que estavam bloqueadas por credencial inválida.
 *
 * <p>A integração é a dona das credenciais; a conexão reage ao evento para não criar dependência
 * {@code integrations → connections}.
 */
@Component
public class IntegrationCredentialsUpdatedListener {

  private final PluggyConnectionRepository connectionRepository;
  private final SyncJobService syncJobService;
  private final Clock clock;

  public IntegrationCredentialsUpdatedListener(
      PluggyConnectionRepository connectionRepository, SyncJobService syncJobService, Clock clock) {
    this.connectionRepository = connectionRepository;
    this.syncJobService = syncJobService;
    this.clock = clock;
  }

  @EventListener
  public void onCredentialsUpdated(IntegrationCredentialsUpdatedEvent event) {
    for (var connection :
        connectionRepository.findByIntegrationIdOrderByCreatedAtAsc(event.integrationId())) {
      if (connection.getStatus() != ConnectionStatus.CREDENTIAL_FAILURE) {
        continue;
      }
      connection.reactivateAfterCredentials();
      connectionRepository.save(connection);
      syncJobService.enqueue(
          event.userId(),
          event.integrationId(),
          connection.getId(),
          SyncJobType.SYNC_CONNECTION,
          clock.now());
    }
  }
}
