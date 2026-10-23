package com.deltafinance.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório de identidade.
 *
 * <p>Identidade é global por definição (username único), por isso a busca por nome não é escopada
 * por usuário. Consultas a dados financeiros devem sempre incluir o {@code user_id} da sessão.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByUsernameNormalized(String usernameNormalized);

  boolean existsByUsernameNormalized(String usernameNormalized);
}
