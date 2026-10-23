package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.invoices.application.ProviderBillImportService;
import com.deltafinance.invoices.application.ProviderBillImportService.ProviderBill;
import com.deltafinance.invoices.application.ProviderBillImportService.ProviderBillItem;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PostgresTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Bills opcionais conciliados, sem órfãos e sem reescrever fatura paga (T123). */
@AutoConfigureMockMvc
class InvoiceProviderIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.bills";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-bills";

  @DynamicPropertySource
  static void pluggyProperties(DynamicPropertyRegistry registry) {
    registry.add("delta.pluggy.base-url", PLUGGY::baseUrl);
  }

  @AfterAll
  static void stopStub() {
    PLUGGY.stop();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private PluggyRateLimiter rateLimiter;
  @Autowired private ProviderBillImportService providerBillImportService;

  private AuthTestClient alice;
  private String cardId;
  private String bankId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem("item-alice", "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-bank", "BANK", "CHECKING_ACCOUNT", "Conta Alfa", "1000.00", null));
    PLUGGY.addAccount(
        "item-alice",
        new AccountFixture("acc-card", "CREDIT", "CREDIT_CARD", "Cartão Alfa", "0.00", null));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice
        .post("/api/v1/connections", Map.of("itemId", "item-alice"))
        .andExpect(status().isCreated());
    for (JsonNode account : accounts().path("items")) {
      if ("CREDIT".equals(account.path("type").asString())) {
        cardId = account.path("id").asString();
      }
      if ("BANK".equals(account.path("type").asString())) {
        bankId = account.path("id").asString();
      }
    }
    alice
        .put(
            "/api/v1/cards/" + cardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "5000.00"))
        .andExpect(status().isOk());
  }

  @Test
  void importaBillReconciliaPorProviderIdSemDuplicar() throws Exception {
    String transactionId = createCardTransaction("-88.00", "2026-10-03", "Loja A");
    String invoiceId = invoices().path("items").get(0).path("id").asString();

    ProviderBill bill =
        new ProviderBill(
            "bill-1",
            List.of(
                new ProviderBillItem(
                    "prov-1",
                    UUID.fromString(transactionId),
                    "Loja A",
                    new BigDecimal("88.00"),
                    LocalDate.parse("2026-10-03"))));
    assertThat(providerBillImportService.importBill(userId(), UUID.fromString(cardId), bill))
        .isEqualTo(1);

    JsonNode detail = invoiceDetail(invoiceId);
    assertThat(detail.path("source").asString()).isEqualTo("PROVIDER");
    assertThat(detail.path("total").asString()).isEqualTo("88.00");
    assertThat(detail.path("items")).hasSize(1);
    assertThat(detail.path("items").get(0).path("source").asString()).isEqualTo("PROVIDER_BILL");

    assertThat(providerBillImportService.importBill(userId(), UUID.fromString(cardId), bill))
        .isZero();
    assertThat(invoiceDetail(invoiceId).path("items")).hasSize(1);
  }

  @Test
  void reconciliaPorFingerprintQuandoBillNaoTrazTransacao() throws Exception {
    createCardTransaction("-50.00", "2026-10-03", "Loja X");
    String invoiceId = invoices().path("items").get(0).path("id").asString();

    ProviderBill bill =
        new ProviderBill(
            "bill-2",
            List.of(
                new ProviderBillItem(
                    "prov-2",
                    null,
                    "Loja X",
                    new BigDecimal("50.00"),
                    LocalDate.parse("2026-10-03"))));
    assertThat(providerBillImportService.importBill(userId(), UUID.fromString(cardId), bill))
        .isEqualTo(1);
    assertThat(invoiceDetail(invoiceId).path("items").get(0).path("source").asString())
        .isEqualTo("PROVIDER_BILL");
  }

  @Test
  void rejeitaItemOrfaoSemPersistir() throws Exception {
    ProviderBill bill =
        new ProviderBill(
            "bill-3",
            List.of(
                new ProviderBillItem(
                    "prov-3",
                    null,
                    "Compra inexistente",
                    new BigDecimal("99.00"),
                    LocalDate.parse("2026-10-03"))));
    assertThatThrownBy(
            () -> providerBillImportService.importBill(userId(), UUID.fromString(cardId), bill))
        .isInstanceOf(ValidationException.class);
    assertThat(count("select count(*) from invoice_item")).isZero();
  }

  @Test
  void naoReescreveFaturaPaga() throws Exception {
    String transactionId = createCardTransaction("-40.00", "2026-10-03", "Loja Paga");
    String invoiceId = invoices().path("items").get(0).path("id").asString();
    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "40.00", "paidOn", "2026-10-20"))
        .andExpect(status().isCreated());
    assertThat(invoiceDetail(invoiceId).path("status").asString()).isEqualTo("PAID");

    ProviderBill bill =
        new ProviderBill(
            "bill-4",
            List.of(
                new ProviderBillItem(
                    "prov-4",
                    UUID.fromString(transactionId),
                    "Loja Paga",
                    new BigDecimal("40.00"),
                    LocalDate.parse("2026-10-03"))));
    assertThatThrownBy(
            () -> providerBillImportService.importBill(userId(), UUID.fromString(cardId), bill))
        .isInstanceOf(ConflictException.class);

    JsonNode detail = invoiceDetail(invoiceId);
    assertThat(detail.path("status").asString()).isEqualTo("PAID");
    assertThat(detail.path("total").asString()).isEqualTo("40.00");
    assertThat(detail.path("items")).hasSize(1);
  }

  private String createCardTransaction(String amount, String postedOn, String description)
      throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", cardId);
    request.put("type", "DEBIT");
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", description);
    String body =
        alice
            .post("/api/v1/transactions", request)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json(body).path("id").asString();
  }

  private JsonNode invoices() throws Exception {
    return json(
        alice
            .get("/api/v1/cards/" + cardId + "/invoices")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode invoiceDetail(String invoiceId) throws Exception {
    return json(
        alice
            .get("/api/v1/invoices/" + invoiceId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode accounts() throws Exception {
    return json(
        alice
            .get("/api/v1/accounts")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private UUID userId() {
    return jdbcTemplate.queryForObject(
        "select id from app_user where username_normalized = ?", UUID.class, ALICE);
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
