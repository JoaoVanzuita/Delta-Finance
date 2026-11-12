package com.deltafinance.invoices.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.invoices.application.InvoiceReportingService.OutstandingByCard;
import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Saldo devedor dos cartões combinando o ledger local com o saldo do provedor.
 *
 * <p>O saldo de uma conta {@code CREDIT} na Pluggy representa a fatura corrente, não a dívida
 * total. Por isso o total local de faturas não pagas é preservado e apenas a diferença do provedor
 * em relação à fatura corrente é somada. A diferença é uma divergência de sincronização, não uma
 * estimativa de compras ainda não faturadas.
 */
@Service
public class CardDebtService {

  private static final BigDecimal ZERO = Money.scaled(BigDecimal.ZERO);

  private final AccountQueryService accountQueryService;
  private final InvoiceReportingService invoiceReportingService;

  public CardDebtService(
      AccountQueryService accountQueryService, InvoiceReportingService invoiceReportingService) {
    this.accountQueryService = accountQueryService;
    this.invoiceReportingService = invoiceReportingService;
  }

  /** Composição da dívida de um cartão. Valores sempre com duas casas decimais. */
  public record CardDebt(
      UUID cardAccountId,
      BigDecimal invoicedOutstanding,
      BigDecimal currentInvoiceOutstanding,
      BigDecimal providerBalance,
      BigDecimal providerDifference,
      BigDecimal outstanding,
      boolean known) {}

  /** Dívida por cartão do usuário, incluindo cartões sem fatura. */
  public Map<UUID, CardDebt> byCard(UUID userId) {
    Map<UUID, OutstandingByCard> details = invoiceReportingService.outstandingDetailByCard(userId);
    Map<UUID, CardDebt> result = new LinkedHashMap<>();
    for (AccountView account : accountQueryService.list(userId)) {
      if (!"CREDIT".equals(account.type())) {
        continue;
      }
      OutstandingByCard detail = details.get(account.id());
      result.put(
          account.id(),
          debt(
              account.id(),
              detail == null ? ZERO : detail.totalOutstanding(),
              detail == null ? ZERO : detail.currentInvoiceOutstanding(),
              detail != null,
              account.currentBalance()));
    }
    return result;
  }

  /** Dívida de um cartão já carregado, evitando uma segunda leitura de contas. */
  public CardDebt byAccount(
      AccountView account, BigDecimal invoicedOutstanding, BigDecimal currentInvoiceOutstanding) {
    return debt(
        account.id(),
        invoicedOutstanding,
        currentInvoiceOutstanding,
        invoicedOutstanding != null,
        account.currentBalance());
  }

  /** Soma da dívida dos cartões informados (apenas os que existem no mapa). */
  public BigDecimal total(UUID userId, Collection<UUID> cardAccountIds) {
    if (cardAccountIds == null || cardAccountIds.isEmpty()) {
      return ZERO;
    }
    Map<UUID, CardDebt> byCard = byCard(userId);
    BigDecimal total = ZERO;
    for (UUID cardAccountId : cardAccountIds) {
      CardDebt debt = byCard.get(cardAccountId);
      if (debt != null) {
        total = total.add(debt.outstanding());
      }
    }
    return Money.scaled(total);
  }

  private static CardDebt debt(
      UUID cardAccountId,
      BigDecimal invoicedOutstanding,
      BigDecimal currentInvoiceOutstanding,
      boolean hasInvoice,
      String providerBalanceValue) {
    BigDecimal invoiced = nonNegative(invoicedOutstanding);
    BigDecimal currentInvoice = nonNegative(currentInvoiceOutstanding);
    BigDecimal provider = parse(providerBalanceValue);
    BigDecimal providerDebt = provider == null ? ZERO : provider.max(ZERO);
    BigDecimal providerDifference = providerDebt.subtract(currentInvoice).max(ZERO);
    BigDecimal outstanding = invoiced.add(providerDifference).max(ZERO);
    boolean known = hasInvoice || provider != null;
    return new CardDebt(
        cardAccountId,
        invoiced,
        currentInvoice,
        provider == null ? null : Money.scaled(provider),
        Money.scaled(providerDifference),
        Money.scaled(outstanding),
        known);
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null ? ZERO : Money.scaled(value.max(ZERO));
  }

  private static BigDecimal parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
