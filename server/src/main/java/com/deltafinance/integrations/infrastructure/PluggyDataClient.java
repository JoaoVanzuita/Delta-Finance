package com.deltafinance.integrations.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Leitura de dados no provedor.
 *
 * <p>Endpoints usados: {@code GET /items/{id}}, {@code GET /accounts?itemId}, {@code GET
 * /bills?accountId} e {@code GET /v2/transactions} com cursor. Em transações a API usa {@code
 * dateFrom}/{@code dateTo} e o {@code next} é uma query string; não envie {@code pageSize} (a API
 * recusa com 400 e usa 500 por padrão). Nenhuma resposta bruta é propagada ao cliente; erros são
 * sanitizados.
 */
@Component
public class PluggyDataClient {

  private static final Logger LOG = LoggerFactory.getLogger(PluggyDataClient.class);
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

  public PluggyModels.PluggyAccountPage listAccounts(String apiKey, String itemId) {
    List<PluggyModels.PluggyAccount> accounts = new ArrayList<>();
    int page = 1;
    int totalPages = 1;
    boolean truncated = false;
    while (page <= totalPages) {
      if (page > MAX_PAGES) {
        truncated = true;
        break;
      }
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
        String type = node.path("type").asString("UNKNOWN");
        accounts.add(
            new PluggyModels.PluggyAccount(
                externalId,
                type == null || type.isBlank()
                    ? "UNKNOWN"
                    : type.trim().toUpperCase(java.util.Locale.ROOT),
                node.path("subtype").asString(null),
                node.path("name").asString(null),
                node.path("number").asString(null),
                PluggyModels.decimal(node, "balance"),
                node.path("currencyCode").asString("BRL"),
                node.path("creditData")));
      }
      totalPages = root.path("totalPages").asInt(1);
      if (totalPages < 1) {
        truncated = true;
        break;
      }
      page++;
    }
    return new PluggyModels.PluggyAccountPage(accounts, !truncated);
  }

  /**
   * Faturas oficiais de um cartão.
   *
   * <p>Além de inferir o ciclo quando {@code creditData.balanceCloseDate} falta, as Bills são
   * persistidas como referência de conciliação (total, mínimo, vencimento e pagamentos oficiais). A
   * leitura continua opcional: falhas não interrompem a sincronização de contas.
   */
  public List<PluggyModels.PluggyBill> listBills(String apiKey, String accountId) {
    List<PluggyModels.PluggyBill> bills = new ArrayList<>();
    int page = 1;
    int totalPages = 1;
    while (page <= totalPages && page <= MAX_PAGES) {
      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("accountId", accountId);
      params.add("pageSize", String.valueOf(PAGE_SIZE));
      params.add("page", String.valueOf(page));
      JsonNode root = getJson("/bills", null, params, apiKey);
      JsonNode results = root.isArray() ? root : root.path("results");
      if (!results.isArray()) {
        throw new PluggyException("Resposta de faturas inválida.");
      }
      for (JsonNode node : results) {
        bills.add(
            new PluggyModels.PluggyBill(
                node.path("id").asString(null),
                PluggyModels.civilDate(node, "billClosingDate"),
                PluggyModels.civilDate(node, "dueDate"),
                PluggyModels.decimal(node, "totalAmount"),
                PluggyModels.decimal(node, "minimumPaymentAmount"),
                firstText(node, "totalAmountCurrencyCode", "currencyCode", "BRL"),
                billPayments(node.path("payments"))));
      }
      totalPages = root.path("totalPages").asInt(1);
      if (totalPages < 1) {
        break;
      }
      page++;
    }
    return bills;
  }

  public PluggyModels.PluggyTransactionPage listTransactions(
      String apiKey, String accountId, LocalDate from, String after) {
    List<PluggyModels.PluggyTransaction> transactions = new ArrayList<>();
    String cursor = after;
    String next = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("accountId", accountId);
      if (from != null) {
        // A API v2 usa dateFrom/dateTo; "from" era ignorado e fazia cada sincronização
        // percorrer todo o histórico disponível.
        params.add("dateFrom", from.toString());
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
      // "next" é uma query string pronta (ex.: "?accountId=...&after=..."); enviar o
      // valor inteiro em "after" gera 400. Extraímos somente o cursor opaco.
      cursor = extractAfter(next);
      if (cursor == null || cursor.isBlank()) {
        break;
      }
    }
    return new PluggyModels.PluggyTransactionPage(
        transactions, next, next == null || next.isBlank());
  }

  private static String extractAfter(String next) {
    try {
      String encoded =
          UriComponentsBuilder.fromUriString(next).build().getQueryParams().getFirst("after");
      // A documentação pede o valor de "after" já decodificado; o builder da requisição
      // volta a codificá-lo como parâmetro normal.
      return encoded == null
          ? null
          : UriUtils.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
    } catch (RuntimeException exception) {
      return null;
    }
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
      // Diagnóstico sanitizado: endpoint e status apenas, nunca corpo ou credenciais.
      LOG.warn("Falha do provedor: endpoint={} status={}", endpoint, status);
      // 400/404 em /items/{id} significa itemId malformado ou inexistente para
      // esta aplicação Meu Pluggy — não é indisponibilidade do provedor.
      if (path.startsWith("/items/") && (status == 400 || status == 404)) {
        throw new PluggyItemInvalidException();
      }
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
      LOG.warn(
          "Falha de comunicação com o provedor: endpoint={} tipo={}",
          endpoint,
          exception.getClass().getSimpleName());
      throw new PluggyException("Falha de comunicação com o provedor.", exception);
    } catch (RuntimeException exception) {
      if (exception instanceof PluggyException pluggyException) {
        throw pluggyException;
      }
      throw new PluggyException("Resposta inválida do provedor.", exception);
    }
  }

  private static List<PluggyModels.PluggyBillPayment> billPayments(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<PluggyModels.PluggyBillPayment> payments = new ArrayList<>();
    for (JsonNode payment : node) {
      BigDecimal amount = PluggyModels.decimal(payment, "amount");
      LocalDate paymentDate = PluggyModels.civilDate(payment, "paymentDate");
      if (amount == null || paymentDate == null) {
        continue;
      }
      payments.add(
          new PluggyModels.PluggyBillPayment(
              payment.path("id").asString(null),
              paymentDate,
              amount,
              payment.path("paymentMode").asString(null),
              payment.path("valueType").asString(null),
              firstText(payment, "currencyCode", "totalAmountCurrencyCode", "BRL")));
    }
    return payments;
  }

  private static String firstText(JsonNode node, String first, String second, String fallback) {
    String value = node.path(first).asString(null);
    if (value == null || value.isBlank()) {
      value = node.path(second).asString(null);
    }
    return value == null || value.isBlank()
        ? fallback
        : value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private static String endpointKey(String path) {
    if (path.startsWith("/items/")) {
      return "items";
    }
    if (path.startsWith("/accounts")) {
      return "accounts";
    }
    if (path.startsWith("/bills")) {
      return "bills";
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
