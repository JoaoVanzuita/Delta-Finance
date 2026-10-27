package com.deltafinance.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.security.OwnershipGuard;
import com.deltafinance.security.UserPrincipal;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Caminhos críticos de autorização (T199): propriedade e escopo sempre vêm da sessão. */
class OwnershipGuardTest {

  private final UUID userId = UUID.randomUUID();
  private OwnershipGuard guard;
  private AuditService auditService;

  @BeforeEach
  void setUp() {
    OwnGuardFixture.login(userId);
    auditService = mock(AuditService.class);
    guard = new OwnershipGuard(new AuthenticatedUser(), auditService);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void usaOUsuarioDaSessaoEIgnoraIdsExternos() {
    assertThat(guard.currentUserId()).isEqualTo(userId);
    assertThat(guard.scopedUserId(null)).isEqualTo(userId);
    assertThat(guard.scopedUserId(userId)).isEqualTo(userId);
    assertThatThrownBy(() -> guard.scopedUserId(UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void exigePropriedadeDoAgregado() {
    guard.requireOwner(userId);
    UUID other = UUID.randomUUID();
    assertThatThrownBy(() -> guard.requireOwner(other)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> guard.scopedUserId(other)).isInstanceOf(NotFoundException.class);
    verify(auditService, org.mockito.Mockito.times(2))
        .record(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(AuditEventType.AUTHORIZATION_DENIED),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.anyMap());

    record Resource(UUID owner) {}
    Resource mine = new Resource(userId);
    assertThat(guard.requireOwned(mine, Resource::owner)).isSameAs(mine);
    assertThatThrownBy(() -> guard.requireOwned(new Resource(other), Resource::owner))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> guard.requireOwned(null, Resource::owner))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void semSessaoFalhaComNaoAutenticado() {
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> guard.currentUserId()).isInstanceOf(UnauthenticatedException.class);
  }

  private static final class OwnGuardFixture {
    private OwnGuardFixture() {}

    static void login(UUID id) {
      UserPrincipal principal = new UserPrincipal(id, "alice.familia", "Alice");
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
  }
}
