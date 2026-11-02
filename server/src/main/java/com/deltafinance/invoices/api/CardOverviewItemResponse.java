package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.CardOverviewView;
import com.deltafinance.invoices.application.InvoiceView;
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
    String invoicedOutstanding,
    String unbilledAmount,
    String utilization,
    LocalDate nextDueDate,
    InvoiceView currentInvoice,
    InvoiceView nextDueInvoice,
    int pendingCount,
    int unallocatedCount,
    int pendingPurchaseCount,
    String pendingPurchaseTotal,
    int unallocatedPurchaseCount,
    String unallocatedPurchaseTotal,
    int unallocatedCreditCount,
    String unallocatedCreditTotal) {

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
        item.invoicedOutstanding(),
        item.unbilledAmount(),
        item.utilization(),
        item.nextDueDate(),
        item.currentInvoice() == null ? null : item.currentInvoice(),
        item.nextDueInvoice() == null ? null : item.nextDueInvoice(),
        item.pendingCount(),
        item.unallocatedCount(),
        item.pendingPurchaseCount(),
        item.pendingPurchaseTotal(),
        item.unallocatedPurchaseCount(),
        item.unallocatedPurchaseTotal(),
        item.unallocatedCreditCount(),
        item.unallocatedCreditTotal());
  }
}
