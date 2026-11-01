package com.deltafinance.invoices.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Saldo devedor dos cartões combinando a fatura local com o saldo do provedor.
 *
 * <p>A dívida exibida é {@code max(faturas não pagas, saldo devedor do provedor)}. O Pluggy define
 * {@code balance} de uma conta {@code CREDIT} como o limite utilizado, que inclui compras ainda não
 * faturadas. Usar apenas as faturas locais zera a dívida quando as compras do ciclo atual ainda
 * estão {@code PENDING} ou quando a fatura aberta ainda não recebeu itens.
 *
 * <p>{@code unbilledAmount} é a diferença entre o saldo do provedor e o valor já faturado; serve
 * para explicar a composição na interface. Saldo negativo do provedor (crédito a favor) não reduz a
 * dívida faturada, mas também não é exibido como dívida.
 */
@Service
public class CardDebtService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Money.SCALE);

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
      BigDecimal providerBalance,
      BigDecimal unbilledAmount,
      BigDecimal outstanding,
      boolean known) {}

  /** Dívida por cartão do usuário, incluindo cartões sem fatura. */
  public Map<UUID, CardDebt> byCard(UUID userId) {
    Map<UUID, BigDecimal> invoiced = invoiceReportingService.outstandingByCard(userId);
    Map<UUID, CardDebt> result = new LinkedHashMap<>();
    for (AccountView account : accountQueryService.list(userId)) {
      if (!"CREDIT".equals(account.type())) {
        continue;
      }
      result.put(
          account.id(),
          debt(
              account.id(),
              invoiced.get(account.id()),
              invoiced.containsKey(account.id()),
              account.currentBalance()));
    }
    return result;
  }

  /** Dívida de um cartão já carregado, evitando uma segunda leitura de contas. */
  public CardDebt byAccount(AccountView account, BigDecimal invoicedOutstanding) {
    return debt(
        account.id(), invoicedOutstanding, invoicedOutstanding != null, account.currentBalance());
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
    return total.setScale(Money.SCALE, RoundingMode.HALF_EVEN);
  }

  private static CardDebt debt(
      UUID cardAccountId,
      BigDecimal invoicedOutstanding,
      boolean hasInvoice,
      String providerBalanceValue) {
    BigDecimal invoiced =
        invoicedOutstanding == null ? ZERO : invoicedOutstanding.max(ZERO).setScale(Money.SCALE);
    BigDecimal provider = parse(providerBalanceValue);
    BigDecimal providerDebt = provider == null ? ZERO : provider.max(ZERO);
    BigDecimal outstanding = invoiced.max(providerDebt).max(ZERO);
    BigDecimal unbilled = outstanding.subtract(invoiced).max(ZERO);
    boolean known = hasInvoice || provider != null;
    return new CardDebt(
        cardAccountId,
        invoiced.setScale(Money.SCALE, RoundingMode.HALF_EVEN),
        provider == null ? null : provider.setScale(Money.SCALE, RoundingMode.HALF_EVEN),
        unbilled.setScale(Money.SCALE, RoundingMode.HALF_EVEN),
        outstanding.setScale(Money.SCALE, RoundingMode.HALF_EVEN),
        known);
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
