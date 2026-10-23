package com.deltafinance.connections.application;

import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.connections.domain.SyncJob;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.integrations.domain.WebhookEvent;
import com.deltafinance.integrations.domain.WebhookEventRepository;
import com.deltafinance.integrations.domain.WebhookEventStatus;
import com.deltafinance.shared.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Processa eventos de webhook pendentes de uma conexão.
 *
 * <p>O conteúdo da notificação nunca é tratado como fonte integral: o evento é apenas um sinal para
 * consultar os dados completos no provedor. Uma falha mantém o evento para retentativa e não apaga
 * histórico.
 */
@Service
public class WebhookEventProcessor {

  private static final List<WebhookEventStatus> PENDING =
      List.of(WebhookEventStatus.RECEIVED, WebhookEventStatus.FAILED);

  private final WebhookEventRepository eventRepository;
  private final PluggyIntegrationRepository integrationRepository;
  private final PluggyConnectionRepository connectionRepository;
  private final PluggySyncService syncService;
  private final Clock clock;

  public WebhookEventProcessor(
      WebhookEventRepository eventRepository,
      PluggyIntegrationRepository integrationRepository,
      PluggyConnectionRepository connectionRepository,
      PluggySyncService syncService,
      Clock clock) {
    this.eventRepository = eventRepository;
    this.integrationRepository = integrationRepository;
    this.connectionRepository = connectionRepository;
    this.syncService = syncService;
    this.clock = clock;
  }

  /** Processa os eventos ainda não concluídos da conexão do job. */
  public boolean processPending(SyncJob job, String correlationId) {
    if (job.getUserId() == null || job.getConnectionId() == null) {
      return true;
    }
    List<WebhookEvent> events =
        eventRepository.findByConnectionIdAndStatusInOrderByReceivedAtAsc(
            job.getConnectionId(), PENDING);
    if (events.isEmpty()) {
      return true;
    }
    Optional<PluggyIntegration> integration =
        integrationRepository.findByIdAndUserId(job.getIntegrationId(), job.getUserId());
    Optional<PluggyConnection> connection =
        connectionRepository.findByIdAndUserId(job.getConnectionId(), job.getUserId());
    if (integration.isEmpty() || connection.isEmpty()) {
      for (WebhookEvent event : events) {
        event.markIgnored("CONNECTION_NOT_FOUND");
        eventRepository.save(event);
      }
      return true;
    }

    boolean allProcessed = true;
    for (WebhookEvent event : events) {
      event.prepareProcessing();
      // Recarrega por evento: cada sincronização atualiza versão de integração/conexão.
      Optional<PluggyIntegration> currentIntegration =
          integrationRepository.findByIdAndUserId(job.getIntegrationId(), job.getUserId());
      Optional<PluggyConnection> currentConnection =
          connectionRepository.findByIdAndUserId(job.getConnectionId(), job.getUserId());
      if (currentIntegration.isEmpty() || currentConnection.isEmpty()) {
        event.markIgnored("CONNECTION_NOT_FOUND");
        eventRepository.save(event);
        continue;
      }
      SyncResult result =
          syncService.sync(currentIntegration.get(), currentConnection.get(), null, correlationId);
      if (result.succeeded()) {
        event.markProcessed(clock.now());
      } else {
        event.markFailed(result.failure() == null ? "SYNC_FAILED" : result.failure().errorCode());
        allProcessed = false;
      }
      eventRepository.save(event);
    }
    return allProcessed;
  }
}
