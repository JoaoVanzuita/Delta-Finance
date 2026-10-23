package com.deltafinance.integrations.infrastructure;

import com.deltafinance.shared.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limita a taxa de chamadas por endpoint do provedor e aplica recuo progressivo.
 *
 * <p>Protege contra 429 e indisponibilidade sem bloquear o usuário por tempo indefinido: ao esgotar
 * a cota ou durante um backoff, a chamada falha rápido com {@link PluggyRateLimitedException} e o
 * job é reagendado. O estado é local ao processo; a reconciliação programada permanece como rede de
 * segurança.
 */
@Component
public class PluggyRateLimiter {

  private static final int DEFAULT_PERMIT_PER_MINUTE = 240;
  private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(1);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final Map<String, Instant> backoffUntil = new ConcurrentHashMap<>();
  private final Map<String, Integer> failures = new ConcurrentHashMap<>();
  private final Clock clock;

  public PluggyRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /**
   * Reserva uma chamada. Lança {@link PluggyRateLimitedException} quando a cota ou o backoff
   * impedem a chamada agora.
   */
  public void acquire(String endpoint) {
    String key = normalize(endpoint);
    Instant now = clock.now();
    Instant blockedUntil = backoffUntil.get(key);
    if (blockedUntil != null && blockedUntil.isAfter(now)) {
      throw new PluggyRateLimitedException(Duration.between(now, blockedUntil));
    }
    Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
    synchronized (window) {
      if (now.isAfter(window.startedAt.plus(Duration.ofMinutes(1)))) {
        window.startedAt = now;
        window.count = 0;
      }
      if (window.count >= DEFAULT_PERMIT_PER_MINUTE) {
        Duration wait = Duration.between(now, window.startedAt.plus(Duration.ofMinutes(1)));
        onRateLimited(key, wait);
        throw new PluggyRateLimitedException(wait);
      }
      window.count++;
    }
  }

  /** Registra 429/limite e ativa backoff conforme o {@code Retry-After}. */
  public void onRateLimited(String endpoint, Duration retryAfter) {
    String key = normalize(endpoint);
    Duration wait = retryAfter == null || retryAfter.isNegative() ? DEFAULT_BACKOFF : retryAfter;
    backoffUntil.put(key, clock.now().plus(wait));
  }

  /** Registra sucesso: zera falhas consecutivas do endpoint. */
  public void onSuccess(String endpoint) {
    failures.remove(normalize(endpoint));
  }

  /** Registra falha temporária e aplica backoff exponencial local. */
  public void onTransientFailure(String endpoint) {
    String key = normalize(endpoint);
    int count = failures.merge(key, 1, Integer::sum);
    long multiplier = 1L << Math.min(6, Math.max(0, count - 1));
    Duration wait = DEFAULT_BACKOFF.multipliedBy(multiplier);
    if (wait.compareTo(MAX_BACKOFF) > 0) {
      wait = MAX_BACKOFF;
    }
    backoffUntil.put(key, clock.now().plus(wait));
  }

  /** Limpa estado local (usado por testes para isolar cenários). */
  public void reset() {
    windows.clear();
    backoffUntil.clear();
    failures.clear();
  }

  private static String normalize(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      return "unknown";
    }
    String trimmed = endpoint.trim();
    int query = trimmed.indexOf('?');
    return query >= 0 ? trimmed.substring(0, query) : trimmed;
  }

  private static final class Window {
    private Instant startedAt;
    private int count;

    private Window(Instant startedAt) {
      this.startedAt = startedAt;
    }
  }
}
