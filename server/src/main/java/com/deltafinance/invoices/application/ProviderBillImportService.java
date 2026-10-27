package com.deltafinance.invoices.application;

import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.invoices.domain.ProviderBill;
import com.deltafinance.invoices.domain.ProviderBillPayment;
import com.deltafinance.invoices.domain.ProviderBillPaymentRepository;
import com.deltafinance.invoices.domain.ProviderBillRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste as Bills oficiais do provedor como referência de conciliação.
 *
 * <p>Não altera a fatura local. O upsert é por {@code (card, external_bill_id_hash)} e os
 * pagamentos oficiais são atualizados em conjunto, removendo apenas os que deixaram de existir na
 * Bill. Identificadores externos nunca são gravados em claro.
 */
@Service
public class ProviderBillImportService {

  private static final String BILL_TABLE = "provider_bill";
  private static final String PAYMENT_TABLE = "provider_bill_payment";

  private final ProviderBillRepository billRepository;
  private final ProviderBillPaymentRepository paymentRepository;
  private final InvoiceBalanceService balanceService;
  private final CryptoService cryptoService;
  private final IndexHmacService hmacService;
  private final Clock clock;

  public ProviderBillImportService(
      ProviderBillRepository billRepository,
      ProviderBillPaymentRepository paymentRepository,
      InvoiceBalanceService balanceService,
      CryptoService cryptoService,
      IndexHmacService hmacService,
      Clock clock) {
    this.billRepository = billRepository;
    this.paymentRepository = paymentRepository;
    this.balanceService = balanceService;
    this.cryptoService = cryptoService;
    this.hmacService = hmacService;
    this.clock = clock;
  }

  /** Falha da leitura do provedor cabe ao chamador; aqui os dados já estão normalizados. */
  @Transactional
  public int importBills(UUID userId, UUID cardAccountId, List<PluggyModels.PluggyBill> bills) {
    if (bills == null || bills.isEmpty()) {
      return 0;
    }
    Instant now = clock.now();
    int imported = 0;
    for (PluggyModels.PluggyBill bill : bills) {
      if (bill.externalId() == null
          || bill.externalId().isBlank()
          || bill.dueDate() == null
          || bill.totalAmount() == null) {
        continue;
      }
      byte[] billHash = hmacService.externalIdHash("pluggy-bill", bill.externalId());
      ProviderBill entity =
          billRepository
              .findByUserIdAndCardAccountIdAndExternalBillIdHash(userId, cardAccountId, billHash)
              .orElse(null);
      BigDecimal total = scale(bill.totalAmount());
      BigDecimal minimum =
          bill.minimumPaymentAmount() == null ? null : scale(bill.minimumPaymentAmount());
      BigDecimal paymentsTotal =
          bill.payments() == null
              ? BigDecimal.ZERO.setScale(Money.SCALE)
              : bill.payments().stream()
                  .map(payment -> scale(payment.amount()))
                  .reduce(BigDecimal.ZERO.setScale(Money.SCALE), BigDecimal::add);
      String currency =
          bill.currency() == null || bill.currency().isBlank() ? "BRL" : bill.currency();

      if (entity == null) {
        UUID billId = UUID.randomUUID();
        entity =
            new ProviderBill(
                billId,
                userId,
                cardAccountId,
                billHash,
                bill.closingDate(),
                bill.dueDate(),
                encryptBill(billId, userId, "total", total.toPlainString()),
                minimum == null
                    ? null
                    : encryptBill(billId, userId, "minimum_payment", minimum.toPlainString()),
                encryptBill(billId, userId, "payments_total", paymentsTotal.toPlainString()),
                currency,
                now);
      } else {
        entity.applyProviderData(
            bill.closingDate(),
            bill.dueDate(),
            encryptBill(entity.getId(), userId, "total", total.toPlainString()),
            minimum == null
                ? null
                : encryptBill(entity.getId(), userId, "minimum_payment", minimum.toPlainString()),
            encryptBill(entity.getId(), userId, "payments_total", paymentsTotal.toPlainString()),
            currency,
            now);
      }
      billRepository.save(entity);
      syncPayments(userId, entity.getId(), bill, currency);
      imported++;
    }
    if (imported > 0) {
      // O total oficial participa do cálculo de crédito por pagamento excedente.
      balanceService.recalculateCard(userId, cardAccountId);
    }
    return imported;
  }

  private void syncPayments(
      UUID userId, UUID billId, PluggyModels.PluggyBill bill, String defaultCurrency) {
    List<PluggyModels.PluggyBillPayment> providerPayments =
        bill.payments() == null ? List.of() : bill.payments();
    Set<String> seen = new HashSet<>();
    Set<String> fingerprints = new HashSet<>();
    int index = 0;
    for (PluggyModels.PluggyBillPayment payment : providerPayments) {
      index++;
      if (payment.amount() == null || payment.paymentDate() == null) {
        continue;
      }
      String fingerprint =
          payment.externalId() == null || payment.externalId().isBlank()
              ? "synthetic|"
                  + payment.paymentDate()
                  + "|"
                  + scale(payment.amount()).toPlainString()
                  + "|"
                  + payment.valueType()
              : payment.externalId();
      while (!fingerprints.add(fingerprint)) {
        fingerprint = fingerprint + "#" + index;
      }
      byte[] paymentHash = hmacService.externalIdHash("pluggy-bill-payment", fingerprint);
      seen.add(java.util.HexFormat.of().formatHex(paymentHash));
      ProviderBillPayment entity =
          paymentRepository
              .findByUserIdAndProviderBillIdAndExternalPaymentIdHash(userId, billId, paymentHash)
              .orElse(null);
      BigDecimal amount = scale(payment.amount());
      String currency =
          payment.currency() == null || payment.currency().isBlank()
              ? defaultCurrency
              : payment.currency();
      if (entity == null) {
        UUID paymentId = UUID.randomUUID();
        entity =
            new ProviderBillPayment(
                paymentId,
                userId,
                billId,
                paymentHash,
                payment.paymentDate(),
                encryptPayment(paymentId, userId, amount.toPlainString()),
                truncate(payment.paymentMode(), 32),
                truncate(payment.valueType(), 32),
                currency);
      } else {
        entity.applyProviderData(
            payment.paymentDate(),
            encryptPayment(entity.getId(), userId, amount.toPlainString()),
            truncate(payment.paymentMode(), 32),
            truncate(payment.valueType(), 32),
            currency);
      }
      paymentRepository.save(entity);
    }

    for (ProviderBillPayment existing :
        paymentRepository.findByUserIdAndProviderBillIdOrderByPaymentDateAscCreatedAtAsc(
            userId, billId)) {
      if (!seen.contains(java.util.HexFormat.of().formatHex(existing.getExternalPaymentIdHash()))) {
        paymentRepository.delete(existing);
      }
    }
  }

  private byte[] encryptBill(UUID billId, UUID userId, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, BILL_TABLE, column, billId));
  }

  private byte[] encryptPayment(UUID paymentId, UUID userId, String value) {
    return cryptoService.encrypt(
        value, new CryptoContext(userId, PAYMENT_TABLE, "amount", paymentId));
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(Money.SCALE, RoundingMode.HALF_EVEN);
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
