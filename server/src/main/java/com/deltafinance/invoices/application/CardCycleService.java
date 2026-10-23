package com.deltafinance.invoices.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.accounts.domain.CardProfileRepository;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Cálculo do ciclo local do cartão.
 *
 * <p>O fechamento ocorre no dia configurado; o vencimento é no dia configurado do mês seguinte. Sem
 * {@code card_profile}, o cartão não possui ciclo e as transações ficam não alocadas.
 */
@Service
public class CardCycleService {

  public record CardCycle(
      LocalDate cycleStart, LocalDate cycleEnd, LocalDate closingDate, LocalDate dueDate) {}

  private final CardProfileRepository cardProfileRepository;
  private final FinancialAccountRepository accountRepository;
  private final Clock clock;

  public CardCycleService(
      CardProfileRepository cardProfileRepository,
      FinancialAccountRepository accountRepository,
      Clock clock) {
    this.cardProfileRepository = cardProfileRepository;
    this.accountRepository = accountRepository;
    this.clock = clock;
  }

  public Optional<CardProfile> profileFor(UUID userId, UUID cardAccountId) {
    return cardProfileRepository.findByAccountIdAndUserId(cardAccountId, userId);
  }

  public Optional<CardCycle> cycleFor(UUID userId, UUID cardAccountId, LocalDate date) {
    return profileFor(userId, cardAccountId).map(profile -> cycleFor(profile, date));
  }

  public CardCycle cycleFor(CardProfile profile, LocalDate date) {
    LocalDate closingThisMonth = closingDate(date.getYear(), date.getMonthValue(), profile);
    LocalDate start;
    LocalDate end;
    if (!date.isAfter(closingThisMonth)) {
      end = closingThisMonth;
      YearMonth previous = YearMonth.from(closingThisMonth).minusMonths(1);
      start = closingDate(previous.getYear(), previous.getMonthValue(), profile).plusDays(1);
    } else {
      start = closingThisMonth.plusDays(1);
      YearMonth next = YearMonth.from(closingThisMonth).plusMonths(1);
      end = closingDate(next.getYear(), next.getMonthValue(), profile);
    }
    LocalDate due = dueDate(end.plusMonths(1), profile);
    return new CardCycle(start, end, end, due);
  }

  public CardCycle nextCycle(CardProfile profile, CardCycle current) {
    return cycleFor(profile, current.cycleEnd().plusDays(1));
  }

  /** Cartões sem ciclo configurado ainda podem ser consultados; não geram faturas. */
  public boolean hasCycle(UUID userId, UUID cardAccountId) {
    return profileFor(userId, cardAccountId).isPresent();
  }

  public FinancialAccount requireCard(UUID userId, UUID cardAccountId) {
    FinancialAccount account =
        accountRepository
            .findByIdAndUserId(cardAccountId, userId)
            .orElseThrow(NotFoundException::new);
    if (account.getType() != com.deltafinance.accounts.domain.AccountType.CREDIT) {
      throw new com.deltafinance.shared.errors.ValidationException(
          "accountId", "A conta informada não é um cartão de crédito.");
    }
    return account;
  }

  public LocalDate today() {
    return clock.today();
  }

  private static LocalDate closingDate(int year, int month, CardProfile profile) {
    YearMonth yearMonth = YearMonth.of(year, month);
    int day = Math.min(profile.getClosingDay(), yearMonth.lengthOfMonth());
    return yearMonth.atDay(day);
  }

  private static LocalDate dueDate(LocalDate monthReference, CardProfile profile) {
    YearMonth yearMonth = YearMonth.from(monthReference);
    int day = Math.min(profile.getDueDay(), yearMonth.lengthOfMonth());
    return yearMonth.atDay(day);
  }
}
