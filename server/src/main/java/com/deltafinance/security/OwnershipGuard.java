package com.deltafinance.security;

import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.NotFoundException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Verificação de propriedade de recursos.
 *
 * <p>Acesso cruzado responde {@link NotFoundException} (404), nunca 403: o atacante não deve
 * conseguir distinguir "não existe" de "existe, mas é de outro usuário". A tentativa é registrada
 * em auditoria de forma sanitizada.
 *
 * <p>Estas verificações complementam (não substituem) consultas de repositório sempre escopadas por
 * {@code user_id}.
 */
@Component
public class OwnershipGuard {

  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;

  public OwnershipGuard(AuthenticatedUser authenticatedUser, AuditService auditService) {
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
  }

  public UUID currentUserId() {
    return authenticatedUser.requireId();
  }

  /** Exige que o recurso pertença ao usuário da sessão. */
  public void requireOwner(UUID ownerUserId) {
    UUID currentUserId = currentUserId();
    if (!currentUserId.equals(ownerUserId)) {
      auditDenied();
      throw new NotFoundException();
    }
  }

  /** Variante que extrai o proprietário do próprio agregado. */
  public <T> T requireOwned(T resource, Function<T, UUID> ownerExtractor) {
    Objects.requireNonNull(resource, "resource");
    requireOwner(ownerExtractor.apply(resource));
    return resource;
  }

  /**
   * Resolve um filtro opcional de usuário. Um valor divergente do usuário da sessão é tratado como
   * recurso inexistente, impedindo sondagem por IDs.
   */
  public UUID scopedUserId(UUID requestedUserId) {
    UUID currentUserId = currentUserId();
    if (requestedUserId != null && !requestedUserId.equals(currentUserId)) {
      auditDenied();
      throw new NotFoundException();
    }
    return currentUserId;
  }

  private void auditDenied() {
    try {
      auditService.record(
          currentUserId(),
          AuditEventType.AUTHORIZATION_DENIED,
          "resource",
          null,
          Map.of("reason", "OWNERSHIP_MISMATCH"));
    } catch (RuntimeException ignored) {
      // Falha de auditoria não altera a resposta 404 de recurso inexistente.
    }
  }
}
