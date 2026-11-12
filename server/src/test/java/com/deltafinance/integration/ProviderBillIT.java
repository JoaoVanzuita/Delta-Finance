package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.support.AuthTestClient;
import com.deltafinance.support.PluggyStub;
import com.deltafinance.support.PluggyStub.AccountFixture;
import com.deltafinance.support.PluggyStub.BillFixture;
import com.deltafinance.support.PluggyStub.BillPaymentFixture;
import com.deltafinance.support.PluggyStub.TransactionFixture;
import com.deltafinance.support.PostgresTestBase;
import java.util.List;
import java.util.Map;
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

/**
 * Conciliação com as Bills oficiais do provedor.
 *
 * <p>A fatura local continua sendo a fonte de verdade; a Bill apenas informa total, mínimo,
 * vencimento e pagamentos oficiais. A sincronização é idempotente e remove pagamentos que
 * desapareceram da Bill.
 */
@AutoConfigureMockMvc
class ProviderBillIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.providerbill";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-providerbill";
  private static final String ITEM = "item-providerbill";
  private static final String CARD = "acc-providerbill-card";

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
  @Autowired private SyncJobService syncJobService;

  private AuthTestClient alice;
  private String cardId;
  private String officialCardId;

  @BeforeEach
  void setUp() throws Exception {
    cleanIdentityData();
    PLUGGY.reset();
    rateLimiter.reset();
    PLUGGY.registerClient(CLIENT, "secret-alice");
    PLUGGY.addItem(ITEM, "Banco Alfa", "UPDATED");
    PLUGGY.addAccount(
        ITEM,
        new AccountFixture(
            CARD,
            "CREDIT",
            "CREDIT_CARD",
            "Cartão conciliação",
            "0.00",
            Map.of(
                "balanceCloseDate", "2026-10-05",
                "balanceDueDate", "2026-10-15",
                "creditLimit", "2000.00")));
    PLUGGY.addBill(
        CARD, new BillFixture("bill-2026-09", "2026-09-05", "2026-09-15", "100.00", "10.00"));
    PLUGGY.addBill(
        CARD, new BillFixture("bill-2026-10", "2026-10-05", "2026-10-15", "0.00", "0.00"));
    PLUGGY.addAccount(
        ITEM,
        new AccountFixture(
            "acc-providerbill-official",
            "CREDIT",
            "CREDIT_CARD",
            "Cartão só Bill",
            "0.00",
            Map.of(
                "balanceCloseDate", "2026-08-05",
                "balanceDueDate", "2026-08-15",
                "creditLimit", "1000.00")));
    PLUGGY.addBill(
        "acc-providerbill-official",
        new BillFixture("official-2026-08", "2026-08-05", "2026-08-15", "50.00", "5.00"));
    PLUGGY.addBill(
        "acc-providerbill-official",
        new BillFixture("official-2026-09", "2026-09-05", "2026-09-15", "0.00", "0.00"));
    PLUGGY.addBillPayment(
        "acc-providerbill-official",
        "official-2026-09",
        new BillPaymentFixture("official-payment", "2026-09-10", "50.00", "PIX", "FULL_PAYMENT"));
    PLUGGY.addTransaction(
        "acc-providerbill-official",
        new TransactionFixture(
            "tx-official-purchase",
            "acc-providerbill-official",
            "Compra só Bill",
            "50.00",
            "2026-07-10",
            "POSTED",
            "Groceries"));
    PLUGGY.setCreditCardMetadata("tx-official-purchase", Map.of("billForecastDate", "2026-08"));
    // Pela regra da Pluggy, os pagamentos da Bill seguinte quitam a Bill anterior.
    PLUGGY.addBillPayment(
        CARD,
        "bill-2026-10",
        new BillPaymentFixture("bill-payment-1", "2026-09-10", "112.50", "PIX", "FULL_PAYMENT"));
    PLUGGY.setBillFinanceCharges(
        CARD,
        "bill-2026-10",
        List.of(new PluggyStub.BillFinanceChargeFixture("charge-1", "IOF", "12.50")));

    PLUGGY.addTransaction(
        CARD,
        new TransactionFixture(
            "tx-provider-purchase",
            CARD,
            "Compra conciliada",
            "100.00",
            "2026-09-03",
            "POSTED",
            "Groceries"));
    PLUGGY.setCreditCardMetadata("tx-provider-purchase", Map.of("billForecastDate", "2026-09"));
    PLUGGY.addTransaction(
        CARD,
        new TransactionFixture(
            "tx-provider-payment",
            CARD,
            "Pagamento recebido",
            "-100.00",
            "2026-09-10",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-provider-payment", Map.of("billForecastDate", "2026-10"));

    alice = new AuthTestClient(mockMvc, jsonMapper).csrf();
    alice.register(ALICE, PASSWORD).login(ALICE, PASSWORD);
    alice
        .put(
            "/api/v1/integrations/pluggy",
            Map.of("clientId", CLIENT, "clientSecret", "secret-alice"))
        .andExpect(status().isOk());
    alice.post("/api/v1/connections", Map.of("itemId", ITEM)).andExpect(status().isCreated());

    for (JsonNode account : accounts().path("items")) {
      if ("Cartão conciliação".equals(account.path("alias").asString())) {
        cardId = account.path("id").asString();
      }
      if ("Cartão só Bill".equals(account.path("alias").asString())) {
        officialCardId = account.path("id").asString();
      }
    }
    assertThat(cardId).isNotBlank();
    assertThat(officialCardId).isNotBlank();
  }

  @Test
  void conciliaTotalMinimoVencimentoEPagamentosOficiais() throws Exception {
    JsonNode invoice = invoiceForCycle("2026-08-06");
    assertThat(invoice.path("total").asString()).isEqualTo("100.00");
    assertThat(invoice.path("paymentsTotal").asString()).isEqualTo("100.00");
    assertThat(invoice.path("minimumPayment").asString()).isEqualTo("10.00");

    JsonNode bill = invoice.path("providerBill");
    assertThat(bill.isNull()).isFalse();
    assertThat(bill.path("closingDate").asString()).isEqualTo("2026-09-05");
    assertThat(bill.path("dueDate").asString()).isEqualTo("2026-09-15");
    assertThat(bill.path("total").asString()).isEqualTo("100.00");
    assertThat(bill.path("minimumPayment").asString()).isEqualTo("10.00");
    assertThat(bill.path("paymentsTotal").asString()).isEqualTo("100.00");
    assertThat(bill.path("financeCharges").asString()).isEqualTo("12.50");
    assertThat(bill.path("settled").asBoolean()).isTrue();
    assertThat(bill.path("totalDifference").asString()).isEqualTo("0.00");
    assertThat(bill.path("paymentsDifference").asString()).isEqualTo("0.00");
    assertThat(bill.path("reconciled").asBoolean()).isTrue();
    assertThat(bill.path("payments")).hasSize(1);
    assertThat(bill.path("payments").get(0).path("amount").asString()).isEqualTo("112.50");
    assertThat(bill.path("payments").get(0).path("paymentMode").asString()).isEqualTo("PIX");
  }

  @Test
  void pagamentoOficialDaBillSeguinteQuitaFaturaSemPagamentoLocal() throws Exception {
    JsonNode invoice = invoiceForCycle(officialCardId, "2026-07-06");
    assertThat(invoice.path("total").asString()).isEqualTo("50.00");
    assertThat(invoice.path("paymentsTotal").asString()).isEqualTo("50.00");
    assertThat(invoice.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(invoice.path("status").asString()).isEqualTo("PAID");
    assertThat(invoice.path("providerBill").path("settled").asBoolean()).isTrue();
  }

  @Test
  void ressincronizacaoAtualizaBillSemDuplicarPagamentos() throws Exception {
    assertThat(invoiceForCycle("2026-08-06").path("providerBill").path("total").asString())
        .isEqualTo("100.00");

    PLUGGY.setBills(
        CARD,
        List.of(
            new BillFixture("bill-2026-09", "2026-09-05", "2026-09-15", "90.00", "9.00"),
            new BillFixture("bill-2026-10", "2026-10-05", "2026-10-15", "0.00", "0.00")));
    PLUGGY.setBillPayments(CARD, "bill-2026-10", List.of());
    PLUGGY.setBillFinanceCharges(CARD, "bill-2026-10", List.of());

    String connectionId = firstConnectionId();
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("worker", "providerbill");

    JsonNode invoice = invoiceForCycle("2026-08-06");
    assertThat(invoice.path("total").asString()).isEqualTo("100.00");
    assertThat(invoice.path("providerBill").path("total").asString()).isEqualTo("90.00");
    assertThat(invoice.path("providerBill").path("totalDifference").asString()).isEqualTo("10.00");
    assertThat(invoice.path("providerBill").path("paymentsTotal").asString()).isEqualTo("0.00");
    assertThat(invoice.path("providerBill").path("payments")).isEmpty();
    assertThat(invoice.path("providerBill").path("reconciled").asBoolean()).isFalse();
    assertThat(invoice.path("minimumPayment").asString()).isEqualTo("9.00");
  }

  private JsonNode invoiceForCycle(String cycleStart) throws Exception {
    return invoiceForCycle(cardId, cycleStart);
  }

  private JsonNode invoiceForCycle(String cardAccountId, String cycleStart) throws Exception {
    JsonNode invoices =
        json(
            alice
                .get("/api/v1/cards/" + cardAccountId + "/invoices")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode invoice : invoices.path("items")) {
      if (cycleStart.equals(invoice.path("cycleStart").asString())) {
        return invoice;
      }
    }
    throw new AssertionError("Fatura não encontrada para o ciclo " + cycleStart + ": " + invoices);
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

  private String firstConnectionId() throws Exception {
    JsonNode connections =
        json(
            alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return connections.path("items").get(0).path("id").asString();
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
