package com.deltafinance.reporting.application;

import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.invoices.application.InvoiceReportingService;
import com.deltafinance.invoices.application.InvoiceReportingService.InvoiceReportItem;
import com.deltafinance.reporting.application.ReportEntry.Direction;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.transactions.application.TransactionQueryService;
import com.deltafinance.transactions.application.TransactionView;
import com.deltafinance.transactions.domain.TransactionStatus;
import com.deltafinance.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Monta as entradas do drill-down e dos totais, incluindo faturas e estornos. */
@Component
class ReportEntryAssembler {

  private static final List<TransactionStatus> EFFECTIVE_STATUSES =
      List.of(TransactionStatus.POSTED, TransactionStatus.RECONCILED);

  private final TransactionQueryService transactionQueryService;
  private final InvoiceReportingService invoiceReportingService;

  ReportEntryAssembler(
      TransactionQueryService transactionQueryService,
      InvoiceReportingService invoiceReportingService) {
    this.transactionQueryService = transactionQueryService;
    this.invoiceReportingService = invoiceReportingService;
  }

  private BigDecimal remainingReduction(UUID userId, UUID originalId, LocalDate from) {
    TransactionView original;
    try {
      original = transactionQueryService.get(userId, originalId);
    } catch (NotFoundException exception) {
      return null;
    }
    BigDecimal originalAmount = MoneyAggregator.parse(original.amount()).abs();
    BigDecimal prior =
        transactionQueryService
            .listLinkedReductionsBefore(userId, originalId, from, EFFECTIVE_STATUSES)
            .stream()
            .map(view -> MoneyAggregator.parse(view.amount()).abs())
            .reduce(MoneyAggregator.zero(), MoneyAggregator::sum);
    return originalAmount.subtract(prior).max(MoneyAggregator.zero());
  }

  List<ReportEntry> entries(UUID userId, ResolvedReportQuery resolved) {
    List<ReportEntry> entries = new ArrayList<>();
    List<InvoiceReportItem> decreases = new ArrayList<>();
    Map<UUID, BigDecimal> remainingReductions = new HashMap<>();
    Set<UUID> creditCardAccounts = new HashSet<>();
    for (AccountView account : resolved.accounts()) {
      if ("CREDIT".equals(account.type())) {
        creditCardAccounts.add(account.id());
      }
    }
    Set<UUID> installmentTransactionIds = invoiceReportingService.installmentTransactionIds(userId);

    UUID effectiveCategoryId =
        ReportQueryResolver.UNCATEGORIZED_ID.equals(resolved.categoryId())
            ? null
            : resolved.categoryId();

    for (TransactionView transaction :
        transactionQueryService.listForReporting(
            userId,
            resolved.from(),
            resolved.toExclusive(),
            singleAccountId(resolved),
            effectiveCategoryId,
            resolved.type(),
            resolved.origin(),
            EFFECTIVE_STATUSES)) {
      if (!isTransactionIncluded(transaction, resolved)) {
        continue;
      }
      if (installmentTransactionIds.contains(transaction.id())) {
        // Compra parcelada: cada parcela é contabilizada no seu próprio ciclo.
        continue;
      }
      BigDecimal amount = MoneyAggregator.parse(transaction.amount());
      if (amount.signum() == 0) {
        continue;
      }
      Direction direction;
      BigDecimal reportAmount;
      if ("REFUND".equals(transaction.type()) || "CHARGEBACK".equals(transaction.type())) {
        UUID relatedId = transaction.relatedTransactionId();
        if (relatedId == null) {
          // ADR-0001: reembolso sem vínculo não reduz despesa nem vira receita.
          continue;
        }
        direction = Direction.EXPENSE_REDUCTION;
        reportAmount = amount.abs();
        BigDecimal remaining = remainingReductions.get(relatedId);
        if (remaining == null && !remainingReductions.containsKey(relatedId)) {
          remaining = remainingReduction(userId, relatedId, resolved.from());
          if (remaining != null) {
            remainingReductions.put(relatedId, remaining);
          }
        }
        if (remaining != null) {
          if (remaining.signum() <= 0) {
            // Excedente ao valor efetivo do original não reduz despesas novamente (FR-057).
            continue;
          }
          BigDecimal applied = Money.scaled(reportAmount.min(remaining));
          remainingReductions.put(relatedId, remaining.subtract(applied));
          reportAmount = applied;
        }
      } else if (amount.signum() > 0) {
        // Entrada em cartão é crédito/estorno: reduz despesa e nunca vira receita.
        direction =
            creditCardAccounts.contains(transaction.accountId())
                ? Direction.EXPENSE_REDUCTION
                : Direction.INCOME;
        reportAmount = amount;
      } else {
        direction = Direction.EXPENSE;
        reportAmount = amount;
      }
      entries.add(
          new ReportEntry(
              transaction.id(),
              "TRANSACTION",
              transaction.id(),
              transaction.source(),
              transaction.postedOn(),
              reportAmount,
              transaction.description() == null ? "" : transaction.description(),
              transaction.categoryId(),
              transaction.accountId(),
              transaction.invoiceId(),
              transaction.id(),
              transaction.type(),
              direction));
    }

    entries.addAll(
        installmentEntries(userId, resolved, effectiveCategoryId, installmentTransactionIds));

    boolean includesExpenseIncrease =
        resolved.type() == null || resolved.type() == TransactionType.DEBIT;
    boolean includesExpenseDecrease =
        resolved.type() == null
            || resolved.type() == TransactionType.REFUND
            || resolved.type() == TransactionType.CHARGEBACK;
    List<InvoiceReportItem> invoiceItems =
        invoiceReportingService.unlinkedItems(
            userId, resolved.from(), resolved.toExclusive(), singleAccountId(resolved));
    for (InvoiceReportItem item : invoiceItems) {
      if (resolved.categoryId() != null
          && !ReportQueryResolver.UNCATEGORIZED_ID.equals(resolved.categoryId())) {
        // Itens de fatura não carregam categoria própria; só aparecem sem filtro ou em
        // "Sem categoria".
        continue;
      }
      if (!accountIncluded(resolved, item.cardAccountId())) {
        continue;
      }
      String origin = normalizedInvoiceOrigin(item.source());
      if (resolved.origin() != null && !resolved.origin().name().equals(origin)) {
        continue;
      }
      if (!accountCurrencyIsBrl(resolved, item.cardAccountId())) {
        continue;
      }
      if (!MoneyAggregator.withinRange(item.amount(), resolved.minAmount(), resolved.maxAmount())) {
        continue;
      }
      if ("EXPENSE_INCREASE".equals(item.reportingEffect())) {
        if (!includesExpenseIncrease) {
          continue;
        }
        entries.add(invoiceEntry(item, origin, item.amount().negate(), Direction.EXPENSE));
      } else if ("EXPENSE_DECREASE".equals(item.reportingEffect())) {
        if (includesExpenseDecrease) {
          decreases.add(item);
        }
      }
    }

    Map<UUID, BigDecimal> grossExpensesByAccount =
        decreases.isEmpty()
            ? Map.of()
            : grossCardExpenses(userId, resolved, invoiceItems, installmentTransactionIds);
    for (InvoiceReportItem item : decreases) {
      BigDecimal cardExpenses =
          grossExpensesByAccount.getOrDefault(item.cardAccountId(), BigDecimal.ZERO);
      BigDecimal effective =
          item.amount()
              .min(item.invoiceTotalBeforeItem().max(BigDecimal.ZERO))
              .min(cardExpenses)
              .max(BigDecimal.ZERO);
      if (effective.signum() <= 0) {
        continue;
      }
      entries.add(
          invoiceEntry(
              item,
              normalizedInvoiceOrigin(item.source()),
              effective,
              Direction.EXPENSE_REDUCTION));
    }

    String direction = resolved.direction();
    if (direction != null && !direction.isBlank()) {
      if ("INCOME".equalsIgnoreCase(direction)) {
        entries.removeIf(entry -> entry.direction() != Direction.INCOME);
      } else if ("EXPENSE".equalsIgnoreCase(direction)) {
        entries.removeIf(entry -> entry.direction() == Direction.INCOME);
      } else {
        throw new ValidationException("direction", "Direção deve ser EXPENSE ou INCOME.");
      }
    }
    entries.sort(Comparator.comparing(ReportEntry::occurredOn).thenComparing(ReportEntry::id));
    return entries;
  }

  private Map<UUID, BigDecimal> grossCardExpenses(
      UUID userId,
      ResolvedReportQuery resolved,
      List<InvoiceReportItem> invoiceItems,
      Set<UUID> installmentTransactionIds) {
    Map<UUID, BigDecimal> gross = new HashMap<>();
    for (TransactionView transaction :
        transactionQueryService.listForReporting(
            userId,
            resolved.from(),
            resolved.toExclusive(),
            singleAccountId(resolved),
            null,
            null,
            null,
            EFFECTIVE_STATUSES)) {
      if (!accountIncluded(resolved, transaction.accountId())
          || !"BRL".equalsIgnoreCase(transaction.currency())
          || "TRANSFER".equals(transaction.type())
          || (transaction.categoryId() != null
              && resolved.transferCategoryIds().contains(transaction.categoryId()))
          || "MATCHED".equals(transaction.reconciliationState())
          || installmentTransactionIds.contains(transaction.id())
          || !isCreditAccount(resolved, transaction.accountId())) {
        continue;
      }
      BigDecimal amount = MoneyAggregator.parse(transaction.amount());
      if (amount.signum() < 0) {
        gross.merge(transaction.accountId(), amount.abs(), BigDecimal::add);
      }
    }
    for (InvoiceReportItem item : invoiceItems) {
      if ("EXPENSE_INCREASE".equals(item.reportingEffect())
          && isCreditAccount(resolved, item.cardAccountId())) {
        gross.merge(item.cardAccountId(), item.amount(), BigDecimal::add);
      }
    }
    if (!installmentTransactionIds.isEmpty()) {
      for (InvoiceReportItem item :
          invoiceReportingService.installmentItems(
              userId, resolved.from(), resolved.toExclusive(), singleAccountId(resolved))) {
        if (isCreditAccount(resolved, item.cardAccountId())) {
          gross.merge(item.cardAccountId(), item.amount(), BigDecimal::add);
        }
      }
    }
    return gross;
  }

  private static boolean accountIncluded(ResolvedReportQuery resolved, UUID accountId) {
    return resolved.accountIds().isEmpty() || resolved.accountIds().contains(accountId);
  }

  /** O repositório ainda filtra por uma conta; para múltiplas, o recorte é feito em memória. */
  private static UUID singleAccountId(ResolvedReportQuery resolved) {
    return resolved.accountIds().size() == 1 ? resolved.accountIds().iterator().next() : null;
  }

  private boolean isCreditAccount(ResolvedReportQuery resolved, UUID accountId) {
    for (AccountView account : resolved.accounts()) {
      if (account.id().equals(accountId)) {
        return "CREDIT".equals(account.type());
      }
    }
    return false;
  }

  private List<ReportEntry> installmentEntries(
      UUID userId,
      ResolvedReportQuery resolved,
      UUID effectiveCategoryId,
      Set<UUID> installmentTransactionIds) {
    if (installmentTransactionIds.isEmpty()) {
      return List.of();
    }
    List<InvoiceReportItem> items =
        invoiceReportingService.installmentItems(
            userId, resolved.from(), resolved.toExclusive(), singleAccountId(resolved));
    if (items.isEmpty()) {
      return List.of();
    }
    Map<UUID, TransactionView> parents = new HashMap<>();
    List<ReportEntry> result = new ArrayList<>();
    for (InvoiceReportItem item : items) {
      if (!accountIncluded(resolved, item.cardAccountId())) {
        continue;
      }
      if (!accountCurrencyIsBrl(resolved, item.cardAccountId())) {
        continue;
      }
      if (!MoneyAggregator.withinRange(item.amount(), resolved.minAmount(), resolved.maxAmount())) {
        continue;
      }
      TransactionView parent = parents.get(item.transactionId());
      if (parent == null) {
        try {
          parent = transactionQueryService.get(userId, item.transactionId());
        } catch (NotFoundException exception) {
          continue;
        }
        parents.put(item.transactionId(), parent);
      }
      if (resolved.categoryId() != null
          && !java.util.Objects.equals(parent.categoryId(), effectiveCategoryId)) {
        continue;
      }
      if (resolved.type() != null && !resolved.type().name().equals(parent.type())) {
        continue;
      }
      String origin = normalizedInvoiceOrigin(item.source());
      if (resolved.origin() != null && !resolved.origin().name().equals(origin)) {
        continue;
      }

      Direction direction;
      BigDecimal amount;
      if ("PURCHASE".equals(item.kind()) || "INSTALLMENT".equals(item.kind())) {
        direction = Direction.EXPENSE;
        amount = item.amount().negate();
      } else if ("CREDIT".equals(item.kind()) || "REFUND".equals(item.kind())) {
        direction = Direction.EXPENSE_REDUCTION;
        amount = item.amount();
      } else if ("EXPENSE_INCREASE".equals(item.reportingEffect())) {
        direction = Direction.EXPENSE;
        amount = item.amount().negate();
      } else if ("EXPENSE_DECREASE".equals(item.reportingEffect())) {
        direction = Direction.EXPENSE_REDUCTION;
        amount = item.amount();
      } else {
        continue;
      }
      result.add(
          new ReportEntry(
              item.itemId(),
              "INVOICE_ITEM",
              item.itemId(),
              origin,
              item.occurredOn(),
              amount,
              item.description() == null ? "" : item.description(),
              parent.categoryId(),
              item.cardAccountId(),
              item.invoiceId(),
              parent.id(),
              parent.type(),
              direction));
    }
    return result;
  }

  private ReportEntry invoiceEntry(
      InvoiceReportItem item, String origin, BigDecimal amount, Direction direction) {
    return new ReportEntry(
        item.itemId(),
        "INVOICE_ITEM",
        item.itemId(),
        origin,
        item.occurredOn(),
        amount,
        item.description() == null ? "" : item.description(),
        null,
        item.cardAccountId(),
        item.invoiceId(),
        null,
        item.kind(),
        direction);
  }

  boolean isTransactionIncluded(TransactionView transaction, ResolvedReportQuery resolved) {
    if (!accountIncluded(resolved, transaction.accountId())) {
      return false;
    }
    if (!"BRL".equalsIgnoreCase(transaction.currency())) {
      return false;
    }
    if (isInvestmentContributionCredit(transaction, resolved)) {
      // Crédito em conta marcada como investimento é aporte, não receita. Proventos continuam
      // entrando porque a categoria é INCOME. A regra evita que um aporte ainda não conciliado
      // infle receitas e apareça ao mesmo tempo no indicador de aportes.
      return false;
    }
    if ("TRANSFER".equals(transaction.type())) {
      return false;
    }
    if (transaction.categoryId() != null
        && resolved.transferCategoryIds().contains(transaction.categoryId())) {
      return false;
    }
    if ("MATCHED".equals(transaction.reconciliationState())) {
      return false;
    }
    if (ReportQueryResolver.UNCATEGORIZED_ID.equals(resolved.categoryId())
        && transaction.categoryId() != null) {
      return false;
    }
    return MoneyAggregator.withinRange(
        MoneyAggregator.parse(transaction.amount()), resolved.minAmount(), resolved.maxAmount());
  }

  private static String normalizedInvoiceOrigin(String source) {
    return "MANUAL".equals(source) ? "MANUAL" : "IMPORTED";
  }

  private boolean isInvestmentContributionCredit(
      TransactionView transaction, ResolvedReportQuery resolved) {
    if (MoneyAggregator.parse(transaction.amount()).signum() <= 0) {
      return false;
    }
    AccountView account = accountById(resolved, transaction.accountId());
    if (account == null || !account.investmentAccount()) {
      return false;
    }
    CategoryKind kind =
        transaction.categoryId() == null
            ? null
            : resolved.categoryKinds().get(transaction.categoryId());
    return kind != CategoryKind.INCOME;
  }

  private static AccountView accountById(ResolvedReportQuery resolved, UUID accountId) {
    for (AccountView account : resolved.accounts()) {
      if (account.id().equals(accountId)) {
        return account;
      }
    }
    return null;
  }

  private boolean accountCurrencyIsBrl(ResolvedReportQuery resolved, UUID accountId) {
    for (AccountView account : resolved.accounts()) {
      if (account.id().equals(accountId)) {
        return "BRL".equalsIgnoreCase(account.currency());
      }
    }
    return false;
  }
}
