package com.deltafinance.auth.application;

import com.deltafinance.auth.domain.AppUser;
import com.deltafinance.auth.domain.AppUserRepository;
import com.deltafinance.auth.domain.Usernames;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação assistida de senha (T197 / FR-139).
 *
 * <p>Não há e-mail transacional no MVP. O operador executa o mecanismo no host, após verificar a
 * identidade por um canal independente. O serviço:
 *
 * <ul>
 *   <li>exige um token operacional configurado em {@code delta.security.operator-reset-token};
 *   <li>só altera contas {@code ACTIVE}, com mensagem genérica para conta inexistente;
 *   <li>gera uma senha temporária aleatória, substitui o hash e nunca a registra em log/auditoria;
 *   <li>revoga todas as sessões, limpa bloqueios de login e registra evento sanitizado.
 * </ul>
 *
 * <p>Nunca aceita {@code user_id} de requisição HTTP: o mecanismo é exclusivamente operacional.
 */
@Service
public class AssistedPasswordResetService {

  private static final int TEMPORARY_PASSWORD_LENGTH = 24;
  private static final String ALPHABET =
      "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final String TARGET_TYPE = "app_user";

  private final AppUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
  private final IndexHmacService hmacService;
  private final JdbcTemplate jdbcTemplate;
  private final AuditService auditService;
  private final Clock clock;
  private final String operatorToken;
  private final SecureRandom secureRandom = new SecureRandom();

  public AssistedPasswordResetService(
      AppUserRepository userRepository,
      PasswordEncoder passwordEncoder,
      FindByIndexNameSessionRepository<? extends Session> sessionRepository,
      IndexHmacService hmacService,
      JdbcTemplate jdbcTemplate,
      AuditService auditService,
      Clock clock,
      @Value("${delta.security.operator-reset-token:}") String operatorToken) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessionRepository = sessionRepository;
    this.hmacService = hmacService;
    this.jdbcTemplate = jdbcTemplate;
    this.auditService = auditService;
    this.clock = clock;
    this.operatorToken = operatorToken == null ? "" : operatorToken.trim();
  }

  /** Indica se o mecanismo está habilitado nesta instalação. */
  public boolean isConfigured() {
    return !operatorToken.isBlank();
  }

  /**
   * Executa a recuperação assistida. O chamador é o operador; a identidade do titular deve ter sido
   * verificada fora do sistema.
   */
  @Transactional
  public ResetResult reset(String rawUsername, String providedToken, String operator) {
    requireOperator(providedToken);
    String normalized = Usernames.normalize(rawUsername);
    AppUser user =
        normalized == null
            ? null
            : userRepository
                .findByUsernameNormalized(normalized)
                .filter(AppUser::isActive)
                .orElse(null);
    if (user == null) {
      throw new NotFoundException();
    }

    String temporaryPassword = generatePassword();
    user.changePasswordHash(passwordEncoder.encode(temporaryPassword));
    userRepository.save(user);

    int sessionsRevoked = 0;
    for (String sessionId :
        sessionRepository.findByPrincipalName(user.getUsernameNormalized()).keySet()) {
      sessionRepository.deleteById(sessionId);
      sessionsRevoked++;
    }
    jdbcTemplate.update(
        "delete from login_attempt where username_hash = ?",
        hmacService.usernameHash(user.getUsernameNormalized()));

    auditService.record(
        user.getId(),
        AuditEventType.PASSWORD_RESET_ASSISTED,
        TARGET_TYPE,
        user.getId(),
        Map.of(
            "operator", sanitizeOperator(operator),
            "sessionsRevoked", sessionsRevoked,
            "performedAt", clock.now().toString()));

    return new ResetResult(
        user.getId(), user.getUsernameNormalized(), temporaryPassword, sessionsRevoked);
  }

  private void requireOperator(String providedToken) {
    if (!isConfigured()) {
      throw new IllegalStateException(
          "Recuperação assistida desabilitada: configure DELTA_OPERATOR_RESET_TOKEN");
    }
    byte[] expected = operatorToken.getBytes(StandardCharsets.UTF_8);
    byte[] provided =
        (providedToken == null ? "" : providedToken.trim()).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, provided)) {
      throw new IllegalArgumentException("Token operacional inválido");
    }
  }

  private String generatePassword() {
    StringBuilder value = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
    for (int index = 0; index < TEMPORARY_PASSWORD_LENGTH; index++) {
      value.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
    }
    return value.toString();
  }

  private static String sanitizeOperator(String operator) {
    if (operator == null || operator.isBlank()) {
      return "operator";
    }
    String sanitized = operator.strip().replaceAll("[^A-Za-z0-9._@ -]", "_");
    return sanitized.length() > 80 ? sanitized.substring(0, 80) : sanitized;
  }

  /** Resultado para o operador. A senha temporária nunca entra em log nem em auditoria. */
  public record ResetResult(
      UUID userId, String username, String temporaryPassword, int sessionsRevoked) {}
}
