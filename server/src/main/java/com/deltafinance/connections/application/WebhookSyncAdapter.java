package com.deltafinance.connections.application;

import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.connections.domain.SyncJobType;
import com.deltafinance.integrations.application.WebhookSyncPort;
import com.deltafinance.shared.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapta conexões/fila ao port consumido pelo módulo de integrações. */
@Component
public class WebhookSyncAdapter implements WebhookSyncPort {

  private final PluggyConnectionRepository connectionRepository;
  private final SyncJobService syncJobService;
  private final Clock clock;

  public WebhookSyncAdapter(
      PluggyConnectionRepository connectionRepository, SyncJobService syncJobService, Clock clock) {
    this.connectionRepository = connectionRepository;
    this.syncJobService = syncJobService;
    this.clock = clock;
  }

  @Override
  public Optional<UUID> resolveConnection(UUID integrationId, byte[] itemIdHash) {
    return connectionRepository
        .findByIntegrationIdAndExternalItemIdHash(integrationId, itemIdHash)
        .map(connection -> connection.getId());
  }

  @Override
  public UUID enqueueWebhookProcessing(UUID userId, UUID integrationId, UUID connectionId) {
    return syncJobService
        .enqueue(userId, integrationId, connectionId, SyncJobType.PROCESS_WEBHOOK, clock.now())
        .getId();
  }
}
