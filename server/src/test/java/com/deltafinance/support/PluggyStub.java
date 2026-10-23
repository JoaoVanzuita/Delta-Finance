package com.deltafinance.support;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stub HTTP do provedor Pluggy para testes de integração.
 *
 * <p>Um único servidor atende múltiplas credenciais/itens, permitindo simular duas integrações
 * independentes. Registra as requisições e as API Keys usadas para provar isolamento.
 */
public final class PluggyStub {

  public record AccountFixture(
      String externalId,
      String type,
      String subtype,
      String name,
      String balance,
      Map<String, Object> creditData) {}

  public record TransactionFixture(
      String externalId,
      String accountId,
      String description,
      String amount,
      String date,
      String status,
      String category) {}

  private final MockWebServer server = new MockWebServer();
  private final Map<String, String> clients = new ConcurrentHashMap<>();
  private final Map<String, ItemFixture> items = new ConcurrentHashMap<>();
  private final Map<String, List<AccountFixture>> accountsByItem = new ConcurrentHashMap<>();
  private final Map<String, List<TransactionFixture>> transactionsByAccount =
      new ConcurrentHashMap<>();
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private final List<String> dataApiKeys = new CopyOnWriteArrayList<>();
  private final JsonMapper jsonMapper = JsonMapper.shared();

  private record ItemFixture(
      String id, String institution, String status, Instant consentExpiresAt) {}

  public PluggyStub() {
    try {
      server.setDispatcher(new StubDispatcher());
      server.start();
    } catch (IOException exception) {
      throw new IllegalStateException("Não foi possível iniciar o stub Pluggy", exception);
    }
  }

  public String baseUrl() {
    String url = server.url("/").toString();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  public void stop() {
    try {
      server.shutdown();
    } catch (IOException ignored) {
      // encerramento de teste
    }
  }

  public void reset() {
    clients.clear();
    items.clear();
    accountsByItem.clear();
    transactionsByAccount.clear();
    requests.clear();
    dataApiKeys.clear();
  }

  public void registerClient(String clientId, String clientSecret) {
    clients.put(clientId, clientSecret);
  }

  public void addItem(String itemId, String institution, String status) {
    items.put(
        itemId, new ItemFixture(itemId, institution, status, Instant.now().plusSeconds(3600)));
  }

  public void addAccount(String itemId, AccountFixture account) {
    accountsByItem.computeIfAbsent(itemId, ignored -> new CopyOnWriteArrayList<>()).add(account);
  }

  public void addTransaction(String accountId, TransactionFixture transaction) {
    transactionsByAccount
        .computeIfAbsent(accountId, ignored -> new CopyOnWriteArrayList<>())
        .add(transaction);
  }

  public List<String> dataApiKeys() {
    return List.copyOf(dataApiKeys);
  }

  public List<RecordedRequest> requests() {
    return List.copyOf(requests);
  }

  private final class StubDispatcher extends Dispatcher {
    @Override
    public MockResponse dispatch(RecordedRequest request) {
      requests.add(request);
      String path = request.getPath() == null ? "" : request.getPath();
      String apiKey = request.getHeader("X-API-KEY");
      if (apiKey != null) {
        dataApiKeys.add(apiKey);
      }

      if (path.startsWith("/auth")) {
        return auth(request);
      }
      if (path.startsWith("/items/")) {
        return item(path.substring("/items/".length()));
      }
      if (path.startsWith("/accounts")) {
        return accounts(request.getRequestUrl().queryParameter("itemId"));
      }
      if (path.startsWith("/v2/transactions")) {
        return transactions(request.getRequestUrl().queryParameter("accountId"));
      }
      return response(404, Map.of("message", "not found"));
    }

    private MockResponse auth(RecordedRequest request) {
      String body = request.getBody().readUtf8();
      try {
        var node = jsonMapper.readTree(body);
        String clientId = node.path("clientId").asString(null);
        String secret = node.path("clientSecret").asString(null);
        if (clientId == null || secret == null || !secret.equals(clients.get(clientId))) {
          return response(401, Map.of("message", "invalid credentials"));
        }
        return response(200, Map.of("apiKey", "key-" + clientId));
      } catch (RuntimeException exception) {
        return response(400, Map.of("message", "bad request"));
      }
    }

    private MockResponse item(String itemId) {
      ItemFixture item = items.get(itemId);
      if (item == null) {
        return response(404, Map.of("message", "item not found"));
      }
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("id", item.id());
      body.put("status", item.status());
      body.put("consentExpiresAt", item.consentExpiresAt().toString());
      body.put("lastUpdatedAt", Instant.now().toString());
      body.put("nextAutoSyncAt", Instant.now().plusSeconds(3600).toString());
      body.put("connector", Map.of("name", item.institution()));
      return response(200, body);
    }

    private MockResponse accounts(String itemId) {
      List<AccountFixture> accounts = accountsByItem.getOrDefault(itemId, List.of());
      List<Map<String, Object>> results = new ArrayList<>();
      for (AccountFixture account : accounts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", account.externalId());
        body.put("type", account.type());
        body.put("subtype", account.subtype());
        body.put("name", account.name());
        body.put("balance", account.balance());
        body.put("currencyCode", "BRL");
        if (account.creditData() != null) {
          body.put("creditData", account.creditData());
        }
        results.add(body);
      }
      return response(
          200, Map.of("results", results, "page", 1, "totalPages", 1, "total", results.size()));
    }

    private MockResponse transactions(String accountId) {
      List<TransactionFixture> transactions =
          transactionsByAccount.getOrDefault(accountId, List.of());
      List<Map<String, Object>> results = new ArrayList<>();
      for (TransactionFixture transaction : transactions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", transaction.externalId());
        body.put("accountId", transaction.accountId());
        body.put("description", transaction.description());
        body.put("amount", transaction.amount());
        body.put("date", transaction.date());
        body.put("status", transaction.status());
        body.put("category", transaction.category());
        body.put("currencyCode", "BRL");
        body.put("creditCardMetadata", Map.of("totalInstallments", 1, "installmentNumber", 1));
        results.add(body);
      }
      return response(200, Map.of("results", results, "next", ""));
    }

    private MockResponse response(int status, Object body) {
      return new MockResponse()
          .setResponseCode(status)
          .setHeader("Content-Type", "application/json")
          .setBody(jsonMapper.writeValueAsString(body));
    }
  }
}
