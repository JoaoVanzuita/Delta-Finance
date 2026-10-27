package com.deltafinance.support;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

  public record BillFixture(
      String externalId,
      String closingDate,
      String dueDate,
      String totalAmount,
      String minimumPaymentAmount) {}

  public record BillPaymentFixture(
      String externalId, String paymentDate, String amount, String paymentMode, String valueType) {}

  private final MockWebServer server = new MockWebServer();
  private final Map<String, String> clients = new ConcurrentHashMap<>();
  private final Map<String, ItemFixture> items = new ConcurrentHashMap<>();
  private final Map<String, List<AccountFixture>> accountsByItem = new ConcurrentHashMap<>();
  private final Map<String, List<TransactionFixture>> transactionsByAccount =
      new ConcurrentHashMap<>();
  private final Map<String, List<BillFixture>> billsByAccount = new ConcurrentHashMap<>();
  private final Map<String, List<BillPaymentFixture>> billPaymentsByBill =
      new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> installmentMetadataByTransaction =
      new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> creditCardMetadataByTransaction =
      new ConcurrentHashMap<>();
  private final Map<String, List<Map<String, Object>>> investmentsByItem =
      new ConcurrentHashMap<>();
  private final Map<String, List<Map<String, Object>>> investmentTransactionsByInvestment =
      new ConcurrentHashMap<>();
  private final Map<String, List<Map<String, Object>>> resourcesByItem = new ConcurrentHashMap<>();
  private final Map<String, List<String>> productsByItem = new ConcurrentHashMap<>();
  private final Map<String, Instant> resourcesCollectedAtByItem = new ConcurrentHashMap<>();
  private final Set<String> resourcesCollectedAtUnset = ConcurrentHashMap.newKeySet();
  private final Map<String, Boolean> pendingAuthorizationByItem = new ConcurrentHashMap<>();
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private final List<String> dataApiKeys = new CopyOnWriteArrayList<>();
  private final Set<String> validApiKeys = ConcurrentHashMap.newKeySet();
  private final Map<String, Integer> failuresRemaining = new ConcurrentHashMap<>();
  private final Map<String, Integer> failureStatus = new ConcurrentHashMap<>();
  private final Map<String, String> nextByAccount = new ConcurrentHashMap<>();
  private final Map<String, String> accountNumbers = new ConcurrentHashMap<>();
  private final AtomicInteger authCalls = new AtomicInteger();
  private volatile boolean rotateKeys;
  private volatile String blockedPathPrefix;
  private volatile CountDownLatch blockedArrived = new CountDownLatch(0);
  private volatile CountDownLatch blockedRelease = new CountDownLatch(0);
  private final JsonMapper jsonMapper = JsonMapper.shared();

  private record ItemFixture(
      String id, String institution, String status, Instant consentExpiresAt, String errorCode) {}

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
    billsByAccount.clear();
    billPaymentsByBill.clear();
    installmentMetadataByTransaction.clear();
    creditCardMetadataByTransaction.clear();
    investmentsByItem.clear();
    investmentTransactionsByInvestment.clear();
    resourcesByItem.clear();
    productsByItem.clear();
    resourcesCollectedAtByItem.clear();
    resourcesCollectedAtUnset.clear();
    pendingAuthorizationByItem.clear();
    requests.clear();
    dataApiKeys.clear();
    validApiKeys.clear();
    failuresRemaining.clear();
    failureStatus.clear();
    nextByAccount.clear();
    accountNumbers.clear();
    authCalls.set(0);
    rotateKeys = false;
    blockedPathPrefix = null;
    blockedRelease.countDown();
    blockedArrived = new CountDownLatch(0);
    blockedRelease = new CountDownLatch(0);
  }

  public void registerClient(String clientId, String clientSecret) {
    clients.put(clientId, clientSecret);
  }

  /** Bloqueia requisições cujo path começa pelo prefixo, para testar cancelamento/timeout. */
  public void blockPath(String pathPrefix) {
    blockedPathPrefix = pathPrefix;
    blockedArrived = new CountDownLatch(1);
    blockedRelease = new CountDownLatch(1);
  }

  public boolean awaitBlocked(int seconds) throws InterruptedException {
    return blockedArrived.await(seconds, TimeUnit.SECONDS);
  }

  public void releaseBlocked() {
    blockedRelease.countDown();
  }

  /** Força o próximo cursor de transações da conta; útil para simular leitura truncada. */
  public void setTransactionsNext(String accountId, String next) {
    nextByAccount.put(accountId, next);
  }

  public void addItem(String itemId, String institution, String status) {
    items.put(
        itemId,
        new ItemFixture(itemId, institution, status, Instant.now().plusSeconds(3600), null));
  }

  public void setItemStatus(String itemId, String status) {
    updateItem(
        itemId,
        current ->
            new ItemFixture(
                current.id(),
                current.institution(),
                status,
                current.consentExpiresAt(),
                current.errorCode()));
  }

  public void setItemConsentExpiresAt(String itemId, Instant consentExpiresAt) {
    updateItem(
        itemId,
        current ->
            new ItemFixture(
                current.id(),
                current.institution(),
                current.status(),
                consentExpiresAt,
                current.errorCode()));
  }

  public void setItemErrorCode(String itemId, String errorCode) {
    updateItem(
        itemId,
        current ->
            new ItemFixture(
                current.id(),
                current.institution(),
                current.status(),
                current.consentExpiresAt(),
                errorCode));
  }

  public void addAccount(String itemId, AccountFixture account) {
    accountsByItem.computeIfAbsent(itemId, ignored -> new CopyOnWriteArrayList<>()).add(account);
  }

  public void addTransaction(String accountId, TransactionFixture transaction) {
    transactionsByAccount
        .computeIfAbsent(accountId, ignored -> new CopyOnWriteArrayList<>())
        .add(transaction);
  }

  public void addBill(String accountId, BillFixture bill) {
    billsByAccount.computeIfAbsent(accountId, ignored -> new CopyOnWriteArrayList<>()).add(bill);
  }

  public void addBillPayment(String accountId, String billExternalId, BillPaymentFixture payment) {
    billPaymentsByBill
        .computeIfAbsent(accountId + "|" + billExternalId, ignored -> new CopyOnWriteArrayList<>())
        .add(payment);
  }

  /** Adiciona uma posição de investimento ao item. */
  public void addInvestment(String itemId, Map<String, Object> investment) {
    investmentsByItem
        .computeIfAbsent(itemId, ignored -> new CopyOnWriteArrayList<>())
        .add(investment);
  }

  public void setInvestments(String itemId, List<Map<String, Object>> investments) {
    investmentsByItem.put(itemId, new CopyOnWriteArrayList<>(investments));
  }

  /** Adiciona uma movimentação a um investimento. */
  public void addInvestmentTransaction(
      String investmentExternalId, Map<String, Object> transaction) {
    investmentTransactionsByInvestment
        .computeIfAbsent(investmentExternalId, ignored -> new CopyOnWriteArrayList<>())
        .add(transaction);
  }

  public void setInvestmentTransactions(
      String investmentExternalId, List<Map<String, Object>> transactions) {
    investmentTransactionsByInvestment.put(
        investmentExternalId, new CopyOnWriteArrayList<>(transactions));
  }

  /** Recursos declarados pelo item; vazio significa que a instituição não compartilha. */
  public void setInvestmentResources(String itemId, List<Map<String, Object>> resources) {
    resourcesByItem.put(itemId, new CopyOnWriteArrayList<>(resources));
  }

  public void setItemProducts(String itemId, List<String> products) {
    productsByItem.put(itemId, List.copyOf(products));
  }

  public void setResourcesCollectedAt(String itemId, Instant resourcesCollectedAt) {
    if (resourcesCollectedAt == null) {
      resourcesCollectedAtByItem.remove(itemId);
      resourcesCollectedAtUnset.add(itemId);
    } else {
      resourcesCollectedAtUnset.remove(itemId);
      resourcesCollectedAtByItem.put(itemId, resourcesCollectedAt);
    }
  }

  public void setHasResourcesPendingAuthorization(String itemId, Boolean pending) {
    if (pending == null) {
      pendingAuthorizationByItem.remove(itemId);
    } else {
      pendingAuthorizationByItem.put(itemId, pending);
    }
  }

  public void setBills(String accountId, List<BillFixture> bills) {
    billsByAccount.put(accountId, new CopyOnWriteArrayList<>(bills));
  }

  public void setBillPayments(
      String accountId, String billExternalId, List<BillPaymentFixture> payments) {
    billPaymentsByBill.put(accountId + "|" + billExternalId, new CopyOnWriteArrayList<>(payments));
  }

  /** Sobrescreve o creditCardMetadata devolvido para uma transação específica. */
  public void setInstallments(String externalTransactionId, int installmentNumber, int total) {
    installmentMetadataByTransaction.put(
        externalTransactionId,
        Map.of("installmentNumber", installmentNumber, "totalInstallments", total));
  }

  /** Acrescenta campos ao creditCardMetadata (ex.: billForecastDate) de uma transação. */
  public void setCreditCardMetadata(String externalTransactionId, Map<String, Object> metadata) {
    creditCardMetadataByTransaction.put(externalTransactionId, metadata);
  }

  /** Sobrescreve o número do cartão (últimos quatro dígitos) devolvido pelo provedor. */
  public void setAccountNumber(String accountId, String number) {
    accountNumbers.put(accountId, number);
  }

  public void setTransactions(String accountId, List<TransactionFixture> transactions) {
    transactionsByAccount.put(accountId, new CopyOnWriteArrayList<>(transactions));
  }

  public void removeTransaction(String accountId, String externalId) {
    List<TransactionFixture> current = transactionsByAccount.getOrDefault(accountId, List.of());
    transactionsByAccount.put(
        accountId,
        new CopyOnWriteArrayList<>(
            current.stream().filter(item -> !item.externalId().equals(externalId)).toList()));
  }

  public void updateTransactionStatus(String accountId, String externalId, String status) {
    List<TransactionFixture> current = transactionsByAccount.getOrDefault(accountId, List.of());
    List<TransactionFixture> updated = new ArrayList<>();
    for (TransactionFixture item : current) {
      updated.add(
          item.externalId().equals(externalId)
              ? new TransactionFixture(
                  item.externalId(),
                  item.accountId(),
                  item.description(),
                  item.amount(),
                  item.date(),
                  status,
                  item.category())
              : item);
    }
    setTransactions(accountId, updated);
  }

  /** Faz as próximas chamadas a um prefixo falharem com o status informado. */
  public void failPath(String pathPrefix, int status, int times) {
    failuresRemaining.put(pathPrefix, times);
    failureStatus.put(pathPrefix, status);
  }

  /** Faz o provedor emitir uma nova API Key a cada POST /auth. */
  public void rotateKeys(boolean enabled) {
    this.rotateKeys = enabled;
  }

  /** Invalida as API Keys emitidas; a próxima chamada de dados recebe 401. */
  public void invalidateApiKeys() {
    validApiKeys.clear();
  }

  public int authCalls() {
    return authCalls.get();
  }

  public List<String> dataApiKeys() {
    return List.copyOf(dataApiKeys);
  }

  public List<RecordedRequest> requests() {
    return List.copyOf(requests);
  }

  private void updateItem(String itemId, java.util.function.UnaryOperator<ItemFixture> update) {
    items.computeIfPresent(itemId, (key, current) -> update.apply(current));
  }

  private final class StubDispatcher extends Dispatcher {
    @Override
    public MockResponse dispatch(RecordedRequest request) {
      requests.add(request);
      String path = request.getRequestUrl() == null ? "" : request.getRequestUrl().encodedPath();
      String apiKey = request.getHeader("X-API-KEY");
      if (apiKey != null) {
        dataApiKeys.add(apiKey);
      }

      String blockedPrefix = blockedPathPrefix;
      if (blockedPrefix != null && path.startsWith(blockedPrefix)) {
        blockedArrived.countDown();
        try {
          blockedRelease.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
        }
      }

      MockResponse failure = failure(path);
      if (failure != null) {
        return failure;
      }

      if (path.startsWith("/auth")) {
        return auth(request);
      }
      if (requiresApiKey(path) && (apiKey == null || !validApiKeys.contains(apiKey))) {
        return response(401, Map.of("message", "invalid api key"));
      }
      if (path.startsWith("/items/") && path.endsWith("/resources")) {
        return resources(path.substring("/items/".length(), path.length() - "/resources".length()));
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
      if (path.startsWith("/bills")) {
        return bills(request.getRequestUrl().queryParameter("accountId"));
      }
      if (path.startsWith("/investments/") && path.endsWith("/transactions")) {
        String remainder = path.substring("/investments/".length());
        String investmentId = remainder.substring(0, remainder.indexOf('/'));
        return investmentTransactions(investmentId);
      }
      if (path.startsWith("/investments")) {
        return investments(request.getRequestUrl().queryParameter("itemId"));
      }
      return response(404, Map.of("message", "not found"));
    }

    private MockResponse failure(String path) {
      for (Map.Entry<String, Integer> entry : failuresRemaining.entrySet()) {
        if (path.startsWith(entry.getKey()) && entry.getValue() > 0) {
          failuresRemaining.computeIfPresent(entry.getKey(), (key, remaining) -> remaining - 1);
          int status = failureStatus.getOrDefault(entry.getKey(), 500);
          return response(status, Map.of("message", "injected failure"));
        }
      }
      return null;
    }

    private boolean requiresApiKey(String path) {
      return path.startsWith("/items/")
          || path.startsWith("/accounts")
          || path.startsWith("/bills")
          || path.startsWith("/investments")
          || path.startsWith("/v2/transactions");
    }

    private MockResponse auth(RecordedRequest request) {
      authCalls.incrementAndGet();
      String body = request.getBody().readUtf8();
      try {
        var node = jsonMapper.readTree(body);
        String clientId = node.path("clientId").asString(null);
        String secret = node.path("clientSecret").asString(null);
        if (clientId == null || secret == null || !secret.equals(clients.get(clientId))) {
          return response(401, Map.of("message", "invalid credentials"));
        }
        String apiKey = rotateKeys ? "key-" + clientId + "-" + authCalls.get() : "key-" + clientId;
        validApiKeys.add(apiKey);
        return response(200, Map.of("apiKey", apiKey));
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
      body.put(
          "products",
          productsByItem.getOrDefault(
              item.id(),
              List.of(
                  "ACCOUNTS",
                  "CREDIT_CARDS",
                  "TRANSACTIONS",
                  "INVESTMENTS",
                  "INVESTMENTS_TRANSACTIONS")));
      Instant resourcesCollectedAt =
          resourcesCollectedAtUnset.contains(item.id())
              ? null
              : resourcesCollectedAtByItem.getOrDefault(item.id(), Instant.now());
      body.put(
          "resourcesCollectedAt",
          resourcesCollectedAt == null ? null : resourcesCollectedAt.toString());
      body.put(
          "hasResourcesPendingAuthorization",
          pendingAuthorizationByItem.getOrDefault(item.id(), false));
      if (item.errorCode() != null) {
        body.put("error", Map.of("code", item.errorCode()));
      }
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
        if ("CREDIT".equals(account.type())) {
          body.put("number", accountNumbers.getOrDefault(account.externalId(), "4242"));
        }
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

    private MockResponse bills(String accountId) {
      List<BillFixture> bills = billsByAccount.getOrDefault(accountId, List.of());
      List<Map<String, Object>> results = new ArrayList<>();
      for (BillFixture bill : bills) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", bill.externalId());
        body.put("billClosingDate", bill.closingDate());
        body.put("dueDate", bill.dueDate());
        body.put("totalAmount", bill.totalAmount());
        body.put("totalAmountCurrencyCode", "BRL");
        body.put("minimumPaymentAmount", bill.minimumPaymentAmount());
        List<BillPaymentFixture> payments =
            billPaymentsByBill.getOrDefault(accountId + "|" + bill.externalId(), List.of());
        List<Map<String, Object>> paymentResults = new ArrayList<>();
        for (BillPaymentFixture payment : payments) {
          Map<String, Object> paymentBody = new LinkedHashMap<>();
          paymentBody.put("id", payment.externalId());
          paymentBody.put("paymentDate", payment.paymentDate());
          paymentBody.put("amount", payment.amount());
          paymentBody.put("paymentMode", payment.paymentMode());
          paymentBody.put("valueType", payment.valueType());
          paymentBody.put("currencyCode", "BRL");
          paymentResults.add(paymentBody);
        }
        body.put("payments", paymentResults);
        results.add(body);
      }
      return response(
          200, Map.of("results", results, "page", 1, "totalPages", 1, "total", results.size()));
    }

    private MockResponse resources(String itemId) {
      List<Map<String, Object>> resources = resourcesByItem.getOrDefault(itemId, List.of());
      return response(
          200, Map.of("results", resources, "page", 1, "totalPages", 1, "total", resources.size()));
    }

    private MockResponse investments(String itemId) {
      List<Map<String, Object>> investments = investmentsByItem.getOrDefault(itemId, List.of());
      return response(
          200,
          Map.of("results", investments, "page", 1, "totalPages", 1, "total", investments.size()));
    }

    private MockResponse investmentTransactions(String investmentId) {
      List<Map<String, Object>> transactions =
          investmentTransactionsByInvestment.getOrDefault(investmentId, List.of());
      return response(
          200,
          Map.of(
              "results", transactions, "page", 1, "totalPages", 1, "total", transactions.size()));
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
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("totalInstallments", 1);
        metadata.put("installmentNumber", 1);
        metadata.putAll(
            installmentMetadataByTransaction.getOrDefault(transaction.externalId(), Map.of()));
        metadata.putAll(
            creditCardMetadataByTransaction.getOrDefault(transaction.externalId(), Map.of()));
        body.put("creditCardMetadata", metadata);
        results.add(body);
      }
      return response(
          200, Map.of("results", results, "next", nextByAccount.getOrDefault(accountId, "")));
    }

    private MockResponse response(int status, Object body) {
      return new MockResponse()
          .setResponseCode(status)
          .setHeader("Content-Type", "application/json")
          .setBody(jsonMapper.writeValueAsString(body));
    }
  }
}
