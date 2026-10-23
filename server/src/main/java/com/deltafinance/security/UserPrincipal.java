package com.deltafinance.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * Principal autenticado.
 *
 * <p>{@link #getName()} usa o nome normalizado, consistente com o índice de sessões do Spring
 * Session; {@code displayName} é apenas para apresentação.
 */
public record UserPrincipal(UUID id, String username, String displayName)
    implements Principal, Serializable {

  @Override
  public String getName() {
    return username;
  }
}
