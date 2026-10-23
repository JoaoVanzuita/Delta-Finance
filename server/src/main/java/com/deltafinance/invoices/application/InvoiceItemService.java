package com.deltafinance.invoices.application;

import com.deltafinance.invoices.api.InvoiceItemRequest;
import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemCorrection;
import com.deltafinance.invoices.domain.InvoiceItemCorrectionRepository;
import com.deltafinance.invoices.domain.InvoiceItemKind;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceItemSource;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.invoices.domain.ReportingEffect;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Itens manuais de fatura, correções auditáveis e remoção lógica.
 *
 * <p>Itens espelho exigem transação. Itens sem transação exigem {@code reporting_effect}; o valor é
 * sempre magnitude positiva. Correções preservam valores anteriores em histórico cifrado.
 */
@Service
public class InvoiceItemService {

  private static final String ITEM_TABLE = "invoice_item";
  private static final String CORRECTION_TABLE = "invoice_item_correction";

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final InvoiceItemCorrectionRepository correctionRepository;
  private final TransactionRepository transactionRepository;
  private final InvoiceBalanceService balanceService;
  private final InstallmentService installmentService;
  private final CryptoService cryptoService;
  private final Clock clock;

  public InvoiceItemService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      InvoiceItemCorrectionRepository correctionRepository,
      TransactionRepository transactionRepository,
      InvoiceBalanceService balanceService,
      InstallmentService installmentService,
      CryptoService cryptoService,
      Clock clock) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.correctionRepository = correctionRepository;
    this.transactionRepository = transactionRepository;
    this.balanceService = balanceService;
    this.installmentService = installmentService;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  @Transactional
  public InvoiceItemView add(UUID userId, UUID invoiceId, InvoiceItemRequest request) {
    Invoice invoice = requireInvoice(userId, invoiceId);
    InvoiceStatus previousStatus = invoice.getStatus();
    InvoiceItemKind kind = parseKind(request.kind());
    ReportingEffect effect = parseEffect(request.reportingEffect());
    validateEffect(kind, effect);
    String description = requireDescription(request.description());
    BigDecimal amount = parseAmount(request.amount());
    installmentService.validate(request.installmentNumber(), request.totalInstallments());
    Transaction transaction = resolveTransaction(userId, invoice, kind, request.transactionId());
    if (transaction != null
        && itemRepository
            .findByUserIdAndTransactionIdAndRemovedAtIsNull(userId, transaction.getId())
            .isPresent()) {
      throw new ConflictException("INVOICE_ITEM_EXISTS", "Esta transação já compõe uma fatura.");
    }

    UUID itemId = UUID.randomUUID();
    InvoiceItemSource source =
        transaction == null
            ? InvoiceItemSource.MANUAL
            : transaction.getSource() == TransactionSource.MANUAL
                ? InvoiceItemSource.MANUAL
                : InvoiceItemSource.IMPORTED;
    InvoiceItem item =
        new InvoiceItem(
            itemId,
            userId,
            invoiceId,
            transaction == null ? null : transaction.getId(),
            kind,
            effect,
            source,
            encryptItem(userId, itemId, "description", description),
            encryptItem(userId, itemId, "amount", amount.toPlainString()),
            request.occurredOn(),
            request.installmentNumber(),
            request.totalInstallments(),
            installmentService.planKey(userId, description, request.totalInstallments()));
    itemRepository.save(item);
    if (transaction != null) {
      transaction.setInvoiceId(invoiceId);
      transactionRepository.save(transaction);
    }
    balanceService.recalculateCard(userId, invoice.getCardAccountId());
    Invoice refreshed = requireInvoice(userId, invoiceId);
    if (isClosedOrPaid(previousStatus)) {
      refreshed.markAdjustment();
      invoiceRepository.save(refreshed);
    }
    return toView(item);
  }

  @Transactional
  public InvoiceItemView update(UUID userId, UUID itemId, InvoiceItemRequest request) {
    InvoiceItem item =
        itemRepository.findByIdAndUserId(itemId, userId).orElseThrow(NotFoundException::new);
    if (item.getTransactionId() != null) {
      throw new ConflictException(
          "INVOICE_ITEM_NOT_EDITABLE",
          "Itens espelho de transação não podem ser editados manualmente.");
    }
    Invoice invoice = requireInvoice(userId, item.getInvoiceId());
    InvoiceStatus previousStatus = invoice.getStatus();
    InvoiceItemKind kind = parseKind(request.kind());
    ReportingEffect effect = parseEffect(request.reportingEffect());
    validateEffect(kind, effect);
    String description = requireDescription(request.description());
    BigDecimal amount = parseAmount(request.amount());
    installmentService.validate(request.installmentNumber(), request.totalInstallments());

    UUID transactionId = item.getTransactionId();
    if (request.transactionId() != null && !request.transactionId().equals(transactionId)) {
      Transaction transaction = resolveTransaction(userId, invoice, kind, request.transactionId());
      transactionId = transaction == null ? null : transaction.getId();
    } else if (kind == InvoiceItemKind.PURCHASE || kind == InvoiceItemKind.INSTALLMENT) {
      if (transactionId == null) {
        throw new ValidationException(
            "transactionId", "Compras e parcelas exigem uma transação vinculada.");
      }
    }

    List<CorrectionDraft> drafts = new ArrayList<>();
    String oldDescription = decryptItem(userId, itemId, "description", item.getDescriptionEnc());
    BigDecimal oldAmount = decryptItemAmount(userId, itemId, item.getAmountEnc());
    if (!oldDescription.equals(description)) {
      drafts.add(new CorrectionDraft("description", oldDescription, description));
    }
    if (oldAmount.compareTo(amount) != 0) {
      drafts.add(new CorrectionDraft("amount", oldAmount.toPlainString(), amount.toPlainString()));
    }
    if (item.getKind() != kind) {
      drafts.add(new CorrectionDraft("kind", item.getKind().name(), kind.name()));
    }
    if (item.getReportingEffect() != effect) {
      drafts.add(
          new CorrectionDraft("reportingEffect", item.getReportingEffect().name(), effect.name()));
    }
    if (!item.getOccurredOn().equals(request.occurredOn())) {
      drafts.add(
          new CorrectionDraft(
              "occurredOn", item.getOccurredOn().toString(), request.occurredOn().toString()));
    }

    item.applyEdits(
        kind,
        effect,
        encryptItem(userId, itemId, "description", description),
        encryptItem(userId, itemId, "amount", amount.toPlainString()),
        request.occurredOn(),
        request.installmentNumber(),
        request.totalInstallments());
    item.attachTransaction(transactionId);
    itemRepository.save(item);

    for (CorrectionDraft draft : drafts) {
      UUID correctionId = UUID.randomUUID();
      correctionRepository.save(
          new InvoiceItemCorrection(
              correctionId,
              userId,
              itemId,
              draft.field(),
              draft.previous() == null
                  ? null
                  : cryptoService.encrypt(
                      draft.previous(),
                      new CryptoContext(userId, CORRECTION_TABLE, "old_value", correctionId)),
              cryptoService.encrypt(
                  draft.next(),
                  new CryptoContext(userId, CORRECTION_TABLE, "new_value", correctionId)),
              null,
              clock.now()));
    }

    balanceService.recalculateCard(userId, invoice.getCardAccountId());
    Invoice refreshed = requireInvoice(userId, invoice.getId());
    if (isClosedOrPaid(previousStatus) && !drafts.isEmpty()) {
      refreshed.markAdjustment();
      invoiceRepository.save(refreshed);
    }
    return toView(item);
  }

  @Transactional
  public void remove(UUID userId, UUID itemId) {
    InvoiceItem item =
        itemRepository.findByIdAndUserId(itemId, userId).orElseThrow(NotFoundException::new);
    if (item.getTransactionId() != null) {
      throw new ConflictException(
          "INVOICE_ITEM_NOT_EDITABLE",
          "Itens espelho de transação não podem ser removidos manualmente.");
    }
    Invoice invoice = requireInvoice(userId, item.getInvoiceId());
    InvoiceStatus previousStatus = invoice.getStatus();
    item.remove(clock.now());
    itemRepository.save(item);
    if (item.getTransactionId() != null) {
      transactionRepository
          .findByIdAndUserId(item.getTransactionId(), userId)
          .ifPresent(
              transaction -> {
                transaction.setInvoiceId(null);
                transactionRepository.save(transaction);
              });
    }
    balanceService.recalculateCard(userId, invoice.getCardAccountId());
    Invoice refreshed = requireInvoice(userId, invoice.getId());
    if (isClosedOrPaid(previousStatus)) {
      refreshed.markAdjustment();
      invoiceRepository.save(refreshed);
    }
  }

  public List<InvoiceItemCorrectionView> corrections(UUID userId, UUID itemId) {
    InvoiceItem item =
        itemRepository.findByIdAndUserId(itemId, userId).orElseThrow(NotFoundException::new);
    return correctionRepository
        .findByUserIdAndInvoiceItemIdOrderByChangedAtAsc(userId, item.getId())
        .stream()
        .map(this::toCorrectionView)
        .toList();
  }

  private Transaction resolveTransaction(
      UUID userId, Invoice invoice, InvoiceItemKind kind, UUID transactionId) {
    if (transactionId == null) {
      if (kind == InvoiceItemKind.PURCHASE || kind == InvoiceItemKind.INSTALLMENT) {
        throw new ValidationException(
            "transactionId", "Compras e parcelas exigem uma transação vinculada.");
      }
      return null;
    }
    Transaction transaction =
        transactionRepository
            .findByIdAndUserId(transactionId, userId)
            .orElseThrow(NotFoundException::new);
    if (!transaction.getAccountId().equals(invoice.getCardAccountId())) {
      throw new ValidationException(
          "transactionId", "A transação deve pertencer ao mesmo cartão da fatura.");
    }
    return transaction;
  }

  private static void validateEffect(InvoiceItemKind kind, ReportingEffect effect) {
    if (kind == InvoiceItemKind.INTEREST
        || kind == InvoiceItemKind.FEE
        || kind == InvoiceItemKind.TAX) {
      if (effect != ReportingEffect.EXPENSE_INCREASE) {
        throw new ValidationException(
            "reportingEffect", "Juros, encargos e tarifas aumentam a despesa.");
      }
    }
    if (kind == InvoiceItemKind.CREDIT || kind == InvoiceItemKind.REFUND) {
      if (effect != ReportingEffect.EXPENSE_DECREASE) {
        throw new ValidationException("reportingEffect", "Créditos e estornos reduzem a despesa.");
      }
    }
  }

  InvoiceItemView toView(InvoiceItem item) {
    long corrections =
        correctionRepository.countByUserIdAndInvoiceItemId(item.getUserId(), item.getId());
    return new InvoiceItemView(
        item.getId(),
        item.getInvoiceId(),
        item.getTransactionId(),
        item.getKind().name(),
        item.getReportingEffect().name(),
        item.getSource().name(),
        decryptItem(item.getUserId(), item.getId(), "description", item.getDescriptionEnc()),
        decryptItemAmount(item.getUserId(), item.getId(), item.getAmountEnc()).toPlainString(),
        item.getOccurredOn(),
        item.getInstallmentNumber(),
        item.getTotalInstallments(),
        corrections);
  }

  private InvoiceItemCorrectionView toCorrectionView(InvoiceItemCorrection correction) {
    return new InvoiceItemCorrectionView(
        correction.getId(),
        correction.getInvoiceItemId(),
        correction.getFieldName(),
        decryptCorrection(correction, "old_value", correction.getOldValueEnc()),
        decryptCorrection(correction, "new_value", correction.getNewValueEnc()),
        decryptCorrection(correction, "reason", correction.getReasonEnc()),
        correction.getChangedAt());
  }

  private Invoice requireInvoice(UUID userId, UUID invoiceId) {
    return invoiceRepository
        .findByIdAndUserId(invoiceId, userId)
        .orElseThrow(NotFoundException::new);
  }

  private static boolean isClosedOrPaid(InvoiceStatus status) {
    return status == InvoiceStatus.CLOSED
        || status == InvoiceStatus.PARTIALLY_PAID
        || status == InvoiceStatus.PAID
        || status == InvoiceStatus.OVERDUE;
  }

  private String decryptItem(UUID userId, UUID itemId, String column, byte[] value) {
    if (value == null) {
      return null;
    }
    try {
      return cryptoService.decryptString(
          value, new CryptoContext(userId, ITEM_TABLE, column, itemId));
    } catch (CryptoException exception) {
      return null;
    }
  }

  private BigDecimal decryptItemAmount(UUID userId, UUID itemId, byte[] value) {
    return new BigDecimal(decryptItem(userId, itemId, "amount", value));
  }

  private String decryptCorrection(InvoiceItemCorrection correction, String column, byte[] value) {
    if (value == null) {
      return null;
    }
    return cryptoService.decryptString(
        value,
        new CryptoContext(correction.getUserId(), CORRECTION_TABLE, column, correction.getId()));
  }

  private byte[] encryptItem(UUID userId, UUID itemId, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, ITEM_TABLE, column, itemId));
  }

  private static InvoiceItemKind parseKind(String value) {
    try {
      return InvoiceItemKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException exception) {
      throw new ValidationException("kind", "Tipo de item inválido.");
    }
  }

  private static ReportingEffect parseEffect(String value) {
    try {
      return ReportingEffect.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException exception) {
      throw new ValidationException("reportingEffect", "Efeito do item inválido.");
    }
  }

  private static String requireDescription(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("description", "Informe a descrição.");
    }
    String trimmed = value.strip();
    if (trimmed.length() > 500) {
      throw new ValidationException(
          "description", "A descrição deve ter no máximo 500 caracteres.");
    }
    return trimmed;
  }

  private static BigDecimal parseAmount(String value) {
    try {
      BigDecimal amount =
          new BigDecimal(value.trim()).setScale(Money.SCALE, RoundingMode.HALF_EVEN);
      if (amount.signum() <= 0) {
        throw new ValidationException("amount", "O valor do item deve ser positivo.");
      }
      return amount;
    } catch (NumberFormatException exception) {
      throw new ValidationException("amount", "Valor inválido.");
    }
  }

  private record CorrectionDraft(String field, String previous, String next) {}
}
