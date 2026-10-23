package com.deltafinance.invoices.application;

import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoiceItem;
import com.deltafinance.invoices.domain.InvoiceItemRepository;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.invoices.domain.InvoiceSource;
import com.deltafinance.invoices.domain.InvoiceStatus;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.transactions.domain.Transaction;
import com.deltafinance.transactions.domain.TransactionRepository;
import com.deltafinance.transactions.domain.TransactionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação opcional de Bills do provedor.
 *
 * <p>Itens são reconciliados por provider ID ou por fingerprint (cartão, valor, data e descrição)
 * com transações existentes. Item sem correspondência é rejeitado; nunca é persistido órfão. Fatura
 * paga não é reescrita por Bill.
 */
@Service
public class ProviderBillImportService {

  public record ProviderBillItem(
      String providerItemId,
      UUID transactionId,
      String description,
      BigDecimal amount,
      LocalDate occurredOn) {}

  public record ProviderBill(String providerBillId, List<ProviderBillItem> items) {}

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository itemRepository;
  private final TransactionRepository transactionRepository;
  private final InvoiceGenerationService generationService;
  private final InvoiceBalanceService balanceService;
  private final IndexHmacService hmacService;
  private final CryptoService cryptoService;

  public ProviderBillImportService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository itemRepository,
      TransactionRepository transactionRepository,
      InvoiceGenerationService generationService,
      InvoiceBalanceService balanceService,
      IndexHmacService hmacService,
      CryptoService cryptoService) {
    this.invoiceRepository = invoiceRepository;
    this.itemRepository = itemRepository;
    this.transactionRepository = transactionRepository;
    this.generationService = generationService;
    this.balanceService = balanceService;
    this.hmacService = hmacService;
    this.cryptoService = cryptoService;
  }

  @Transactional
  public int importBill(UUID userId, UUID cardAccountId, ProviderBill bill) {
    if (bill == null || bill.items() == null || bill.items().isEmpty()) {
      return 0;
    }
    generationService.ensureInvoices(userId, cardAccountId);
    int imported = 0;
    for (ProviderBillItem providerItem : bill.items()) {
      if (providerItem.providerItemId() == null || providerItem.providerItemId().isBlank()) {
        throw new ValidationException("items", "Item de Bill sem providerItemId.");
      }
      byte[] providerHash = hmacService.hmac("provider-bill-item", providerItem.providerItemId());
      if (itemRepository
          .findByUserIdAndProviderItemIdHashAndRemovedAtIsNull(userId, providerHash)
          .isPresent()) {
        continue;
      }
      Transaction transaction =
          resolveTransaction(userId, cardAccountId, providerItem, providerHash);
      Optional<InvoiceItem> existing =
          itemRepository.findByUserIdAndTransactionIdAndRemovedAtIsNull(
              userId, transaction.getId());
      if (existing.isEmpty()) {
        throw new ValidationException(
            "items",
            "Item de Bill sem item de fatura correspondente; converta explicitamente em transação.");
      }
      InvoiceItem item = existing.get();
      Invoice invoice =
          invoiceRepository
              .findByIdAndUserId(item.getInvoiceId(), userId)
              .orElseThrow(NotFoundException::new);
      if (invoice.getStatus() == InvoiceStatus.PAID) {
        throw new ConflictException(
            "INVOICE_ALREADY_PAID", "Fatura paga não é reescrita por Bill do provedor.");
      }
      item.attachProviderItem(
          providerItem.providerItemId() == null
              ? null
              : cryptoService.encrypt(
                  providerItem.providerItemId(),
                  new CryptoContext(userId, "invoice_item", "provider_item_id", item.getId())),
          providerHash);
      itemRepository.save(item);
      if (bill.providerBillId() != null && !bill.providerBillId().isBlank()) {
        invoice.attachProviderBill(
            cryptoService.encrypt(
                bill.providerBillId(),
                new CryptoContext(userId, "invoice", "provider_bill_id", invoice.getId())),
            hmacService.hmac("provider-bill", bill.providerBillId()),
            InvoiceSource.PROVIDER);
        invoiceRepository.save(invoice);
      }
      imported++;
    }
    balanceService.recalculateCard(userId, cardAccountId);
    return imported;
  }

  private Transaction resolveTransaction(
      UUID userId, UUID cardAccountId, ProviderBillItem providerItem, byte[] providerHash) {
    if (providerItem.transactionId() != null) {
      Transaction transaction =
          transactionRepository
              .findByIdAndUserId(providerItem.transactionId(), userId)
              .orElseThrow(NotFoundException::new);
      if (!transaction.getAccountId().equals(cardAccountId)) {
        throw new ValidationException(
            "transactionId", "Transação do Bill não pertence ao cartão informado.");
      }
      return transaction;
    }
    BigDecimal expected = providerItem.amount().abs().setScale(2, RoundingMode.HALF_EVEN);
    String normalized = normalize(providerItem.description());
    for (Transaction transaction :
        transactionRepository.findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(
            userId, cardAccountId)) {
      if (transaction.getStatus() != TransactionStatus.POSTED
          || !transaction.getPostedOn().equals(providerItem.occurredOn())) {
        continue;
      }
      BigDecimal amount =
          new BigDecimal(
                  cryptoService.decryptString(
                      transaction.getAmountEffectiveEnc(),
                      new CryptoContext(
                          userId, "transaction", "amount_effective", transaction.getId())))
              .abs()
              .setScale(2, RoundingMode.HALF_EVEN);
      String description =
          normalize(
              cryptoService.decryptString(
                  transaction.getDescriptionEffectiveEnc(),
                  new CryptoContext(
                      userId, "transaction", "description_effective", transaction.getId())));
      if (amount.compareTo(expected) == 0 && description.equals(normalized)) {
        return transaction;
      }
    }
    throw new ValidationException(
        "items", "Item de Bill sem correspondência; importação rejeitada.");
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .strip()
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }
}
