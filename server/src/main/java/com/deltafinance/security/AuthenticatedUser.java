package com.deltafinance.security;

import com.deltafinance.shared.errors.UnauthenticatedException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Deriva o usuário autenticado exclusivamente da sessão.
 *
 * <p>Nenhum controller aceita {@code user_id} do cliente. Serviços de negócio devem chamar este
 * componente antes de qualquer consulta a dados de usuário.
 */
@Component
public class AuthenticatedUser {

  public UserPrincipal require() {
    return current().orElseThrow(UnauthenticatedException::new);
  }

  public UUID requireId() {
    return require().id();
  }

  public Optional<UserPrincipal> current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      return Optional.empty();
    }
    return Optional.of(principal);
  }
}
