package com.deltafinance.integrations.infrastructure;

import com.deltafinance.shared.time.Clock;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cache em memória da API Key temporária, por integração.
 *
 * <p>TTL de 110 minutos (a chave do provedor dura 2 h), máximo de 100 entradas. O valor carrega
 * {@code userId} e {@code integrationId} e é validado na leitura: uma chave jamais é usada por
 * outra integração/usuário. Nunca é persistida nem retornada à interface.
 */
@Component
public class ApiKeyCache {

  private static final Duration TTL = Duration.ofMinutes(110);
  private static final int MAX_ENTRIES = 100;

  record CachedApiKey(UUID userId, UUID integrationId, String apiKey, Instant expiresAt) {}

  private final Cache<UUID, CachedApiKey> cache =
      Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(MAX_ENTRIES).build();
  private final Clock clock;

  public ApiKeyCache(Clock clock) {
    this.clock = clock;
  }

  public Optional<String> get(UUID userId, UUID integrationId) {
    CachedApiKey cached = cache.getIfPresent(integrationId);
    if (cached == null) {
      return Optional.empty();
    }
    if (!cached.userId().equals(userId)
        || !cached.integrationId().equals(integrationId)
        || !cached.expiresAt().isAfter(clock.now())) {
      cache.invalidate(integrationId);
      return Optional.empty();
    }
    return Optional.of(cached.apiKey());
  }

  public void put(UUID userId, UUID integrationId, String apiKey) {
    cache.put(
        integrationId, new CachedApiKey(userId, integrationId, apiKey, clock.now().plus(TTL)));
  }

  public void invalidate(UUID integrationId) {
    cache.invalidate(integrationId);
  }

  public long estimatedSize() {
    return cache.estimatedSize();
  }
}
