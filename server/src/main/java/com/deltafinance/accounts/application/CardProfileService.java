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
import java.math.RoundingMode;
import java.util.UUID;
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

  public CardProfileService(
      CardProfileRepository cardProfileRepository,
      FinancialAccountRepository accountRepository,
      AccountQueryService accountQueryService,
      CryptoService cryptoService) {
    this.cardProfileRepository = cardProfileRepository;
    this.accountRepository = accountRepository;
    this.accountQueryService = accountQueryService;
    this.cryptoService = cryptoService;
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
    cardProfileRepository.save(profile);
    return accountQueryService.get(userId, accountId).cardProfile();
  }

  private static int validateDay(Integer day, String field) {
    if (day == null || day < 1 || day > 31) {
      throw new ValidationException(field, "O dia deve estar entre 1 e 31.");
    }
    return day;
  }

  private static BigDecimal parseLimit(String value) {
    try {
      BigDecimal limit = new BigDecimal(value.trim()).setScale(Money.SCALE, RoundingMode.HALF_EVEN);
      if (limit.signum() < 0) {
        throw new ValidationException("creditLimit", "O limite não pode ser negativo.");
      }
      return limit;
    } catch (NumberFormatException exception) {
      throw new ValidationException("creditLimit", "Limite inválido.");
    }
  }
}
