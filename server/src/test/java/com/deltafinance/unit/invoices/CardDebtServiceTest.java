package com.deltafinance.unit.invoices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.invoices.application.CardDebtService;
import com.deltafinance.invoices.application.InvoiceReportingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardDebtServiceTest {

  @Mock private AccountQueryService accountQueryService;
  @Mock private InvoiceReportingService invoiceReportingService;

  @Test
  void usaSaldoDoProvedorQuandoEleSuperaAsFaturas() {
    UUID cardId = UUID.randomUUID();
    when(accountQueryService.list(anyUser())).thenReturn(List.of(card(cardId, "382.83")));
    when(invoiceReportingService.outstandingByCard(anyUser())).thenReturn(Map.of());

    CardDebtService.CardDebt debt = service().byCard(anyUser()).get(cardId);

    assertThat(debt.outstanding()).isEqualByComparingTo("382.83");
    assertThat(debt.invoicedOutstanding()).isEqualByComparingTo("0.00");
    assertThat(debt.unbilledAmount()).isEqualByComparingTo("382.83");
    assertThat(debt.known()).isTrue();
  }

  @Test
  void preservaFaturaQuandoElaSuperaOSaldoDoProvedor() {
    UUID cardId = UUID.randomUUID();
    when(accountQueryService.list(anyUser())).thenReturn(List.of(card(cardId, "10.00")));
    when(invoiceReportingService.outstandingByCard(anyUser()))
        .thenReturn(Map.of(cardId, new BigDecimal("120.00")));

    CardDebtService.CardDebt debt = service().byCard(anyUser()).get(cardId);

    assertThat(debt.outstanding()).isEqualByComparingTo("120.00");
    assertThat(debt.invoicedOutstanding()).isEqualByComparingTo("120.00");
    assertThat(debt.unbilledAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void creditoAFavorNaoViraDividaNegativa() {
    UUID cardId = UUID.randomUUID();
    when(accountQueryService.list(anyUser())).thenReturn(List.of(card(cardId, "-50.00")));
    when(invoiceReportingService.outstandingByCard(anyUser())).thenReturn(Map.of());

    CardDebtService.CardDebt debt = service().byCard(anyUser()).get(cardId);

    assertThat(debt.outstanding()).isEqualByComparingTo("0.00");
    assertThat(debt.providerBalance()).isEqualByComparingTo("-50.00");
    assertThat(debt.known()).isTrue();
  }

  @Test
  void cartaoSemFaturaESemSaldoContinuaDesconhecido() {
    UUID cardId = UUID.randomUUID();
    when(accountQueryService.list(anyUser())).thenReturn(List.of(card(cardId, null)));
    when(invoiceReportingService.outstandingByCard(anyUser())).thenReturn(Map.of());

    CardDebtService.CardDebt debt = service().byCard(anyUser()).get(cardId);

    assertThat(debt.outstanding()).isEqualByComparingTo("0.00");
    assertThat(debt.known()).isFalse();
  }

  private CardDebtService service() {
    return new CardDebtService(accountQueryService, invoiceReportingService);
  }

  private static UUID anyUser() {
    return USER_ID;
  }

  private static final UUID USER_ID = UUID.randomUUID();

  private static AccountView card(UUID id, String balance) {
    return new AccountView(
        id,
        "CREDIT",
        "CREDIT_CARD",
        "IMPORTED",
        "Banco",
        "Cartão",
        "Cartão",
        false,
        false,
        false,
        "0.00",
        "UNKNOWN",
        "UNKNOWN",
        "BRL",
        balance,
        balance,
        null,
        "ACTIVE",
        null,
        null,
        null,
        null);
  }
}
