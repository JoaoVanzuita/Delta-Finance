package com.deltafinance.invoices.application;

import com.deltafinance.accounts.application.CardProfileUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Organiza compras já importadas quando o ciclo do cartão passa a existir ou muda.
 *
 * <p>É um caminho de escrita: as leituras de faturas não geram nem recalculam nada.
 */
@Component
public class CardProfileUpdatedListener {

  private final InvoiceGenerationService generationService;

  public CardProfileUpdatedListener(InvoiceGenerationService generationService) {
    this.generationService = generationService;
  }

  @EventListener
  public void onProfileUpdated(CardProfileUpdatedEvent event) {
    generationService.ensureInvoices(event.userId(), event.cardAccountId());
    generationService.ensureCurrentInvoice(event.userId(), event.cardAccountId());
  }
}
