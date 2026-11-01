package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.AccountStatus;
import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.invoices.api.InvoicePaymentRequest;
import com.deltafinance.invoices.domain.Invoice;
import com.deltafinance.invoices.domain.InvoicePayment;
import com.deltafinance.invoices.domain.InvoicePaymentRepository;
import com.deltafinance.invoices.domain.InvoicePaymentSource;
import com.deltafinance.invoices.domain.InvoicePaymentStatus;
import com.deltafinance.invoices.domain.InvoiceRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro de pagamento total/parcial; reduz o saldo devedor sem criar despesa. */
@Service
public class InvoicePaymentService {

  private static final String PAYMENT_TABLE = "invoice_payment";

  private final InvoiceRepository invoiceRepository;
  private final InvoicePaymentRepository paymentRepository;
  private final FinancialAccountRepository accountRepository;
  private final InvoiceBalanceService balanceService;
  private final CryptoService cryptoService;

  public InvoicePaymentService(
      InvoiceRepository invoiceRepository,
      InvoicePaymentRepository paymentRepository,
      FinancialAccountRepository accountRepository,
      InvoiceBalanceService balanceService,
      CryptoService cryptoService) {
    this.invoiceRepository = invoiceRepository;
    this.paymentRepository = paymentRepository;
    this.accountRepository = accountRepository;
    this.balanceService = balanceService;
    this.cryptoService = cryptoService;
  }

  @Transactional
  public InvoicePaymentView add(UUID userId, UUID invoiceId, InvoicePaymentRequest request) {
    Invoice invoice =
        invoiceRepository.findByIdAndUserId(invoiceId, userId).orElseThrow(NotFoundException::new);
    FinancialAccount account =
        accountRepository
            .findByIdAndUserId(request.paymentAccountId(), userId)
            .orElseThrow(NotFoundException::new);
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new ConflictException("ACCOUNT_INACTIVE", "A conta de pagamento está inativa.");
    }
    FinancialAccount cardAccount =
        accountRepository
            .findByIdAndUserId(invoice.getCardAccountId(), userId)
            .orElseThrow(NotFoundException::new);
    if (account.getType() == AccountType.CREDIT) {
      throw new ConflictException(
          "PAYMENT_ACCOUNT_INCOMPATIBLE",
          "A conta de pagamento não pode ser um cartão de crédito.");
    }
    if (!account.getCurrency().equalsIgnoreCase(cardAccount.getCurrency())) {
      throw new ConflictException(
          "PAYMENT_CURRENCY_MISMATCH", "A conta de pagamento deve usar a mesma moeda da fatura.");
    }
    BigDecimal amount = parseAmount(request.amount());
    UUID id = UUID.randomUUID();
    InvoicePayment payment =
        new InvoicePayment(
            id,
            userId,
            invoiceId,
            account.getId(),
            cryptoService.encrypt(
                amount.toPlainString(), new CryptoContext(userId, PAYMENT_TABLE, "amount", id)),
            request.paidOn(),
            InvoicePaymentStatus.CONFIRMED,
            InvoicePaymentSource.MANUAL,
            null);
    paymentRepository.save(payment);
    balanceService.recalculateCard(userId, invoice.getCardAccountId());
    return toView(payment);
  }

  InvoicePaymentView toView(InvoicePayment payment) {
    return new InvoicePaymentView(
        payment.getId(),
        payment.getInvoiceId(),
        payment.getPaymentAccountId(),
        new BigDecimal(
                cryptoService.decryptString(
                    payment.getAmountEnc(),
                    new CryptoContext(
                        payment.getUserId(), PAYMENT_TABLE, "amount", payment.getId())))
            .toPlainString(),
        payment.getPaidOn(),
        payment.getStatus().name(),
        payment.getSource().name());
  }

  private static BigDecimal parseAmount(String value) {
    try {
      BigDecimal amount =
          new BigDecimal(value.trim()).setScale(Money.SCALE, RoundingMode.HALF_EVEN);
      if (amount.signum() <= 0) {
        throw new ValidationException("amount", "O valor do pagamento deve ser positivo.");
      }
      return amount;
    } catch (NumberFormatException exception) {
      throw new ValidationException("amount", "Valor inválido.");
    }
  }
}
