package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemKind;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.ReportingEffect;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Leitura pública de itens de fatura e saldo devedor para o módulo de relatórios.
 *
 * <p>Itens espelho de transação não geram entrada de relatório, mas participam do total corrente da
 * fatura usado como limite para itens de redução de despesa (FR-077).
 */
@Service
public class InvoiceReportingService {

  private static final String ITEM_TABLE = "invoice_item";
  private static final String INVOICE_TABLE = "invoice";

  private final InvoiceItemRepository itemRepository;
  private final InvoiceRepository invoiceRepository;
  private final CryptoService cryptoService;

  public InvoiceReportingService(
      InvoiceItemRepository itemRepository,
      InvoiceRepository invoiceRepository,
      CryptoService cryptoService) {
    this.itemRepository = itemRepository;
    this.invoiceRepository = invoiceRepository;
    this.cryptoService = cryptoService;
  }

  /**
   * Item sem transação, já com o total da fatura imediatamente antes dele.
   *
   * <p>{@code amount} é sempre magnitude positiva; a direção vem de {@code reportingEffect}.
   */
  public record InvoiceReportItem(
      UUID itemId,
      UUID invoiceId,
      UUID cardAccountId,
      String kind,
      String reportingEffect,
      String source,
      String description,
      BigDecimal amount,
      LocalDate occurredOn,
      BigDecimal invoiceTotalBeforeItem) {}

  /** Itens sem transação no intervalo {@code [from, toExclusive)}, opcionalmente de um cartão. */
  public List<InvoiceReportItem> unlinkedItems(
      UUID userId, LocalDate from, LocalDate toExclusive, UUID accountId) {
    List<InvoiceItem> candidates =
        itemRepository.findUnlinkedForReporting(userId, from, toExclusive, accountId);
    if (candidates.isEmpty()) {
      return List.of();
    }
    Map<UUID, UUID> cardByInvoice = cardAccountsByInvoice(userId);
    Map<UUID, List<InvoiceItem>> allItemsByInvoice = allItemsByInvoice(userId, candidates);
    Set<UUID> candidateIds =
        candidates.stream().map(InvoiceItem::getId).collect(Collectors.toSet());

    List<InvoiceReportItem> result = new ArrayList<>();
    for (Map.Entry<UUID, List<InvoiceItem>> entry : allItemsByInvoice.entrySet()) {
      UUID invoiceId = entry.getKey();
      UUID cardAccountId = cardByInvoice.get(invoiceId);
      BigDecimal running = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
      for (InvoiceItem item : entry.getValue()) {
        BigDecimal amount = decryptAmount(item);
        if (candidateIds.contains(item.getId())) {
          result.add(
              new InvoiceReportItem(
                  item.getId(),
                  invoiceId,
                  cardAccountId,
                  item.getKind().name(),
                  item.getReportingEffect().name(),
                  item.getSource().name(),
                  decryptDescription(item),
                  amount,
                  item.getOccurredOn(),
                  running));
        }
        running = applyToInvoiceTotal(running, item, amount);
      }
    }
    return result;
  }

  /** Saldo devedor consolidado dos cartões informados. */
  public BigDecimal outstandingBalance(UUID userId, Collection<UUID> cardAccountIds) {
    if (cardAccountIds == null || cardAccountIds.isEmpty()) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
    }
    BigDecimal total = BigDecimal.ZERO;
    for (Invoice invoice : invoiceRepository.findByUserIdOrderByDueDateAsc(userId)) {
      if (!cardAccountIds.contains(invoice.getCardAccountId())) {
        continue;
      }
      total =
          total.add(
              new BigDecimal(
                  cryptoService.decryptString(
                      invoice.getOutstandingEnc(),
                      new CryptoContext(userId, INVOICE_TABLE, "outstanding", invoice.getId()))));
    }
    return total.setScale(2, RoundingMode.HALF_EVEN);
  }

  private Map<UUID, UUID> cardAccountsByInvoice(UUID userId) {
    Map<UUID, UUID> result = new HashMap<>();
    for (Invoice invoice : invoiceRepository.findByUserIdOrderByDueDateAsc(userId)) {
      result.put(invoice.getId(), invoice.getCardAccountId());
    }
    return result;
  }

  private Map<UUID, List<InvoiceItem>> allItemsByInvoice(
      UUID userId, List<InvoiceItem> candidates) {
    List<UUID> invoiceIds = candidates.stream().map(InvoiceItem::getInvoiceId).distinct().toList();
    List<InvoiceItem> items =
        itemRepository.findByUserIdAndInvoiceIdInAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
            userId, invoiceIds);
    Map<UUID, List<InvoiceItem>> grouped = new LinkedHashMap<>();
    for (InvoiceItem item : items) {
      grouped.computeIfAbsent(item.getInvoiceId(), ignored -> new ArrayList<>()).add(item);
    }
    return grouped;
  }

  private static BigDecimal applyToInvoiceTotal(
      BigDecimal running, InvoiceItem item, BigDecimal amount) {
    boolean mirror = item.getTransactionId() != null;
    if (mirror) {
      if (item.getKind() == InvoiceItemKind.PURCHASE
          || item.getKind() == InvoiceItemKind.INSTALLMENT) {
        return running.add(amount);
      }
      if (item.getKind() == InvoiceItemKind.CREDIT || item.getKind() == InvoiceItemKind.REFUND) {
        return running.subtract(amount.min(running));
      }
      return running.add(amount);
    }
    if (item.getReportingEffect() == ReportingEffect.EXPENSE_INCREASE) {
      return running.add(amount);
    }
    if (item.getReportingEffect() == ReportingEffect.EXPENSE_DECREASE) {
      return running.subtract(amount.min(running));
    }
    return running;
  }

  private BigDecimal decryptAmount(InvoiceItem item) {
    return new BigDecimal(
        cryptoService.decryptString(
            item.getAmountEnc(),
            new CryptoContext(item.getUserId(), ITEM_TABLE, "amount", item.getId())));
  }

  private String decryptDescription(InvoiceItem item) {
    try {
      return cryptoService.decryptString(
          item.getDescriptionEnc(),
          new CryptoContext(item.getUserId(), ITEM_TABLE, "description", item.getId()));
    } catch (CryptoException exception) {
      return null;
    }
  }
}
