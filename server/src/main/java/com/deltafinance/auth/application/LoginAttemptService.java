package com.deltafinance.auth.application;

import com.deltafinance.auth.config.LoginProtectionProperties;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.time.Clock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Limitação de tentativas de autenticação.
 *
 * <p>5 falhas por 15 minutos por combinação de usuário + IP, bloqueio de 15 minutos persistido a
 * partir da falha que atingiu o limite e contador zerado em autenticação bem-sucedida. IP e usuário
 * são persistidos apenas como HMAC; a mensagem devolvida é sempre genérica.
 */
@Service
public class LoginAttemptService {

  public record Decision(boolean blocked, Duration retryAfter) {
    static Decision allowed() {
      return new Decision(false, Duration.ZERO);
    }
  }

  private static final String LAST_BLOCK =
      """
      SELECT max(blocked_until)
      FROM login_attempt
      WHERE username_hash = ? AND ip_hash = ? AND success = false
      """;

  private static final String COUNT_FAILURES =
      """
      SELECT count(*)
      FROM login_attempt
      WHERE username_hash = ? AND ip_hash = ? AND success = false AND attempted_at > ?
      """;

  private static final String INSERT =
      """
      INSERT INTO login_attempt
        (id, username_hash, ip_hash, attempted_at, success, failure_reason)
      VALUES (?, ?, ?, ?, ?, ?)
      """;

  private static final String MARK_BLOCKED =
      """
      UPDATE login_attempt
      SET blocked_until = ?
      WHERE username_hash = ? AND ip_hash = ? AND success = false AND blocked_until IS NULL
      """;

  private static final String CLEAR_EXPIRED_BLOCK =
      """
      DELETE FROM login_attempt
      WHERE username_hash = ? AND ip_hash = ? AND success = false
        AND blocked_until IS NOT NULL AND blocked_until <= ?
      """;

  private static final String DELETE_FAILURES =
      "DELETE FROM login_attempt WHERE username_hash = ? AND ip_hash = ? AND success = false";

  private final JdbcTemplate jdbcTemplate;
  private final IndexHmacService hmacService;
  private final LoginProtectionProperties properties;
  private final Clock clock;

  public LoginAttemptService(
      JdbcTemplate jdbcTemplate,
      IndexHmacService hmacService,
      LoginProtectionProperties properties,
      Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.hmacService = hmacService;
    this.properties = properties;
    this.clock = clock;
  }

  /** Verifica o bloqueio persistido antes de validar a senha. */
  public Decision check(String usernameNormalized, String ipAddress) {
    Instant now = clock.now();
    byte[] usernameHash = hmacService.usernameHash(usernameNormalized);
    byte[] ipHash = hmacService.ipHash(ipAddress);

    Timestamp blockedUntil =
        jdbcTemplate.queryForObject(LAST_BLOCK, Timestamp.class, usernameHash, ipHash);
    if (blockedUntil != null) {
      Instant until = blockedUntil.toInstant();
      if (until.isAfter(now)) {
        return new Decision(true, Duration.between(now, until));
      }
      jdbcTemplate.update(CLEAR_EXPIRED_BLOCK, usernameHash, ipHash, Timestamp.from(now));
    }

    long failures = countFailures(usernameHash, ipHash, now);
    if (failures < properties.getMaxFailures()) {
      return Decision.allowed();
    }

    // Salvaguarda para falhas concorrentes ou registros antigos sem blocked_until.
    Instant until = now.plus(properties.getBlockDuration());
    markBlocked(usernameHash, ipHash, until);
    return new Decision(true, properties.getBlockDuration());
  }

  public void recordFailure(String usernameNormalized, String ipAddress) {
    Instant now = clock.now();
    byte[] usernameHash = hmacService.usernameHash(usernameNormalized);
    byte[] ipHash = hmacService.ipHash(ipAddress);
    insert(usernameHash, ipHash, now, false, "BAD_CREDENTIALS");
    if (countFailures(usernameHash, ipHash, now) >= properties.getMaxFailures()) {
      markBlocked(usernameHash, ipHash, now.plus(properties.getBlockDuration()));
    }
  }

  /** Autenticação bem-sucedida zera o contador da combinação usuário+IP. */
  public void recordSuccess(String usernameNormalized, String ipAddress) {
    byte[] usernameHash = hmacService.usernameHash(usernameNormalized);
    byte[] ipHash = hmacService.ipHash(ipAddress);
    jdbcTemplate.update(DELETE_FAILURES, usernameHash, ipHash);
    insert(usernameHash, ipHash, clock.now(), true, null);
  }

  private long countFailures(byte[] usernameHash, byte[] ipHash, Instant now) {
    Long failures =
        jdbcTemplate.queryForObject(
            COUNT_FAILURES,
            Long.class,
            usernameHash,
            ipHash,
            Timestamp.from(now.minus(properties.getWindow())));
    return failures == null ? 0 : failures;
  }

  private void markBlocked(byte[] usernameHash, byte[] ipHash, Instant until) {
    jdbcTemplate.update(MARK_BLOCKED, Timestamp.from(until), usernameHash, ipHash);
  }

  private void insert(
      byte[] usernameHash,
      byte[] ipHash,
      Instant attemptedAt,
      boolean success,
      String failureReason) {
    jdbcTemplate.update(
        INSERT,
        UUID.randomUUID(),
        usernameHash,
        ipHash,
        Timestamp.from(attemptedAt),
        success,
        failureReason);
  }
}
