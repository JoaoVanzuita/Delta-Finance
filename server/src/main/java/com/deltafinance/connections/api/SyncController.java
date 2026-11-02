package com.deltafinance.connections.api;

import com.deltafinance.connections.application.ConnectionService;
import com.deltafinance.connections.application.ConnectionView;
import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.connections.application.SyncQueryService;
import com.deltafinance.connections.application.SyncRunView;
import com.deltafinance.integrations.application.IntegrationService;
import com.deltafinance.integrations.application.IntegrationView;
import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.shared.pagination.CursorPage;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Estado e histórico de sincronização do usuário autenticado. */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

  private final SyncJobService syncJobService;
  private final SyncQueryService syncQueryService;
  private final IntegrationService integrationService;
  private final ConnectionService connectionService;
  private final AuthenticatedUser authenticatedUser;

  public SyncController(
      SyncJobService syncJobService,
      SyncQueryService syncQueryService,
      IntegrationService integrationService,
      ConnectionService connectionService,
      AuthenticatedUser authenticatedUser) {
    this.syncJobService = syncJobService;
    this.syncQueryService = syncQueryService;
    this.integrationService = integrationService;
    this.connectionService = connectionService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/status")
  public SyncStatusResponse status() {
    UUID userId = authenticatedUser.requireId();
    List<IntegrationView> integrations = integrationService.findView(userId).stream().toList();
    List<ConnectionView> connections = connectionService.list(userId).stream().toList();
    return new SyncStatusResponse(integrations, connections, syncJobService.pendingCount(userId));
  }

  @GetMapping("/runs")
  public SyncRunPageResponse runs(
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
    CursorPage<SyncRunView> page =
        syncQueryService.runs(authenticatedUser.requireId(), cursor, limit);
    return new SyncRunPageResponse(page.items(), page.nextCursor(), page.hasMore());
  }
}
