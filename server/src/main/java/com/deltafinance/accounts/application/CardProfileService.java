package com.deltafinance.accounts.application;

import com.deltafinance.accounts.api.CardProfileRequest;
import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.accounts.domain.CardProfileRepository;
import com.deltafinance.accounts.domain.CardProfileSource;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Configuração de ciclo e limite do cartão; não reescreve faturas fechadas/pagas. */
@Service
public class CardProfileService {

  private static final String TABLE = "card_profile";
  private static final String LIMIT_COLUMN = "credit_limit";
  private static final String BALANCE_COLUMN = "credit_balance";

  private final CardProfileRepository cardProfileRepository;
  private final FinancialAccountRepository accountRepository;
  private final AccountQueryService accountQueryService;
  private final CryptoService cryptoService;
  private final ApplicationEventPublisher eventPublisher;

  public CardProfileService(
      CardProfileRepository cardProfileRepository,
      FinancialAccountRepository accountRepository,
      AccountQueryService accountQueryService,
      CryptoService cryptoService,
      ApplicationEventPublisher eventPublisher) {
    this.cardProfileRepository = cardProfileRepository;
    this.accountRepository = accountRepository;
    this.accountQueryService = accountQueryService;
    this.cryptoService = cryptoService;
    this.eventPublisher = eventPublisher;
  }

  public CardProfileView update(UUID userId, UUID accountId, CardProfileRequest request) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    if (account.getType() != com.deltafinance.accounts.domain.AccountType.CREDIT) {
      throw new ValidationException("accountId", "A conta informada não é um cartão de crédito.");
    }
    int closingDay = validateDay(request.closingDay(), "closingDay");
    int dueDay = validateDay(request.dueDay(), "dueDay");

    CardProfile profile =
        cardProfileRepository
            .findByAccountIdAndUserId(accountId, userId)
            .orElseGet(
                () ->
                    new CardProfile(userId, accountId, closingDay, dueDay, CardProfileSource.USER));
    profile.updateCycle(closingDay, dueDay);

    if (request.creditLimit() != null && !request.creditLimit().isBlank()) {
      BigDecimal limit = parseLimit(request.creditLimit());
      profile.updateCreditLimit(
          cryptoService.encrypt(
              limit.toPlainString(),
              new CryptoContext(userId, TABLE, LIMIT_COLUMN, profile.getId())));
    }
    if (profile.getCreditBalanceEnc() == null) {
      profile.updateCreditBalance(
          cryptoService.encrypt(
              "0.00", new CryptoContext(userId, TABLE, BALANCE_COLUMN, profile.getId())));
    }
    profile.updatePaymentAccountId(
        validatePaymentAccount(userId, account, request.paymentAccountId()));
    cardProfileRepository.save(profile);
    eventPublisher.publishEvent(new CardProfileUpdatedEvent(userId, accountId));
    return accountQueryService.get(userId, accountId).cardProfile();
  }

  /**
   * Valida a conta padrão de pagamento: do mesmo usuário, ativa, não-cartão e na moeda do cartão.
   *
   * <p>É uma preferência de desempate, não uma regra financeira: a conta escolhida nunca decide
   * sozinha a classificação de um pagamento importado.
   */
  private UUID validatePaymentAccount(UUID userId, FinancialAccount card, UUID paymentAccountId) {
    if (paymentAccountId == null) {
      return null;
    }
    FinancialAccount paymentAccount =
        accountRepository
            .findByIdAndUserId(paymentAccountId, userId)
            .orElseThrow(
                () ->
                    new ValidationException(
                        "paymentAccountId", "A conta de pagamento informada não existe."));
    if (paymentAccount.getType() == com.deltafinance.accounts.domain.AccountType.CREDIT) {
      throw new ValidationException(
          "paymentAccountId", "A conta de pagamento não pode ser um cartão de crédito.");
    }
    if (paymentAccount.getStatus() != com.deltafinance.accounts.domain.AccountStatus.ACTIVE) {
      throw new ValidationException("paymentAccountId", "A conta de pagamento deve estar ativa.");
    }
    if (!paymentAccount.getCurrency().equalsIgnoreCase(card.getCurrency())) {
      throw new ValidationException(
          "paymentAccountId", "A conta de pagamento deve ter a mesma moeda do cartão.");
    }
    return paymentAccount.getId();
  }

  private static int validateDay(Integer day, String field) {
    if (day == null || day < 1 || day > 31) {
      throw new ValidationException(field, "O dia deve estar entre 1 e 31.");
    }
    return day;
  }

  private static BigDecimal parseLimit(String value) {
    try {
      BigDecimal limit = Money.scaled(new BigDecimal(value.trim()));
      if (limit.signum() < 0) {
        throw new ValidationException("creditLimit", "O limite não pode ser negativo.");
      }
      return limit;
    } catch (NumberFormatException exception) {
      throw new ValidationException("creditLimit", "Limite inválido.");
    }
  }
}
