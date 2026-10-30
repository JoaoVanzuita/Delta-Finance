package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.AccountOrigin;
import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.accounts.domain.CardProfileRepository;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Consultas de contas/cartões com valores decifrados e escopo de usuário. */
@Service
public class AccountQueryService {

  private final FinancialAccountRepository accountRepository;
  private final CardProfileRepository cardProfileRepository;
  private final ConnectionStatusPort connectionStatusPort;
  private final AccountBalanceService balanceService;
  private final CryptoService cryptoService;

  public AccountQueryService(
      FinancialAccountRepository accountRepository,
      CardProfileRepository cardProfileRepository,
      ConnectionStatusPort connectionStatusPort,
      AccountBalanceService balanceService,
      CryptoService cryptoService) {
    this.accountRepository = accountRepository;
    this.cardProfileRepository = cardProfileRepository;
    this.connectionStatusPort = connectionStatusPort;
    this.balanceService = balanceService;
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

  /**
   * Mapa {@code accountId -> connectionId} das contas importadas do usuário.
   *
   * <p>Usado por relatórios para descobrir quais conexões são relevantes ao filtro sem expor a
   * entidade ou o repositório de contas a outro módulo.
   */
  public Map<UUID, UUID> connectionIdsByAccount(UUID userId) {
    Map<UUID, UUID> result = new HashMap<>();
    for (FinancialAccount account : accountRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
      if (account.getConnectionId() != null) {
        result.put(account.getId(), account.getConnectionId());
      }
    }
    return result;
  }

  private AccountView toView(FinancialAccount account) {
    CardProfileView cardView =
        account.getType() == com.deltafinance.accounts.domain.AccountType.CREDIT
            ? cardProfileRepository
                .findByAccountIdAndUserId(account.getId(), account.getUserId())
                .map(this::toCardView)
                .orElse(null)
            : null;
    String referenceBalance = decrypt(account, account.getBalanceEnc(), "balance");
    String providerAlias = decrypt(account, account.getAliasEnc(), "alias");
    String customName = decrypt(account, account.getDisplayNameEnc(), "display_name");
    boolean nameCustomized =
        account.getOrigin() == AccountOrigin.IMPORTED
            && customName != null
            && !customName.isBlank();
    String displayName = nameCustomized ? customName : providerAlias;
    String providerName = account.getOrigin() == AccountOrigin.IMPORTED ? providerAlias : null;

    return new AccountView(
        account.getId(),
        account.getType().name(),
        account.getSubtype(),
        account.getOrigin().name(),
        decrypt(account, account.getInstitutionNameEnc(), "institution_name"),
        displayName,
        providerName,
        nameCustomized,
        account.getCurrency(),
        balanceService.effectiveBalance(account, referenceBalance),
        referenceBalance,
        account.getBalanceReferenceAt(),
        account.getStatus().name(),
        account.getLastSyncAt(),
        decrypt(account, account.getNotesEnc(), "notes"),
        cardView,
        connectionStatus(account));
  }

  private String connectionStatus(FinancialAccount account) {
    if (account.getConnectionId() == null) {
      return null;
    }
    return connectionStatusPort.statusOf(account.getUserId(), account.getConnectionId());
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
            profile.getCreditBalanceEnc()),
        profile.getBrand(),
        profile.getLevel(),
        decrypt(
            profile.getUserId(), "card_profile", "last4", profile.getId(), profile.getLast4Enc()),
        decrypt(
            profile.getUserId(),
            "card_profile",
            "available_credit_limit",
            profile.getId(),
            profile.getAvailableCreditLimitEnc()));
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
