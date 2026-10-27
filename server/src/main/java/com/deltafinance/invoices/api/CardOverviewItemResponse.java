package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.CardOverviewView;
import java.time.LocalDate;
import java.util.UUID;

public record CardOverviewItemResponse(
    UUID accountId,
    String alias,
    String institutionName,
    String status,
    String currency,
    boolean hasCycle,
    Integer closingDay,
    Integer dueDay,
    String creditLimit,
    String creditBalance,
    String availableCreditLimit,
    String brand,
    String level,
    String last4,
    String usedAmount,
    String outstanding,
    String utilization,
    LocalDate nextDueDate,
    InvoiceResponse currentInvoice,
    InvoiceResponse nextDueInvoice,
    int unallocatedCount) {

  public static CardOverviewItemResponse from(CardOverviewView.CardItem item) {
    return new CardOverviewItemResponse(
        item.accountId(),
        item.alias(),
        item.institutionName(),
        item.status(),
        item.currency(),
        item.hasCycle(),
        item.closingDay(),
        item.dueDay(),
        item.creditLimit(),
        item.creditBalance(),
        item.availableCreditLimit(),
        item.brand(),
        item.level(),
        item.last4(),
        item.usedAmount(),
        item.outstanding(),
        item.utilization(),
        item.nextDueDate(),
        item.currentInvoice() == null ? null : InvoiceResponse.from(item.currentInvoice()),
        item.nextDueInvoice() == null ? null : InvoiceResponse.from(item.nextDueInvoice()),
        item.unallocatedCount());
  }
}
