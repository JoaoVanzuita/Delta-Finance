package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.accounts.domain.CardProfileRepository;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Consultas de contas/cartões com valores decifrados e escopo de usuário. */
@Service
public class AccountQueryService {

  private final FinancialAccountRepository accountRepository;
  private final CardProfileRepository cardProfileRepository;
  private final CryptoService cryptoService;

  public AccountQueryService(
      FinancialAccountRepository accountRepository,
      CardProfileRepository cardProfileRepository,
      CryptoService cryptoService) {
    this.accountRepository = accountRepository;
    this.cardProfileRepository = cardProfileRepository;
    this.cryptoService = cryptoService;
  }

  public List<AccountView> list(UUID userId) {
    return accountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
        .map(this::toView)
        .toList();
  }

  public AccountView get(UUID userId, UUID accountId) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    return toView(account);
  }

  public long countByConnection(UUID connectionId) {
    return accountRepository.findByConnectionIdOrderByCreatedAtAsc(connectionId).size();
  }

  private AccountView toView(FinancialAccount account) {
    CardProfileView cardView =
        account.getType() == com.deltafinance.accounts.domain.AccountType.CREDIT
            ? cardProfileRepository
                .findByAccountIdAndUserId(account.getId(), account.getUserId())
                .map(this::toCardView)
                .orElse(null)
            : null;

    return new AccountView(
        account.getId(),
        account.getType().name(),
        account.getSubtype(),
        account.getOrigin().name(),
        decrypt(account, account.getInstitutionNameEnc(), "institution_name"),
        decrypt(account, account.getAliasEnc(), "alias"),
        account.getCurrency(),
        decrypt(account, account.getBalanceEnc(), "balance"),
        account.getBalanceReferenceAt(),
        account.getStatus().name(),
        account.getLastSyncAt(),
        cardView);
  }

  private CardProfileView toCardView(CardProfile profile) {
    return new CardProfileView(
        profile.getAccountId(),
        profile.getClosingDay(),
        profile.getDueDay(),
        decrypt(
            profile.getUserId(),
            "card_profile",
            "credit_limit",
            profile.getId(),
            profile.getCreditLimitEnc()),
        decrypt(
            profile.getUserId(),
            "card_profile",
            "credit_balance",
            profile.getId(),
            profile.getCreditBalanceEnc()));
  }

  private String decrypt(FinancialAccount account, byte[] ciphertext, String column) {
    return decrypt(account.getUserId(), "financial_account", column, account.getId(), ciphertext);
  }

  private String decrypt(
      UUID userId, String table, String column, UUID recordId, byte[] ciphertext) {
    if (ciphertext == null) {
      return null;
    }
    try {
      return cryptoService.decryptString(
          ciphertext, new CryptoContext(userId, table, column, recordId));
    } catch (CryptoException exception) {
      return null;
    }
  }
}
