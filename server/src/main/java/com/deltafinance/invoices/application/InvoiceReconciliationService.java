package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemKind;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceItemSource;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.invoices.domain.ReportingEffect;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ajuste explícito para alinhar o total local ao total da Bill oficial.
 *
 * <p>O ajuste é um item manual de conciliação, nunca uma reescrita de compras. Em faturas pagas a
 * operação é permitida apenas por este fluxo auditado: o status {@code PAID} é preservado e o
 * crédito aplicado é normalizado para manter a identidade {@code total = pagamentos + crédito
 * aplicado}.
 */
@Service
public class InvoiceReconciliationService {

  private static final String ITEM_TABLE = "invoice_item";
  private static final int MAX_DESCRIPTION = 500;

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final ProviderBillService providerBillService;
  private final InvoiceBalanceService balanceService;
  private final CryptoService cryptoService;
  private final AuditService auditService;

  public InvoiceReconciliationService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      ProviderBillService providerBillService,
      InvoiceBalanceService balanceService,
      CryptoService cryptoService,
      AuditService auditService) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.providerBillService = providerBillService;
    this.balanceService = balanceService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
  }

  @Transactional
  public void reconcile(UUID userId, UUID invoiceId, String reason) {
    Invoice invoice =
        invoiceRepository.findByIdAndUserId(invoiceId, userId).orElseThrow(NotFoundException::new);
    // Garante que o total local comparado reflita todos os itens/pagamentos já lançados.
    balanceService.recalculateCard(userId, invoice.getCardAccountId());
    invoice =
        invoiceRepository.findByIdAndUserId(invoiceId, userId).orElseThrow(NotFoundException::new);

    ProviderBillView bill =
        providerBillService
            .views(userId, invoice.getCardAccountId(), List.of(invoice))
            .get(invoiceId);
    if (bill == null) {
      throw new ConflictException(
          "PROVIDER_BILL_MISSING", "Não há Bill oficial conciliada para esta fatura.");
    }
    BigDecimal difference = new BigDecimal(bill.totalDifference());
    if (difference.signum() == 0) {
      throw new ConflictException(
          "INVOICE_ALREADY_RECONCILED",
          "O total local já coincide com a Bill oficial desta fatura.");
    }
    BigDecimal adjustment = difference.negate().setScale(Money.SCALE, RoundingMode.HALF_EVEN);
    ReportingEffect effect =
        adjustment.signum() > 0
            ? ReportingEffect.EXPENSE_INCREASE
            : ReportingEffect.EXPENSE_DECREASE;
    UUID itemId = UUID.randomUUID();
    String description = truncate("Conciliação Bill oficial: " + reason.trim());
    InvoiceItem item =
        new InvoiceItem(
            itemId,
            userId,
            invoiceId,
            null,
            InvoiceItemKind.MANUAL_ADJUSTMENT,
            effect,
            InvoiceItemSource.MANUAL,
            cryptoService.encrypt(
                description, new CryptoContext(userId, ITEM_TABLE, "description", itemId)),
            cryptoService.encrypt(
                adjustment.abs().toPlainString(),
                new CryptoContext(userId, ITEM_TABLE, "amount", itemId)),
            invoice.getClosingDate(),
            null,
            null,
            null);
    itemRepository.save(item);
    if (isClosedOrPaid(invoice.getStatus())) {
      // Fatura fechada/paga permanece histórica; o ajuste é registrado explicitamente.
      invoice.markAdjustment();
      invoiceRepository.save(invoice);
    }
    balanceService.recalculateCard(userId, invoice.getCardAccountId());
    auditService.record(
        userId,
        AuditEventType.INVOICE_RECONCILED,
        "invoice",
        invoiceId,
        Map.of("direction", adjustment.signum() > 0 ? "INCREASE" : "DECREASE"));
  }

  private static boolean isClosedOrPaid(InvoiceStatus status) {
    return status == InvoiceStatus.CLOSED
        || status == InvoiceStatus.PARTIALLY_PAID
        || status == InvoiceStatus.PAID
        || status == InvoiceStatus.OVERDUE;
  }

  private static String truncate(String value) {
    if (value.length() <= MAX_DESCRIPTION) {
      return value;
    }
    return value.substring(0, MAX_DESCRIPTION);
  }
}
