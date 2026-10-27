package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.transactions.application.PaidInvoiceGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementa a consulta de fatura paga usada pelas correções de transação. */
@Component
public class PaidInvoiceGuardAdapter implements PaidInvoiceGuard {

  private final InvoiceItemRepository itemRepository;
  private final InvoiceRepository invoiceRepository;

  public PaidInvoiceGuardAdapter(
      InvoiceItemRepository itemRepository, InvoiceRepository invoiceRepository) {
    this.itemRepository = itemRepository;
    this.invoiceRepository = invoiceRepository;
  }

  @Override
  public boolean isInPaidInvoice(UUID userId, UUID transactionId) {
    for (InvoiceItem item :
        itemRepository.findByUserIdAndTransactionIdAndRemovedAtIsNull(userId, transactionId)) {
      Invoice invoice =
          invoiceRepository.findByIdAndUserId(item.getInvoiceId(), userId).orElse(null);
      if (invoice != null && invoice.getStatus() == InvoiceStatus.PAID) {
        return true;
      }
    }
    return false;
  }
}
