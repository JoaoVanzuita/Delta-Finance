package com.deltafinance.invoices.application;

import com.deltafinance.transactions.application.CardRecalculationRequestedEvent;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Executa o recálculo adiado de cada cartão afetado por um lote de importação. */
@Component
public class CardRecalculationListener {

  private final InvoiceBalanceService balanceService;

  public CardRecalculationListener(InvoiceBalanceService balanceService) {
    this.balanceService = balanceService;
  }

  @EventListener
  public void onRequested(CardRecalculationRequestedEvent event) {
    for (UUID cardAccountId : event.cardAccountIds()) {
      balanceService.recalculateCard(event.userId(), cardAccountId);
    }
  }
}
