package com.deltafinance.connections.api;

import com.deltafinance.connections.application.ConnectionService;
import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionController {

  private final ConnectionService connectionService;
  private final SyncJobService syncJobService;
  private final AuthenticatedUser authenticatedUser;

  public ConnectionController(
      ConnectionService connectionService,
      SyncJobService syncJobService,
      AuthenticatedUser authenticatedUser) {
    this.connectionService = connectionService;
    this.syncJobService = syncJobService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  public ConnectionPageResponse list() {
    return ConnectionPageResponse.last(
        connectionService.list(authenticatedUser.requireId()).stream()
            .map(ConnectionResponse::from)
            .toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ConnectionResponse add(@Valid @RequestBody ConnectionCreateRequest request) {
    return ConnectionResponse.from(
        connectionService.add(authenticatedUser.requireId(), request.itemId()));
  }

  @PatchMapping("/{id}")
  public ConnectionResponse update(
      @PathVariable UUID id, @Valid @RequestBody ConnectionUpdateRequest request) {
    return ConnectionResponse.from(
        connectionService.updateItemId(authenticatedUser.requireId(), id, request.itemId()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID id) {
    connectionService.remove(authenticatedUser.requireId(), id);
  }

  /**
   * Consulta os dados já disponíveis agora.
   *
   * <p>Não força atualização na instituição de origem; apenas enfileira a leitura e a
   * reconciliação.
   */
  @PostMapping("/{id}/sync")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public SyncAcceptedResponse sync(@PathVariable UUID id) {
    return SyncAcceptedResponse.queued(
        syncJobService.enqueueAvailableData(authenticatedUser.requireId(), id));
  }
}
