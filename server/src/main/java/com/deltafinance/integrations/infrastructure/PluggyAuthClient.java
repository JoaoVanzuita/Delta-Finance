package com.deltafinance.integrations.infrastructure;

import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Autenticação no provedor: {@code POST /auth} com Client ID/Secret, sempre no servidor.
 *
 * <p>A API Key resultante é mantida apenas no cache em memória e nunca é devolvida à interface.
 */
@Component
public class PluggyAuthClient {

  private static final String TABLE = "pluggy_integration";
  private static final String CLIENT_ID_COLUMN = "client_id";
  private static final String CLIENT_SECRET_COLUMN = "client_secret";

  private final RestClient restClient;
  private final ApiKeyCache apiKeyCache;
  private final CryptoService cryptoService;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public PluggyAuthClient(
      RestClient pluggyRestClient,
      ApiKeyCache apiKeyCache,
      CryptoService cryptoService,
      JsonMapper jsonMapper,
      Clock clock) {
    this.restClient = pluggyRestClient;
    this.apiKeyCache = apiKeyCache;
    this.cryptoService = cryptoService;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  public String getApiKey(PluggyIntegration integration) {
    Objects.requireNonNull(integration, "integration");
    var cached = apiKeyCache.get(integration.getUserId(), integration.getId());
    if (cached.isPresent()) {
      return cached.get();
    }
    if (!integration.hasCredentials()) {
      throw new PluggyCredentialException();
    }

    String clientId = decrypt(integration, integration.getClientIdEnc(), CLIENT_ID_COLUMN);
    String clientSecret =
        decrypt(integration, integration.getClientSecretEnc(), CLIENT_SECRET_COLUMN);
    String apiKey = requestApiKey(clientId, clientSecret);
    apiKeyCache.put(integration.getUserId(), integration.getId(), apiKey);
    return apiKey;
  }

  /** Valida credenciais sem salvar. */
  public void validateCredentials(String clientId, String clientSecret) {
    requestApiKey(clientId, clientSecret);
  }

  public void invalidate(PluggyIntegration integration) {
    apiKeyCache.invalidate(integration.getId());
  }

  /** Invalida e obtém uma nova API Key (após 401 em chamada de dados). */
  public String refreshApiKey(PluggyIntegration integration) {
    invalidate(integration);
    return getApiKey(integration);
  }

  private String decrypt(PluggyIntegration integration, byte[] ciphertext, String column) {
    try {
      return cryptoService.decryptString(
          ciphertext,
          new CryptoContext(integration.getUserId(), TABLE, column, integration.getId()));
    } catch (CryptoException exception) {
      throw new PluggyCredentialException();
    }
  }

  private String requestApiKey(String clientId, String clientSecret) {
    String body;
    try {
      body =
          restClient
              .post()
              .uri("/auth")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("clientId", clientId, "clientSecret", clientSecret))
              .retrieve()
              .body(String.class);
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      if (status == 401 || status == 403) {
        throw new PluggyCredentialException();
      }
      if (status == 429) {
        throw new PluggyRateLimitedException(retryAfter(exception));
      }
      throw new PluggyException("Provedor indisponível para autenticação.", exception);
    } catch (RestClientException exception) {
      throw new PluggyException("Falha de comunicação com o provedor.", exception);
    }

    JsonNode response = parse(body);
    String apiKey = response.path("apiKey").asString(null);
    if (apiKey == null || apiKey.isBlank()) {
      throw new PluggyException("Resposta de autenticação sem credencial temporária.");
    }
    return apiKey;
  }

  private JsonNode parse(String body) {
    if (body == null || body.isBlank()) {
      throw new PluggyException("Resposta vazia do provedor.");
    }
    try {
      return jsonMapper.readTree(body);
    } catch (RuntimeException exception) {
      throw new PluggyException("Resposta inválida do provedor.", exception);
    }
  }

  Duration retryAfter(RestClientResponseException exception) {
    var headers = exception.getResponseHeaders();
    if (headers == null) {
      return Duration.ofSeconds(60);
    }
    String retryAfter = headers.getFirst("Retry-After");
    if (retryAfter != null) {
      try {
        return Duration.ofSeconds(Long.parseLong(retryAfter));
      } catch (NumberFormatException ignored) {
        // cai para RateLimit-Reset
      }
    }
    String reset = headers.getFirst("RateLimit-Reset");
    if (reset != null) {
      try {
        return Duration.ofSeconds(
            Math.max(1, Long.parseLong(reset) - clock.now().getEpochSecond()));
      } catch (NumberFormatException ignored) {
        // usa o padrão
      }
    }
    return Duration.ofSeconds(60);
  }
}
