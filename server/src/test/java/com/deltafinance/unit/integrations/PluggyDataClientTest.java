package com.deltafinance.unit.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.integrations.infrastructure.PluggyDataClient;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyRateLimiter;
import com.deltafinance.shared.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato do cliente de transações da Pluggy.
 *
 * <p>Regressão: o cursor retornado em {@code next} é uma query string pronta; enviá-lo inteiro como
 * {@code after} faz a segunda página retornar 400. O filtro de data também deve usar {@code
 * dateFrom}, não {@code from}.
 */
class PluggyDataClientTest {

  private MockWebServer server;
  private PluggyDataClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    RestClient restClient = RestClient.builder().baseUrl(server.url("/").toString()).build();
    client =
        new PluggyDataClient(
            restClient,
            JsonMapper.shared(),
            new PluggyRateLimiter(
                Clock.fixed(
                    Instant.parse("2026-10-24T12:00:00Z"), ZoneId.of("America/Sao_Paulo"))));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void segueOCursorDeNextComoQueryStringEUsaDateFrom() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "results": [
                    {"id": "tx-1", "accountId": "acc-1", "description": "Primeira",
                     "amount": -10.00, "date": "2026-10-01", "status": "POSTED",
                     "currencyCode": "BRL"}
                  ],
                  "next": "?accountId=acc-1&after=YWJjPT0%3D"
                }
                """));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "results": [
                    {"id": "tx-2", "accountId": "acc-1", "description": "Segunda",
                     "amount": -20.00, "date": "2026-10-02", "status": "POSTED",
                     "currencyCode": "BRL"}
                  ],
                  "next": null
                }
                """));

    PluggyModels.PluggyTransactionPage page =
        client.listTransactions("api-key", "acc-1", LocalDate.of(2026, 10, 1), null);

    assertThat(page.items())
        .extracting(PluggyModels.PluggyTransaction::externalId)
        .containsExactly("tx-1", "tx-2");

    RecordedRequest first = server.takeRequest();
    assertThat(first.getRequestUrl().queryParameter("dateFrom")).isEqualTo("2026-10-01");
    assertThat(first.getRequestUrl().queryParameter("from")).isNull();
    assertThat(first.getRequestUrl().queryParameter("pageSize")).isNull();

    RecordedRequest second = server.takeRequest();
    // O valor é decodificado e reencodado como parâmetro normal da requisição.
    assertThat(second.getRequestUrl().queryParameter("after")).isEqualTo("YWJjPT0=");
    assertThat(second.getRequestUrl().queryParameter("dateFrom")).isEqualTo("2026-10-01");
  }

  @Test
  void leTotalMinimoMoedaEPagamentosOficiaisDaBill() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "results": [
                    {"id": "bill-1", "billClosingDate": "2026-09-05T00:00:00.000Z",
                     "dueDate": "2026-09-15T00:00:00.000Z", "totalAmount": 100.00,
                     "minimumPaymentAmount": 10.00, "totalAmountCurrencyCode": "BRL",
                     "payments": [
                       {"id": "pay-1", "paymentDate": "2026-09-10T00:00:00.000Z",
                        "amount": 100.00, "paymentMode": "PIX",
                        "valueType": "FULL_PAYMENT", "currencyCode": "BRL"}
                     ]}
                  ],
                  "page": 1,
                  "totalPages": 1
                }
                """));

    var bills = client.listBills("api-key", "acc-1");

    assertThat(bills).hasSize(1);
    var bill = bills.getFirst();
    assertThat(bill.externalId()).isEqualTo("bill-1");
    assertThat(bill.closingDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    assertThat(bill.dueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(bill.totalAmount()).isEqualByComparingTo("100.00");
    assertThat(bill.minimumPaymentAmount()).isEqualByComparingTo("10.00");
    assertThat(bill.currency()).isEqualTo("BRL");
    assertThat(bill.payments()).hasSize(1);
    assertThat(bill.payments().getFirst().externalId()).isEqualTo("pay-1");
    assertThat(bill.payments().getFirst().amount()).isEqualByComparingTo("100.00");
    assertThat(bill.payments().getFirst().paymentMode()).isEqualTo("PIX");
    assertThat(bill.payments().getFirst().valueType()).isEqualTo("FULL_PAYMENT");
  }
}
