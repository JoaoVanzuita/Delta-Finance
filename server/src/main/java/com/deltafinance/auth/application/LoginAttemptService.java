package com.deltafinance.auth.application;

import com.deltafinance.auth.config.LoginProtectionProperties;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.time.Clock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Limitação de tentativas de autenticação.
 *
 * <p>5 falhas por 15 minutos por combinação de usuário + IP, bloqueio de 15 minutos e contador
 * zerado em autenticação bem-sucedida. IP e usuário são persistidos apenas como HMAC; a mensagem
 * devolvida é sempre genérica.
 */
@Service
public class LoginAttemptService {

  public record Decision(boolean blocked, Duration retryAfter) {
    static Decision allowed() {
      return new Decision(false, Duration.ZERO);
    }
  }

  private static final String COUNT_FAILURES =
      """
      SELECT count(*), max(attempted_at)
      FROM login_attempt
      WHERE username_hash = ? AND ip_hash = ? AND success = false AND attempted_at > ?
      """;

  private static final String INSERT =
      """
      INSERT INTO login_attempt
        (id, username_hash, ip_hash, attempted_at, success, failure_reason)
      VALUES (?, ?, ?, ?, ?, ?)
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

  /** Verifica o bloqueio antes de validar a senha. */
  public Decision check(String usernameNormalized, String ipAddress) {
    Instant now = clock.now();
    Timestamp windowStart = Timestamp.from(now.minus(properties.getWindow()));
    byte[] usernameHash = hmacService.usernameHash(usernameNormalized);
    byte[] ipHash = hmacService.ipHash(ipAddress);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(COUNT_FAILURES, usernameHash, ipHash, windowStart);
    long failures = ((Number) row.get("count")).longValue();
    if (failures < properties.getMaxFailures()) {
      return Decision.allowed();
    }

    Object lastAttemptValue = row.get("max");
    Instant lastAttempt =
        lastAttemptValue == null ? now : ((Timestamp) lastAttemptValue).toInstant();
    Instant blockedUntil = lastAttempt.plus(properties.getBlockDuration());
    Duration retryAfter = Duration.between(now, blockedUntil);
    if (retryAfter.isNegative() || retryAfter.isZero()) {
      return Decision.allowed();
    }
    return new Decision(true, retryAfter);
  }

  public void recordFailure(String usernameNormalized, String ipAddress) {
    insert(usernameNormalized, ipAddress, false, "BAD_CREDENTIALS");
  }

  /** Autenticação bem-sucedida zera o contador da combinação usuário+IP. */
  public void recordSuccess(String usernameNormalized, String ipAddress) {
    jdbcTemplate.update(
        DELETE_FAILURES,
        hmacService.usernameHash(usernameNormalized),
        hmacService.ipHash(ipAddress));
    insert(usernameNormalized, ipAddress, true, null);
  }

  private void insert(
      String usernameNormalized, String ipAddress, boolean success, String failureReason) {
    Timestamp attemptedAt = Timestamp.from(clock.now());
    jdbcTemplate.update(
        INSERT,
        UUID.randomUUID(),
        hmacService.usernameHash(usernameNormalized),
        hmacService.ipHash(ipAddress),
        attemptedAt,
        success,
        failureReason);
  }
}
