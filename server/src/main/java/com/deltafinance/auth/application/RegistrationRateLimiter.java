package com.deltafinance.auth.application;

import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.TooManyRequestsException;
import com.deltafinance.shared.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limite de cadastros por IP antes do custo de Argon2id.
 *
 * <p>Controle em memória, adequado à implantação de instância única do MVP. O IP é guardado apenas
 * como HMAC e o mapa é podado a cada verificação. Reinícios zeram a janela; para múltiplas
 * instâncias seria necessário persistir o contador.
 */
@Component
public class RegistrationRateLimiter {

  private final Clock clock;
  private final IndexHmacService hmacService;
  private final int maxAttempts;
  private final Duration window;
  private final ConcurrentMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

  public RegistrationRateLimiter(
      Clock clock,
      IndexHmacService hmacService,
      @Value("${delta.security.registration.max-attempts:5}") int maxAttempts,
      @Value("${delta.security.registration.window:1h}") Duration window) {
    this.clock = clock;
    this.hmacService = hmacService;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.window = window;
  }

  /**
   * Registra a tentativa e falha com {@code 429} quando o IP já atingiu o limite. Deve ser chamado
   * antes de qualquer hash de senha.
   */
  public void checkAndRecord(String ipAddress) {
    Instant now = clock.now();
    String key = HexFormat.of().formatHex(hmacService.ipHash(ipAddress));
    Deque<Instant> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());

    synchronized (timestamps) {
      Instant cutoff = now.minus(window);
      while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
        timestamps.removeFirst();
      }
      if (timestamps.size() >= maxAttempts) {
        Instant retryAt = timestamps.peekFirst().plus(window);
        long retryAfter = Math.max(1, Duration.between(now, retryAt).toSeconds());
        throw new TooManyRequestsException(
            "Muitas tentativas de cadastro. Aguarde antes de tentar novamente.", retryAfter);
      }
      timestamps.addLast(now);
    }
  }
}
