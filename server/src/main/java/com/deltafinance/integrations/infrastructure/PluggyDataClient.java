package com.deltafinance.integrations.infrastructure;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Leitura de dados no provedor.
 *
 * <p>Endpoints usados: {@code GET /items/{id}}, {@code GET /accounts?itemId} e {@code GET
 * /v2/transactions} com cursor. Nenhuma resposta bruta é propagada ao cliente; erros são
 * sanitizados.
 */
@Component
public class PluggyDataClient {

  private static final int PAGE_SIZE = 500;
  private static final int MAX_PAGES = 100;

  private final RestClient restClient;
  private final JsonMapper jsonMapper;
  private final PluggyRateLimiter rateLimiter;

  public PluggyDataClient(
      RestClient pluggyRestClient, JsonMapper jsonMapper, PluggyRateLimiter rateLimiter) {
    this.restClient = pluggyRestClient;
    this.jsonMapper = jsonMapper;
    this.rateLimiter = rateLimiter;
  }

  public PluggyModels.PluggyItem getItem(String apiKey, String itemId) {
    JsonNode node = getJson("/items/{id}", itemId, new LinkedMultiValueMap<>(), apiKey);
    String institutionName = node.path("connector").path("name").asString(null);
    if (institutionName == null || institutionName.isBlank()) {
      institutionName = node.path("institution").asString(null);
    }
    return new PluggyModels.PluggyItem(
        node.path("id").asString(itemId),
        node.path("status").asString(null),
        PluggyModels.instant(node, "consentExpiresAt"),
        PluggyModels.instant(node, "lastUpdatedAt"),
        PluggyModels.instant(node, "nextAutoSyncAt"),
        institutionName,
        node.path("error").path("code").asString(null));
  }

  public List<PluggyModels.PluggyAccount> listAccounts(String apiKey, String itemId) {
    List<PluggyModels.PluggyAccount> accounts = new ArrayList<>();
    int page = 1;
    int totalPages = 1;
    while (page <= totalPages && page <= MAX_PAGES) {
      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("itemId", itemId);
      params.add("pageSize", String.valueOf(PAGE_SIZE));
      params.add("page", String.valueOf(page));
      JsonNode root = getJson("/accounts", null, params, apiKey);
      JsonNode results = root.isArray() ? root : root.path("results");
      if (!results.isArray()) {
        throw new PluggyException("Resposta de contas inválida.");
      }
      for (JsonNode node : results) {
        String externalId = node.path("id").asString(null);
        if (externalId == null || externalId.isBlank()) {
          continue;
        }
        String type = node.path("type").asString("BANK");
        accounts.add(
            new PluggyModels.PluggyAccount(
                externalId,
                "CREDIT".equalsIgnoreCase(type) ? "CREDIT" : "BANK",
                node.path("subtype").asString(null),
                node.path("name").asString(null),
                PluggyModels.decimal(node, "balance"),
                node.path("currencyCode").asString("BRL"),
                node.path("creditData")));
      }
      totalPages = root.path("totalPages").asInt(1);
      if (totalPages < 1) {
        break;
      }
      page++;
    }
    return accounts;
  }

  public PluggyModels.PluggyTransactionPage listTransactions(
      String apiKey, String accountId, LocalDate from, String after) {
    List<PluggyModels.PluggyTransaction> transactions = new ArrayList<>();
    String cursor = after;
    String next = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("accountId", accountId);
      params.add("pageSize", String.valueOf(PAGE_SIZE));
      if (from != null) {
        params.add("from", from.toString());
      }
      if (cursor != null && !cursor.isBlank()) {
        params.add("after", cursor);
      }
      JsonNode root = getJson("/v2/transactions", null, params, apiKey);
      JsonNode results = root.isArray() ? root : root.path("results");
      if (!results.isArray()) {
        throw new PluggyException("Resposta de transações inválida.");
      }
      for (JsonNode node : results) {
        String externalId = node.path("id").asString(null);
        if (externalId == null || externalId.isBlank()) {
          continue;
        }
        transactions.add(
            new PluggyModels.PluggyTransaction(
                externalId,
                node.path("accountId").asString(accountId),
                node.path("description").asString(null),
                PluggyModels.decimal(node, "amount"),
                PluggyModels.date(node, "date"),
                node.path("status").asString(null),
                node.path("category").asString(null),
                node.path("type").asString(null),
                node.path("currencyCode").asString("BRL"),
                node.path("creditCardMetadata")));
      }
      next = root.path("next").asString(null);
      if (next == null || next.isBlank()) {
        break;
      }
      cursor = next;
    }
    return new PluggyModels.PluggyTransactionPage(transactions, next);
  }

  private JsonNode getJson(
      String path, String itemId, MultiValueMap<String, String> params, String apiKey) {
    String endpoint = endpointKey(path);
    rateLimiter.acquire(endpoint);
    String uri =
        UriComponentsBuilder.fromPath(path)
            .queryParams(params)
            .buildAndExpand(itemId == null ? java.util.Map.of() : java.util.Map.of("id", itemId))
            .toUriString();
    try {
      String body =
          restClient
              .get()
              .uri(uri)
              .header("X-API-KEY", apiKey)
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(String.class);
      if (body == null || body.isBlank()) {
        throw new PluggyException("Resposta vazia do provedor.");
      }
      rateLimiter.onSuccess(endpoint);
      return jsonMapper.readTree(body);
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      if (status == 401 || status == 403) {
        throw new PluggyUnauthorizedException();
      }
      if (status == 429) {
        Duration retryAfter = retryAfter(exception);
        rateLimiter.onRateLimited(endpoint, retryAfter);
        throw new PluggyRateLimitedException(retryAfter);
      }
      rateLimiter.onTransientFailure(endpoint);
      throw new PluggyException("Provedor indisponível.", exception);
    } catch (RestClientException exception) {
      rateLimiter.onTransientFailure(endpoint);
      throw new PluggyException("Falha de comunicação com o provedor.", exception);
    } catch (RuntimeException exception) {
      if (exception instanceof PluggyException pluggyException) {
        throw pluggyException;
      }
      throw new PluggyException("Resposta inválida do provedor.", exception);
    }
  }

  private static String endpointKey(String path) {
    if (path.startsWith("/items/")) {
      return "items";
    }
    if (path.startsWith("/accounts")) {
      return "accounts";
    }
    if (path.startsWith("/v2/transactions")) {
      return "transactions";
    }
    return path;
  }

  private static Duration retryAfter(RestClientResponseException exception) {
    HttpHeaders headers = exception.getResponseHeaders();
    if (headers == null) {
      return Duration.ofSeconds(60);
    }
    String retryAfter = headers.getFirst("Retry-After");
    if (retryAfter != null) {
      try {
        return Duration.ofSeconds(Math.max(1, Long.parseLong(retryAfter)));
      } catch (NumberFormatException ignored) {
        // usa o padrão
      }
    }
    String reset = headers.getFirst("RateLimit-Reset");
    if (reset != null) {
      try {
        long seconds = Long.parseLong(reset) - java.time.Instant.now().getEpochSecond();
        return Duration.ofSeconds(Math.max(1, seconds));
      } catch (NumberFormatException ignored) {
        // usa o padrão
      }
    }
    return Duration.ofSeconds(60);
  }
}
