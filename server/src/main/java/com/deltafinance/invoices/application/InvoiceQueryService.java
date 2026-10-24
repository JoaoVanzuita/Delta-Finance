package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoicePaymentRepository;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.pagination.CursorPage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Consultas de faturas, itens e pagamentos com escopo de usuário/cartão. */
@Service
public class InvoiceQueryService {

  private static final String INVOICE_TABLE = "invoice";

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final InvoicePaymentRepository paymentRepository;
  private final InvoiceItemService itemService;
  private final InvoicePaymentService paymentService;
  private final InvoiceGenerationService generationService;
  private final InvoiceBalanceService balanceService;
  private final CardCycleService cardCycleService;
  private final CryptoService cryptoService;

  public InvoiceQueryService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      InvoicePaymentRepository paymentRepository,
      InvoiceItemService itemService,
      InvoicePaymentService paymentService,
      InvoiceGenerationService generationService,
      InvoiceBalanceService balanceService,
      CardCycleService cardCycleService,
      CryptoService cryptoService) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.paymentRepository = paymentRepository;
    this.itemService = itemService;
    this.paymentService = paymentService;
    this.generationService = generationService;
    this.balanceService = balanceService;
    this.cardCycleService = cardCycleService;
    this.cryptoService = cryptoService;
  }

  public CursorPage<InvoiceView> list(UUID userId, UUID cardAccountId, String cursor, int limit) {
    ensureInvoices(userId, cardAccountId);
    List<Invoice> all =
        invoiceRepository.findByUserIdAndCardAccountIdOrderByCycleStartDesc(userId, cardAccountId);
    Cursor decoded = decode(cursor);
    List<Invoice> filtered = all;
    if (decoded != null) {
      filtered =
          all.stream()
              .filter(
                  invoice ->
                      invoice.getCycleStart().isBefore(decoded.cycleStart())
                          || (invoice.getCycleStart().equals(decoded.cycleStart())
                              && invoice.getId().compareTo(decoded.id()) < 0))
              .toList();
    }
    int pageSize = Math.min(100, Math.max(1, limit));
    boolean hasMore = filtered.size() > pageSize;
    List<Invoice> page = hasMore ? filtered.subList(0, pageSize) : filtered;
    List<InvoiceView> views = page.stream().map(this::toView).toList();
    String nextCursor =
        hasMore && !page.isEmpty()
            ? encode(page.getLast().getCycleStart(), page.getLast().getId())
            : null;
    return hasMore ? CursorPage.of(views, nextCursor) : CursorPage.last(views);
  }

  /**
   * Garante que as faturas locais do cartão existam.
   *
   * <p>Usado pela visão patrimonial para que o saldo devedor reflita transações já importadas. Um
   * cartão sem ciclo configurado continua sendo uma lacuna sinalizada, não um erro fatal.
   */
  public void ensureInvoices(UUID userId, UUID cardAccountId) {
    cardCycleService.requireCard(userId, cardAccountId);
    generationService.ensureInvoices(userId, cardAccountId);
  }

  public InvoiceDetailView detail(UUID userId, UUID invoiceId) {
    Invoice invoice =
        invoiceRepository.findByIdAndUserId(invoiceId, userId).orElseThrow(NotFoundException::new);
    balanceService.recalculateInvoice(userId, invoiceId);
    Invoice refreshed =
        invoiceRepository.findByIdAndUserId(invoiceId, userId).orElseThrow(NotFoundException::new);
    List<InvoiceItemView> items =
        itemRepository
            .findByUserIdAndInvoiceIdAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
                userId, invoiceId)
            .stream()
            .map(itemService::toView)
            .toList();
    List<InvoicePaymentView> payments =
        paymentRepository
            .findByUserIdAndInvoiceIdOrderByPaidOnAscCreatedAtAsc(userId, invoiceId)
            .stream()
            .map(paymentService::toView)
            .toList();
    return new InvoiceDetailView(toView(refreshed), items, payments);
  }

  public InvoiceView get(UUID userId, UUID invoiceId) {
    return toView(requireInvoice(userId, invoiceId));
  }

  public Invoice requireInvoice(UUID userId, UUID invoiceId) {
    return invoiceRepository
        .findByIdAndUserId(invoiceId, userId)
        .orElseThrow(NotFoundException::new);
  }

  InvoiceView toView(Invoice invoice) {
    return new InvoiceView(
        invoice.getId(),
        invoice.getCardAccountId(),
        invoice.getCycleStart(),
        invoice.getCycleEnd(),
        invoice.getClosingDate(),
        invoice.getDueDate(),
        invoice.getStatus().name(),
        decrypt(invoice, "total", invoice.getTotalEnc()),
        decrypt(invoice, "payments_total", invoice.getPaymentsTotalEnc()),
        decrypt(invoice, "outstanding", invoice.getOutstandingEnc()),
        decrypt(invoice, "applied_credit", invoice.getAppliedCreditEnc()),
        invoice.getMinimumPaymentEnc() == null
            ? null
            : decrypt(invoice, "minimum_payment", invoice.getMinimumPaymentEnc()),
        invoice.getSource().name(),
        invoice.isHasAdjustments(),
        invoice.getAdjustmentCount());
  }

  private String decrypt(Invoice invoice, String column, byte[] value) {
    return cryptoService.decryptString(
        value, new CryptoContext(invoice.getUserId(), INVOICE_TABLE, column, invoice.getId()));
  }

  private static String encode(LocalDate cycleStart, UUID id) {
    String raw = cycleStart + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf('|');
      if (separator <= 0) {
        throw new IllegalArgumentException("cursor sem separador");
      }
      return new Cursor(
          LocalDate.parse(raw.substring(0, separator)),
          UUID.fromString(raw.substring(separator + 1)));
    } catch (RuntimeException exception) {
      throw new ValidationException("cursor", "Cursor inválido.");
    }
  }

  private record Cursor(LocalDate cycleStart, UUID id) {}
}
