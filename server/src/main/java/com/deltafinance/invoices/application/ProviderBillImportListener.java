package com.deltafinance.invoices.application;

import com.deltafinance.accounts.application.CardBillsFetchedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Persiste as Bills oficiais lidas pelo módulo de contas.
 *
 * <p>Falhas aqui não interrompem a importação de contas/transações: a Bill é apenas referência de
 * conciliação e pode ser recuperada na próxima sincronização.
 */
@Component
public class ProviderBillImportListener {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderBillImportListener.class);

  private final ProviderBillImportService importService;

  public ProviderBillImportListener(ProviderBillImportService importService) {
    this.importService = importService;
  }

  @EventListener
  public void onFetched(CardBillsFetchedEvent event) {
    try {
      importService.importBills(event.userId(), event.cardAccountId(), event.bills());
    } catch (RuntimeException exception) {
      LOG.warn(
          "Não foi possível persistir as faturas oficiais do cartão {}: {}",
          event.cardAccountId(),
          exception.getClass().getSimpleName());
    }
  }
}
