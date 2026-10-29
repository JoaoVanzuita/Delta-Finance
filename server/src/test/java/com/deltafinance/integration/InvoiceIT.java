package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.util.LinkedHashMap;
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

/** Ciclos, itens, pagamentos, crédito e faturas sem duplicação de compras (T122). */
@AutoConfigureMockMvc
class InvoiceIT extends PostgresTestBase {

  private static final PluggyStub PLUGGY = new PluggyStub();
  private static final String ALICE = "alice.faturas";
  private static final String PASSWORD = "cavalo correto bateria grampo";
  private static final String CLIENT = "client-alice-faturas";

  @DynamicPropertySource
  static void pluggyProperties(DynamicPropertyRegistry registry) {
    registry.add("delta.pluggy.base-url", PLUGGY::baseUrl);
    // A remoção na origem usa a janela diária; mantenha as datas fixas do teste cobertas.
    registry.add("delta.sync.daily-window-days", () -> "30");
  }

  @AfterAll
  static void stopStub() {
    PLUGGY.stop();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private SyncJobService syncJobService;
  @Autowired private PluggyRateLimiter rateLimiter;

  private AuthTestClient alice;
  private String cardId;
  private String bankId;
  private String connectionId;

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
    connectionId =
        json(alice
                .get("/api/v1/connections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .path("items")
            .get(0)
            .path("id")
            .asString();

    for (JsonNode account : accounts().path("items")) {
      if ("CREDIT".equals(account.path("type").asString())) {
        cardId = account.path("id").asString();
      }
      if ("BANK".equals(account.path("type").asString())) {
        bankId = account.path("id").asString();
      }
    }
    configureCycle(5, 15, "5000.00");
  }

  @Test
  void configuraCicloELimiteComValidacao() throws Exception {
    JsonNode profile = updateProfile(5, 15, "5000.00");
    assertThat(profile.path("closingDay").asInt()).isEqualTo(5);
    assertThat(profile.path("dueDay").asInt()).isEqualTo(15);
    assertThat(profile.path("creditLimit").asString()).isEqualTo("5000.00");
    assertThat(profile.path("creditBalance").asString()).isEqualTo("0.00");

    alice
        .put("/api/v1/cards/" + cardId + "/profile", Map.of("closingDay", 0, "dueDay", 15))
        .andExpect(status().isUnprocessableEntity());
    alice
        .put("/api/v1/cards/" + cardId + "/profile", Map.of("closingDay", 5, "dueDay", 32))
        .andExpect(status().isUnprocessableEntity());
    alice
        .put(
            "/api/v1/cards/" + cardId + "/profile",
            Map.of("closingDay", 5, "dueDay", 15, "creditLimit", "-1.00"))
        .andExpect(status().isUnprocessableEntity());
    alice
        .put("/api/v1/cards/" + bankId + "/profile", Map.of("closingDay", 5, "dueDay", 15))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void compraEPagamentoNaoDuplicamDespesas() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-10-03");
    JsonNode list = invoices();
    assertThat(list.path("items")).hasSize(1);
    JsonNode invoice = list.path("items").get(0);
    assertThat(invoice.path("total").asString()).isEqualTo("100.00");
    assertThat(invoice.path("status").asString()).isEqualTo("OVERDUE");
    assertThat(invoice.path("outstanding").asString()).isEqualTo("100.00");

    JsonNode detail = invoiceDetail(invoice.path("id").asString());
    assertThat(detail.path("items")).hasSize(1);
    JsonNode item = detail.path("items").get(0);
    assertThat(item.path("kind").asString()).isEqualTo("PURCHASE");
    assertThat(item.path("reportingEffect").asString()).isEqualTo("NONE");
    assertThat(item.path("transactionId").asString()).isNotBlank();
    assertThat(item.path("amount").asString()).isEqualTo("100.00");

    int transactionsBefore = transactions().path("items").size();
    alice
        .post(
            "/api/v1/invoices/" + invoice.path("id").asString() + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "40.00", "paidOn", "2026-10-20"))
        .andExpect(status().isCreated());

    detail = invoiceDetail(invoice.path("id").asString());
    assertThat(detail.path("status").asString()).isEqualTo("OVERDUE");
    assertThat(detail.path("paymentsTotal").asString()).isEqualTo("40.00");
    assertThat(detail.path("outstanding").asString()).isEqualTo("60.00");

    alice
        .post(
            "/api/v1/invoices/" + invoice.path("id").asString() + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "60.00", "paidOn", "2026-10-21"))
        .andExpect(status().isCreated());
    detail = invoiceDetail(invoice.path("id").asString());
    assertThat(detail.path("status").asString()).isEqualTo("PAID");
    assertThat(detail.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(transactions().path("items").size()).isEqualTo(transactionsBefore);
  }

  @Test
  void estornoAutomaticoViraEspelhoSemEfeitoDeRelatorio() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-10-20");
    createCardTransaction("REFUND", "20.00", "2026-10-21");

    JsonNode invoice = invoices().path("items").get(0);
    JsonNode detail = invoiceDetail(invoice.path("id").asString());
    JsonNode refund = findItem(detail.path("items"), "REFUND");
    assertThat(refund.path("transactionId").asString()).isNotBlank();
    assertThat(refund.path("reportingEffect").asString()).isEqualTo("NONE");
    assertThat(detail.path("total").asString()).isEqualTo("80.00");
  }

  @Test
  void itensManuaisAplicamEfeitoHistoricoERemocao() throws Exception {
    createCardTransaction("DEBIT", "-200.00", "2026-10-03");
    String invoiceId = invoices().path("items").get(0).path("id").asString();

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "INTEREST",
                "reportingEffect", "EXPENSE_INCREASE",
                "description", "Juros",
                "amount", "20.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("MANUAL"));

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "MANUAL_ADJUSTMENT",
                "reportingEffect", "EXPENSE_DECREASE",
                "description", "Ajuste",
                "amount", "50.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isCreated());

    assertThat(invoiceDetail(invoiceId).path("total").asString()).isEqualTo("170.00");

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "INTEREST",
                "description", "Sem efeito",
                "amount", "1.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isUnprocessableEntity());
    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "PURCHASE",
                "reportingEffect", "NONE",
                "description", "Sem transação",
                "amount", "10.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isUnprocessableEntity());

    JsonNode detail = invoiceDetail(invoiceId);
    JsonNode interest = findItem(detail.path("items"), "INTEREST");
    alice
        .patch(
            "/api/v1/invoice-items/" + interest.path("id").asString(),
            Map.of(
                "kind", "INTEREST",
                "reportingEffect", "EXPENSE_INCREASE",
                "description", "Juros revisado",
                "amount", "25.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctionCount").value(2));
    assertThat(invoiceDetail(invoiceId).path("total").asString()).isEqualTo("175.00");

    JsonNode corrections =
        json(
            alice
                .get("/api/v1/invoice-items/" + interest.path("id").asString() + "/corrections")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(corrections).hasSize(2);
    assertThat(corrections.toString()).contains("Juros revisado", "20.00", "25.00");

    JsonNode adjustment = findItem(invoiceDetail(invoiceId).path("items"), "MANUAL_ADJUSTMENT");
    alice
        .delete("/api/v1/invoice-items/" + adjustment.path("id").asString())
        .andExpect(status().isNoContent());
    detail = invoiceDetail(invoiceId);
    assertThat(detail.path("total").asString()).isEqualTo("225.00");
    assertThat(detail.path("hasAdjustments").asBoolean()).isTrue();
    assertThat(detail.path("adjustmentCount").asInt()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void creditoExcedenteECarryForward() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-10-03");
    String firstInvoice = invoices().path("items").get(0).path("id").asString();
    alice
        .post(
            "/api/v1/invoices/" + firstInvoice + "/items",
            Map.of(
                "kind", "CREDIT",
                "reportingEffect", "EXPENSE_DECREASE",
                "description", "Crédito",
                "amount", "150.00",
                "occurredOn", "2026-10-04"))
        .andExpect(status().isCreated());

    JsonNode first = invoiceDetail(firstInvoice);
    assertThat(first.path("total").asString()).isEqualTo("0.00");
    assertThat(first.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(cardCreditBalance()).isEqualTo("50.00");

    createCardTransaction("DEBIT", "-30.00", "2026-10-10");
    JsonNode list = invoices();
    JsonNode second = null;
    for (JsonNode invoice : list.path("items")) {
      if (!invoice.path("id").asString().equals(firstInvoice)) {
        second = invoice;
      }
    }
    assertThat(second).isNotNull();
    JsonNode secondDetail = invoiceDetail(second.path("id").asString());
    assertThat(secondDetail.path("total").asString()).isEqualTo("30.00");
    assertThat(secondDetail.path("appliedCredit").asString()).isEqualTo("30.00");
    assertThat(secondDetail.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(cardCreditBalance()).isEqualTo("20.00");
  }

  @Test
  void pagamentoComBillForecastQuitaAFaturaAnteriorENaoAProxima() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-bill-purchase",
            "acc-card",
            "Compra da fatura",
            "100.00",
            "2026-09-03",
            "POSTED",
            "Groceries"));
    PLUGGY.setCreditCardMetadata("tx-bill-purchase", Map.of("billForecastDate", "2026-09"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-bill-payment",
            "acc-card",
            "Pagamento recebido",
            "-100.00",
            "2026-09-10",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-bill-payment", Map.of("billForecastDate", "2026-10"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-next-purchase",
            "acc-card",
            "Compra do próximo ciclo",
            "50.00",
            "2026-09-11",
            "POSTED",
            "Groceries"));
    PLUGGY.setCreditCardMetadata("tx-next-purchase", Map.of("billForecastDate", "2026-10"));
    sync();

    JsonNode invoices = invoices();
    assertThat(invoices.path("items")).hasSize(2);

    JsonNode paidBill = null;
    JsonNode nextBill = null;
    for (JsonNode invoice : invoices.path("items")) {
      if ("2026-09-05".equals(invoice.path("closingDate").asString())) {
        paidBill = invoice;
      }
      if ("2026-10-05".equals(invoice.path("closingDate").asString())) {
        nextBill = invoice;
      }
    }
    assertThat(paidBill).isNotNull();
    assertThat(paidBill.path("total").asString()).isEqualTo("100.00");
    assertThat(paidBill.path("paymentsTotal").asString()).isEqualTo("100.00");
    assertThat(paidBill.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(paidBill.path("dueDate").asString()).isEqualTo("2026-09-15");

    JsonNode paidDetail = invoiceDetail(paidBill.path("id").asString());
    assertThat(paidDetail.path("items")).hasSize(1);
    assertThat(paidDetail.path("payments")).hasSize(1);

    assertThat(nextBill).isNotNull();
    assertThat(nextBill.path("total").asString()).isEqualTo("50.00");
    assertThat(nextBill.path("outstanding").asString()).isEqualTo("50.00");
    assertThat(nextBill.path("dueDate").asString()).isEqualTo("2026-10-15");

    JsonNode overview =
        json(
            alice
                .get("/api/v1/cards/overview")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(overview.path("totalOutstanding").asString()).isEqualTo("50.00");
  }

  @Test
  void pagamentoSemBillForecastUsaAFaturaAnterior() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-no-forecast-purchase",
            "acc-card",
            "Compra da fatura",
            "80.00",
            "2026-09-03",
            "POSTED",
            "Groceries"));
    PLUGGY.setCreditCardMetadata("tx-no-forecast-purchase", Map.of("billForecastDate", "2026-09"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-no-forecast-payment",
            "acc-card",
            "Pagamento recebido",
            "-80.00",
            "2026-09-10",
            "POSTED",
            "Credit card payment"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-no-forecast-next",
            "acc-card",
            "Compra do próximo ciclo",
            "20.00",
            "2026-09-11",
            "POSTED",
            "Groceries"));
    PLUGGY.setCreditCardMetadata("tx-no-forecast-next", Map.of("billForecastDate", "2026-10"));
    sync();

    JsonNode invoices = invoices();
    JsonNode paidBill = null;
    JsonNode nextBill = null;
    for (JsonNode invoice : invoices.path("items")) {
      if ("2026-09-05".equals(invoice.path("closingDate").asString())) {
        paidBill = invoice;
      }
      if ("2026-10-05".equals(invoice.path("closingDate").asString())) {
        nextBill = invoice;
      }
    }
    assertThat(paidBill).isNotNull();
    assertThat(paidBill.path("total").asString()).isEqualTo("80.00");
    assertThat(paidBill.path("paymentsTotal").asString()).isEqualTo("80.00");
    assertThat(paidBill.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(nextBill).isNotNull();
    assertThat(nextBill.path("outstanding").asString()).isEqualTo("20.00");
  }

  @Test
  void posFechamentoVaiParaCicloSeguinteEMudancaDeCicloNaoReescreve() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-10-03");
    JsonNode first = invoices().path("items").get(0);
    String firstId = first.path("id").asString();
    assertThat(first.path("closingDate").asString()).isEqualTo("2026-10-05");

    // Chega depois do fechamento, mas com data dentro do ciclo já fechado.
    createCardTransaction("DEBIT", "-20.00", "2026-10-04");
    JsonNode list = invoices();
    assertThat(list.path("items")).hasSize(2);
    JsonNode second = null;
    for (JsonNode invoice : list.path("items")) {
      if (!invoice.path("id").asString().equals(firstId)) {
        second = invoice;
      }
    }
    assertThat(second).isNotNull();
    assertThat(second.path("cycleStart").asString()).isEqualTo("2026-10-06");

    configureCycle(10, 20, "5000.00");
    createCardTransaction("DEBIT", "-30.00", "2026-10-08");
    invoices();
    JsonNode firstAfter = invoiceDetail(firstId);
    assertThat(firstAfter.path("closingDate").asString()).isEqualTo("2026-10-05");
    assertThat(firstAfter.path("total").asString()).isEqualTo("100.00");
    assertThat(firstAfter.path("items")).hasSize(1);

    JsonNode future = null;
    for (JsonNode invoice : invoices().path("items")) {
      if ("2026-10-10".equals(invoice.path("closingDate").asString())) {
        future = invoice;
      }
    }
    assertThat(future).isNotNull();
  }

  @Test
  void compraParceladaEmTresCiclosSubstituiEspelhoEProjetaParcelas() throws Exception {
    createCardTransaction("DEBIT", "-300.00", "2026-10-03");
    invoices();
    JsonNode transaction = transactions().path("items").get(0);
    String transactionId = transaction.path("id").asString();
    String firstInvoiceId = transaction.path("invoiceId").asString();

    JsonNode firstInstallment =
        json(
            alice
                .post(
                    "/api/v1/invoices/" + firstInvoiceId + "/items",
                    Map.of(
                        "kind", "INSTALLMENT",
                        "reportingEffect", "NONE",
                        "description", "Compra parcelada",
                        "amount", "100.00",
                        "occurredOn", "2026-10-03",
                        "transactionId", transactionId,
                        "installmentNumber", 1,
                        "totalInstallments", 3))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(firstInstallment.path("installmentNumber").asInt()).isEqualTo(1);
    assertThat(firstInstallment.path("totalInstallments").asInt()).isEqualTo(3);

    JsonNode list = invoices();
    assertThat(list.path("items")).hasSize(3);
    for (JsonNode invoice : list.path("items")) {
      JsonNode detail = invoiceDetail(invoice.path("id").asString());
      assertThat(detail.path("total").asString()).isEqualTo("100.00");
      assertThat(detail.path("items")).hasSize(1);
      assertThat(detail.path("items").get(0).path("kind").asString()).isEqualTo("INSTALLMENT");
      assertThat(detail.path("items").get(0).path("installmentNumber").asInt()).isBetween(1, 3);
    }
  }

  @Test
  void cartaoSemCicloNaoGeraFaturas() throws Exception {
    jdbcTemplate.update(
        "delete from card_profile where account_id = ?", java.util.UUID.fromString(cardId));
    createCardTransaction("DEBIT", "-40.00", "2026-10-03");

    assertThat(invoices().path("items")).isEmpty();
    JsonNode transaction = transactions().path("items").get(0);
    assertThat(transaction.path("invoiceId").isNull()).isTrue();
  }

  @Test
  void transacaoPendenteSuspendeEEspelhoEReativaAoTornarEfetiva() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-pendente", "acc-card", "Compra pendente", "100.00", "2026-10-20", "PENDING", null));
    sync();

    assertThat(summary("from=2026-10-01&to=2026-10-31").path("expenses").asString())
        .isEqualTo("0.00");
    JsonNode pendingInvoice = invoices().path("items");
    assertThat(pendingInvoice).hasSize(1);
    assertThat(pendingInvoice.get(0).path("status").asString()).isEqualTo("OPEN");
    assertThat(pendingInvoice.get(0).path("total").asString()).isEqualTo("0.00");

    PLUGGY.setTransactions(
        "acc-card",
        List.of(
            new TransactionFixture(
                "tx-pendente",
                "acc-card",
                "Compra pendente",
                "100.00",
                "2026-10-20",
                "POSTED",
                null)));
    sync();

    JsonNode first = invoices().path("items").get(0);
    assertThat(first.path("total").asString()).isEqualTo("100.00");
    String invoiceId = first.path("id").asString();
    assertThat(invoiceDetail(invoiceId).path("items")).hasSize(1);

    PLUGGY.setTransactions(
        "acc-card",
        List.of(
            new TransactionFixture(
                "tx-pendente",
                "acc-card",
                "Compra pendente",
                "100.00",
                "2026-10-20",
                "PENDING",
                null)));
    sync();

    JsonNode suspended = invoiceDetail(invoiceId);
    assertThat(suspended.path("total").asString()).isEqualTo("0.00");
    assertThat(suspended.path("items")).isEmpty();

    PLUGGY.setTransactions(
        "acc-card",
        List.of(
            new TransactionFixture(
                "tx-pendente",
                "acc-card",
                "Compra pendente",
                "100.00",
                "2026-10-20",
                "POSTED",
                null)));
    sync();

    JsonNode restored = invoiceDetail(invoiceId);
    assertThat(restored.path("total").asString()).isEqualTo("100.00");
    assertThat(restored.path("items")).hasSize(1);
  }

  @Test
  void remocaoNaOrigemSuspendeEspelhoAteReativacao() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-removida", "acc-card", "Compra removida", "100.00", "2026-10-20", "POSTED", null));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-viva", "acc-card", "Compra viva", "40.00", "2026-10-21", "POSTED", null));
    sync();

    JsonNode invoice = invoices().path("items").get(0);
    String invoiceId = invoice.path("id").asString();
    assertThat(invoice.path("total").asString()).isEqualTo("140.00");

    // Mantém uma transação viva para que a lista vazia não seja tratada como inconclusiva.
    PLUGGY.setTransactions(
        "acc-card",
        List.of(
            new TransactionFixture(
                "tx-viva", "acc-card", "Compra viva", "40.00", "2026-10-21", "POSTED", null)));
    sync();

    JsonNode removed = findTransactionByDescription(transactions(), "Compra removida");
    assertThat(removed.path("status").asString()).isEqualTo("SOURCE_REMOVED");
    assertThat(removed.path("reconciliationState").asString()).isEqualTo("SOURCE_REMOVED");
    JsonNode suspended = invoiceDetail(invoiceId);
    assertThat(suspended.path("total").asString()).isEqualTo("40.00");
    assertThat(suspended.path("items")).hasSize(1);

    PLUGGY.setTransactions(
        "acc-card",
        List.of(
            new TransactionFixture(
                "tx-removida",
                "acc-card",
                "Compra removida",
                "100.00",
                "2026-10-20",
                "POSTED",
                null),
            new TransactionFixture(
                "tx-viva", "acc-card", "Compra viva", "40.00", "2026-10-21", "POSTED", null)));
    sync();

    JsonNode restored = findTransactionByDescription(transactions(), "Compra removida");
    assertThat(restored.path("reconciliationState").asString()).isEqualTo("NONE");
    assertThat(invoiceDetail(invoiceId).path("total").asString()).isEqualTo("140.00");
    assertThat(invoiceDetail(invoiceId).path("items")).hasSize(2);
  }

  @Test
  void faturaPagaNaoAceitaItensNemCorrecaoDeCompra() throws Exception {
    String transactionId = createCardTransaction("DEBIT", "-100.00", "2026-10-20");
    JsonNode invoice = invoices().path("items").get(0);
    String invoiceId = invoice.path("id").asString();
    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "100.00", "paidOn", "2026-10-21"))
        .andExpect(status().isCreated());
    assertThat(invoiceDetail(invoiceId).path("status").asString()).isEqualTo("PAID");

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/items",
            Map.of(
                "kind", "INTEREST",
                "reportingEffect", "EXPENSE_INCREASE",
                "description", "Juros após pagamento",
                "amount", "10.00",
                "occurredOn", "2026-10-22"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVOICE_PAID_IMMUTABLE"));
    alice
        .patch("/api/v1/transactions/" + transactionId, Map.of("amount", "120.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRANSACTION_PAID_INVOICE"));
    alice
        .patch("/api/v1/transactions/" + transactionId, Map.of("description", "Compra corrigida"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRANSACTION_PAID_INVOICE"));
    alice
        .delete("/api/v1/transactions/" + transactionId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRANSACTION_PAID_INVOICE"));

    assertThat(invoiceDetail(invoiceId).path("total").asString()).isEqualTo("100.00");
    assertThat(invoiceDetail(invoiceId).path("items")).hasSize(1);
  }

  @Test
  void faturaVencidaComPagamentoParcialFicaOverdue() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-09-03");
    JsonNode invoice = invoices().path("items").get(0);
    String invoiceId = invoice.path("id").asString();
    assertThat(invoice.path("dueDate").asString()).isEqualTo("2026-09-15");
    assertThat(invoice.path("status").asString()).isEqualTo("OVERDUE");

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "40.00", "paidOn", "2026-10-20"))
        .andExpect(status().isCreated());

    JsonNode partial = invoiceDetail(invoiceId);
    assertThat(partial.path("status").asString()).isEqualTo("OVERDUE");
    assertThat(partial.path("outstanding").asString()).isEqualTo("60.00");

    alice
        .post(
            "/api/v1/invoices/" + invoiceId + "/payments",
            Map.of("paymentAccountId", bankId, "amount", "60.00", "paidOn", "2026-10-21"))
        .andExpect(status().isCreated());
    assertThat(invoiceDetail(invoiceId).path("status").asString()).isEqualTo("PAID");
  }

  @Test
  void exclusaoDeLancamentoManualDeCartaoRemoveEspelhoERecalcula() throws Exception {
    String transactionId = createCardTransaction("DEBIT", "-100.00", "2026-10-20");
    JsonNode invoice = invoices().path("items").get(0);
    String invoiceId = invoice.path("id").asString();
    assertThat(invoiceDetail(invoiceId).path("total").asString()).isEqualTo("100.00");

    alice.delete("/api/v1/transactions/" + transactionId).andExpect(status().isNoContent());

    JsonNode detail = invoiceDetail(invoiceId);
    assertThat(detail.path("total").asString()).isEqualTo("0.00");
    assertThat(detail.path("items")).isEmpty();
  }

  @Test
  void correcaoDeCompraAtualizaEspelhoEMudaDeCiclo() throws Exception {
    String transactionId = createCardTransaction("DEBIT", "-100.00", "2026-10-20");
    JsonNode first = invoices().path("items").get(0);
    String firstId = first.path("id").asString();

    alice
        .patch(
            "/api/v1/transactions/" + transactionId,
            Map.of("amount", "120.00", "description", "Compra corrigida"))
        .andExpect(status().isOk());

    JsonNode detail = invoiceDetail(firstId);
    assertThat(detail.path("total").asString()).isEqualTo("120.00");
    assertThat(detail.path("items").get(0).path("amount").asString()).isEqualTo("120.00");
    assertThat(detail.path("items").get(0).path("description").asString())
        .isEqualTo("Compra corrigida");

    alice
        .patch("/api/v1/transactions/" + transactionId, Map.of("postedOn", "2026-11-06"))
        .andExpect(status().isOk());

    JsonNode transaction = findTransactionByDescription(transactions(), "Compra corrigida");
    assertThat(transaction.path("postedOn").asString()).isEqualTo("2026-11-06");
    assertThat(invoiceDetail(firstId).path("total").asString()).isEqualTo("0.00");

    JsonNode moved = null;
    for (JsonNode invoice : invoices().path("items")) {
      if ("2026-12-05".equals(invoice.path("closingDate").asString())) {
        moved = invoice;
      }
    }
    assertThat(moved).isNotNull();
    assertThat(moved.path("id").asString()).isEqualTo(transaction.path("invoiceId").asString());
    assertThat(invoiceDetail(moved.path("id").asString()).path("total").asString())
        .isEqualTo("120.00");
  }

  @Test
  void dashboardMostraSaldoDevedorLogoAposCompraNoCartaoSemAbrirFaturas() throws Exception {
    createCardTransaction("DEBIT", "-100.00", "2026-10-20");

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31");
    assertThat(summary.path("expenses").asString()).isEqualTo("100.00");
    assertThat(summary.path("cardOutstandingBalance").asString()).isEqualTo("100.00");
  }

  @Test
  void syncDeCompraDeCartaoAtualizaDashboardSemAbrirFaturas() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-dashboard",
            "acc-card",
            "Compra sincronizada",
            "80.00",
            "2026-10-20",
            "POSTED",
            null));
    sync();

    JsonNode summary = summary("from=2026-10-01&to=2026-10-31");
    assertThat(summary.path("expenses").asString()).isEqualTo("80.00");
    assertThat(summary.path("cardOutstandingBalance").asString()).isEqualTo("80.00");

    sync();
    assertThat(invoices().path("items")).hasSize(1);
  }

  @Test
  void pagamentoExcedenteViraCreditoParaFaturaSeguinte() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-over-purchase",
            "acc-card",
            "Compra ciclo um",
            "100.00",
            "2026-10-03",
            "POSTED",
            null));
    PLUGGY.setCreditCardMetadata("tx-over-purchase", Map.of("billForecastDate", "2026-10"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-over-payment",
            "acc-card",
            "Pagamento recebido",
            "-120.00",
            "2026-10-04",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-over-payment", Map.of("billForecastDate", "2026-11"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-over-next",
            "acc-card",
            "Compra ciclo dois",
            "50.00",
            "2026-10-08",
            "POSTED",
            null));
    PLUGGY.setCreditCardMetadata("tx-over-next", Map.of("billForecastDate", "2026-11"));
    sync();

    JsonNode first = invoiceForCycle("2026-09-06");
    assertThat(first.path("total").asString()).isEqualTo("100.00");
    assertThat(first.path("paymentsTotal").asString()).isEqualTo("120.00");
    assertThat(first.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(first.path("status").asString()).isEqualTo("PAID");

    JsonNode next = invoiceForCycle("2026-10-06");
    assertThat(next.path("total").asString()).isEqualTo("50.00");
    assertThat(next.path("appliedCredit").asString()).isEqualTo("20.00");
    assertThat(next.path("outstanding").asString()).isEqualTo("30.00");
    assertThat(cardCreditBalance()).isEqualTo("0.00");
  }

  @Test
  void billOficialMaiorQueTotalLocalNaoGeraCreditoIndevido() throws Exception {
    PLUGGY.addBill(
        "acc-card",
        new BillFixture("bill-over-official", "2026-10-05", "2026-10-15", "120.00", "12.00"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-over-official-purchase",
            "acc-card",
            "Compra com total oficial",
            "100.00",
            "2026-10-03",
            "POSTED",
            null));
    PLUGGY.setCreditCardMetadata(
        "tx-over-official-purchase", Map.of("billForecastDate", "2026-10"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-over-official-payment",
            "acc-card",
            "Pagamento recebido",
            "-120.00",
            "2026-10-04",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-over-official-payment", Map.of("billForecastDate", "2026-11"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-over-official-next",
            "acc-card",
            "Compra sem crédito",
            "50.00",
            "2026-10-08",
            "POSTED",
            null));
    PLUGGY.setCreditCardMetadata("tx-over-official-next", Map.of("billForecastDate", "2026-11"));
    sync();

    JsonNode first = invoiceForCycle("2026-09-06");
    assertThat(first.path("total").asString()).isEqualTo("100.00");
    assertThat(first.path("paymentsTotal").asString()).isEqualTo("120.00");
    assertThat(first.path("providerBill").path("total").asString()).isEqualTo("120.00");
    assertThat(first.path("providerBill").path("totalDifference").asString()).isEqualTo("-20.00");

    JsonNode next = invoiceForCycle("2026-10-06");
    assertThat(next.path("appliedCredit").asString()).isEqualTo("0.00");
    assertThat(next.path("outstanding").asString()).isEqualTo("50.00");
    assertThat(cardCreditBalance()).isEqualTo("0.00");
  }

  @Test
  void duplicataPendingPostedNaoDuplicaPagamento() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-dup-purchase", "acc-card", "Compra dup", "100.00", "2026-10-24", "POSTED", null));
    PLUGGY.setCreditCardMetadata("tx-dup-purchase", Map.of("billForecastDate", "2026-11"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-dup-posted",
            "acc-card",
            "Pagamento recebido",
            "-100.00",
            "2026-10-25",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-dup-posted", Map.of("billForecastDate", "2026-12"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-dup-pending",
            "acc-card",
            "Pagamento recebido",
            "-100.00",
            "2026-10-25",
            "PENDING",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-dup-pending", Map.of("billForecastDate", "2026-12"));
    sync();

    JsonNode invoice = invoiceForCycle("2026-10-06");
    assertThat(invoice.path("paymentsTotal").asString()).isEqualTo("100.00");

    PLUGGY.setTransactions(
        "acc-card",
        List.of(
            new TransactionFixture(
                "tx-dup-purchase",
                "acc-card",
                "Compra dup",
                "100.00",
                "2026-10-03",
                "POSTED",
                null),
            new TransactionFixture(
                "tx-dup-posted",
                "acc-card",
                "Pagamento recebido",
                "-100.00",
                "2026-10-25",
                "POSTED",
                "Credit card payment"),
            new TransactionFixture(
                "tx-dup-pending",
                "acc-card",
                "Pagamento recebido",
                "-100.00",
                "2026-10-25",
                "POSTED",
                "Credit card payment")));
    sync();

    JsonNode after = invoiceForCycle("2026-10-06");
    assertThat(after.path("paymentsTotal").asString()).isEqualTo("100.00");
    assertThat(invoiceDetail(after.path("id").asString()).path("payments")).hasSize(1);
    assertThat(cardCreditBalance()).isEqualTo("0.00");
  }

  @Test
  void duplicataAssumeOPagamentoQuandoAOrigemDeixaDeValer() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-dup2-purchase", "acc-card", "Compra dup2", "100.00", "2026-10-24", "POSTED", null));
    PLUGGY.setCreditCardMetadata("tx-dup2-purchase", Map.of("billForecastDate", "2026-11"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-dup2-a",
            "acc-card",
            "Pagamento recebido",
            "-100.00",
            "2026-10-25",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-dup2-a", Map.of("billForecastDate", "2026-12"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-dup2-b",
            "acc-card",
            "Pagamento recebido",
            "-100.00",
            "2026-10-25",
            "PENDING",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-dup2-b", Map.of("billForecastDate", "2026-12"));
    sync();
    assertThat(invoiceForCycle("2026-10-06").path("paymentsTotal").asString()).isEqualTo("100.00");

    // A origem canônica some e a duplicata ainda está pendente: o pagamento é revertido.
    PLUGGY.setTransactions(
        "acc-card",
        List.of(
            new TransactionFixture(
                "tx-dup2-purchase",
                "acc-card",
                "Compra dup2",
                "100.00",
                "2026-10-24",
                "POSTED",
                null),
            new TransactionFixture(
                "tx-dup2-b",
                "acc-card",
                "Pagamento recebido",
                "-100.00",
                "2026-10-25",
                "PENDING",
                "Credit card payment")));
    sync();
    assertThat(invoiceForCycle("2026-10-06").path("paymentsTotal").asString()).isEqualTo("0.00");

    // Quando a duplicata efetiva, ela revive o mesmo pagamento em vez de criar outro.
    PLUGGY.setTransactions(
        "acc-card",
        List.of(
            new TransactionFixture(
                "tx-dup2-purchase",
                "acc-card",
                "Compra dup2",
                "100.00",
                "2026-10-24",
                "POSTED",
                null),
            new TransactionFixture(
                "tx-dup2-b",
                "acc-card",
                "Pagamento recebido",
                "-100.00",
                "2026-10-25",
                "POSTED",
                "Credit card payment")));
    sync();

    JsonNode restored = invoiceForCycle("2026-10-06");
    assertThat(restored.path("paymentsTotal").asString()).isEqualTo("100.00");
    assertThat(invoiceDetail(restored.path("id").asString()).path("payments")).hasSize(1);
    assertThat(cardCreditBalance()).isEqualTo("0.00");
  }

  @Test
  void conciliacaoComBillOficialAjustaFaturaAbertaComItemAuditado() throws Exception {
    PLUGGY.addBill(
        "acc-card",
        new BillFixture("bill-reconcile-open", "2026-10-05", "2026-10-15", "120.00", "12.00"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-reconcile-purchase",
            "acc-card",
            "Compra conciliação",
            "100.00",
            "2026-10-03",
            "POSTED",
            null));
    PLUGGY.setCreditCardMetadata("tx-reconcile-purchase", Map.of("billForecastDate", "2026-10"));
    sync();

    JsonNode invoice = invoiceForCycle("2026-09-06");
    assertThat(invoice.path("total").asString()).isEqualTo("100.00");
    assertThat(invoice.path("providerBill").path("total").asString()).isEqualTo("120.00");
    assertThat(invoice.path("providerBill").path("totalDifference").asString()).isEqualTo("-20.00");

    JsonNode reconciled =
        json(
            alice
                .post(
                    "/api/v1/invoices/" + invoice.path("id").asString() + "/reconcile-provider",
                    Map.of("reason", "Compra ausente no histórico do conector"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(reconciled.path("total").asString()).isEqualTo("120.00");
    assertThat(reconciled.path("providerBill").path("total").asString()).isEqualTo("120.00");
    assertThat(reconciled.path("providerBill").path("totalDifference").asString())
        .isEqualTo("0.00");
    assertThat(reconciled.path("providerBill").path("reconciled").asBoolean()).isTrue();
    assertThat(reconciled.path("items")).hasSize(2);
    JsonNode adjustment = reconciled.path("items").get(1);
    assertThat(adjustment.path("kind").asString()).isEqualTo("MANUAL_ADJUSTMENT");
    assertThat(adjustment.path("reportingEffect").asString()).isEqualTo("EXPENSE_INCREASE");
    assertThat(adjustment.path("amount").asString()).isEqualTo("20.00");
    assertThat(adjustment.path("description").asString())
        .contains("Compra ausente no histórico do conector");

    alice
        .post(
            "/api/v1/invoices/" + invoice.path("id").asString() + "/reconcile-provider",
            Map.of("reason", "Tentativa repetida"))
        .andExpect(status().isConflict());
  }

  @Test
  void conciliacaoEmFaturaPagaPreservaStatusENormalizaCreditoAplicado() throws Exception {
    PLUGGY.addBill(
        "acc-card",
        new BillFixture("bill-reconcile-paid", "2026-10-05", "2026-10-15", "120.00", "12.00"));
    PLUGGY.addBillPayment(
        "acc-card",
        "bill-reconcile-paid",
        new BillPaymentFixture(
            "pay-reconcile-paid", "2026-10-04", "100.00", "PIX", "PARTIAL_PAYMENT"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-reconcile-paid-purchase",
            "acc-card",
            "Compra paga",
            "100.00",
            "2026-10-03",
            "POSTED",
            null));
    PLUGGY.setCreditCardMetadata(
        "tx-reconcile-paid-purchase", Map.of("billForecastDate", "2026-10"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-reconcile-paid-payment",
            "acc-card",
            "Pagamento recebido",
            "-100.00",
            "2026-10-04",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata(
        "tx-reconcile-paid-payment", Map.of("billForecastDate", "2026-11"));
    sync();

    JsonNode invoice = invoiceForCycle("2026-09-06");
    assertThat(invoice.path("status").asString()).isEqualTo("PAID");
    assertThat(invoice.path("total").asString()).isEqualTo("100.00");
    assertThat(invoice.path("paymentsTotal").asString()).isEqualTo("100.00");
    assertThat(invoice.path("appliedCredit").asString()).isEqualTo("0.00");

    JsonNode reconciled =
        json(
            alice
                .post(
                    "/api/v1/invoices/" + invoice.path("id").asString() + "/reconcile-provider",
                    Map.of("reason", "Compra ausente já faturada"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(reconciled.path("status").asString()).isEqualTo("PAID");
    assertThat(reconciled.path("total").asString()).isEqualTo("120.00");
    assertThat(reconciled.path("paymentsTotal").asString()).isEqualTo("100.00");
    assertThat(reconciled.path("appliedCredit").asString()).isEqualTo("20.00");
    assertThat(reconciled.path("outstanding").asString()).isEqualTo("0.00");
    assertThat(reconciled.path("providerBill").path("totalDifference").asString())
        .isEqualTo("0.00");
    assertThat(reconciled.path("providerBill").path("paymentsDifference").asString())
        .isEqualTo("0.00");
    assertThat(reconciled.path("providerBill").path("reconciled").asBoolean()).isTrue();
    assertThat(cardCreditBalance()).isEqualTo("0.00");
  }

  @Test
  void pagamentoSemComprasLocaisSinalizaHistoricoIncompleto() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-missing-purchase",
            "acc-card",
            "Pagamento recebido",
            "-100.00",
            "2026-10-24",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-missing-purchase", Map.of("billForecastDate", "2026-12"));
    sync();

    JsonNode invoice = invoiceForCycle("2026-10-06");
    assertThat(invoice.path("total").asString()).isEqualTo("0.00");
    assertThat(invoice.path("paymentsTotal").asString()).isEqualTo("100.00");
    assertThat(invoice.path("missingPurchases").asBoolean()).isTrue();
    assertThat(invoice.path("providerBill").isNull()).isTrue();
  }

  @Test
  void correcaoDePagamentoImportadoAtualizaValorDataECredito() throws Exception {
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-correction-purchase",
            "acc-card",
            "Compra para correção",
            "100.00",
            "2026-10-24",
            "POSTED",
            null));
    PLUGGY.setCreditCardMetadata("tx-correction-purchase", Map.of("billForecastDate", "2026-11"));
    PLUGGY.addTransaction(
        "acc-card",
        new TransactionFixture(
            "tx-correction-payment",
            "acc-card",
            "Pagamento recebido",
            "-100.00",
            "2026-10-25",
            "POSTED",
            "Credit card payment"));
    PLUGGY.setCreditCardMetadata("tx-correction-payment", Map.of("billForecastDate", "2026-12"));
    sync();

    JsonNode invoice = invoiceForCycle("2026-10-06");
    String invoiceId = invoice.path("id").asString();
    String paymentTransactionId =
        findTransactionByDescription(transactions(), "Pagamento recebido").path("id").asString();

    alice
        .patch(
            "/api/v1/transactions/" + paymentTransactionId,
            Map.of(
                "amount", "120.00",
                "description", "Pagamento recebido",
                "postedOn", "2026-10-26"))
        .andExpect(status().isOk());

    JsonNode corrected = invoiceDetail(invoiceId);
    assertThat(corrected.path("paymentsTotal").asString()).isEqualTo("120.00");
    assertThat(corrected.path("payments").get(0).path("amount").asString()).isEqualTo("120.00");
    assertThat(corrected.path("payments").get(0).path("paidOn").asString()).isEqualTo("2026-10-26");
    assertThat(cardCreditBalance()).isEqualTo("20.00");
  }

  private JsonNode invoiceForCycle(String cycleStart) throws Exception {
    for (JsonNode invoice : invoices().path("items")) {
      if (cycleStart.equals(invoice.path("cycleStart").asString())) {
        return invoice;
      }
    }
    throw new AssertionError("Fatura não encontrada: " + cycleStart + " em " + invoices());
  }

  private void sync() throws Exception {
    alice
        .post("/api/v1/connections/" + connectionId + "/sync", null)
        .andExpect(status().isAccepted());
    syncJobService.processNext("invoice-it", "invoice-it");
  }

  private JsonNode summary(String query) throws Exception {
    return json(
        alice
            .get("/api/v1/dashboard/summary?" + query)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static JsonNode findTransactionByDescription(JsonNode transactions, String description) {
    for (JsonNode item : transactions.path("items")) {
      if (description.equals(item.path("description").asString())) {
        return item;
      }
    }
    throw new AssertionError("Transação não encontrada: " + description);
  }

  private JsonNode updateProfile(int closing, int due, String limit) throws Exception {
    return json(
        alice
            .put(
                "/api/v1/cards/" + cardId + "/profile",
                Map.of("closingDay", closing, "dueDay", due, "creditLimit", limit))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void configureCycle(int closing, int due, String limit) throws Exception {
    updateProfile(closing, due, limit);
  }

  private String createCardTransaction(String type, String amount, String postedOn)
      throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("accountId", cardId);
    request.put("type", type);
    request.put("postedOn", postedOn);
    request.put("amount", amount);
    request.put("description", "Compra " + amount);
    return json(alice
            .post("/api/v1/transactions", request)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("id")
        .asString();
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

  private String cardCreditBalance() throws Exception {
    return json(alice
            .get("/api/v1/accounts/" + cardId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .path("cardProfile")
        .path("creditBalance")
        .asString();
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

  private JsonNode transactions() throws Exception {
    return json(
        alice
            .get("/api/v1/transactions?limit=100")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static JsonNode findItem(JsonNode items, String kind) {
    for (JsonNode item : items) {
      if (kind.equals(item.path("kind").asString())) {
        return item;
      }
    }
    throw new AssertionError("Item não encontrado: " + kind);
  }

  private JsonNode json(String body) {
    return jsonMapper.readTree(body);
  }
}
