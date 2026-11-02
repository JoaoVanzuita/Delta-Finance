package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.AccountOrigin;
import com.deltafinance.accounts.domain.CardProfile;
import com.deltafinance.accounts.domain.CardProfileRepository;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.accounts.domain.ReservedBalance;
import com.deltafinance.accounts.domain.ReservedBalanceRepository;
import com.deltafinance.accounts.domain.ReservedBalanceTreatment;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
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
  private final ReservedBalanceRepository reservedBalanceRepository;
  private final ConnectionStatusPort connectionStatusPort;
  private final ConnectionNamePort connectionNamePort;
  private final ConnectionReservedBalanceTreatmentPort connectionReservedBalanceTreatmentPort;
  private final AccountBalanceService balanceService;
  private final EncryptedValueReader encryptedValueReader;

  public AccountQueryService(
      FinancialAccountRepository accountRepository,
      CardProfileRepository cardProfileRepository,
      ReservedBalanceRepository reservedBalanceRepository,
      ConnectionStatusPort connectionStatusPort,
      ConnectionNamePort connectionNamePort,
      ConnectionReservedBalanceTreatmentPort connectionReservedBalanceTreatmentPort,
      AccountBalanceService balanceService,
      EncryptedValueReader encryptedValueReader) {
    this.accountRepository = accountRepository;
    this.cardProfileRepository = cardProfileRepository;
    this.reservedBalanceRepository = reservedBalanceRepository;
    this.connectionStatusPort = connectionStatusPort;
    this.connectionNamePort = connectionNamePort;
    this.connectionReservedBalanceTreatmentPort = connectionReservedBalanceTreatmentPort;
    this.balanceService = balanceService;
    this.encryptedValueReader = encryptedValueReader;
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
    ReservedBalanceTreatment treatment = effectiveReservedBalanceTreatment(account);

    return new AccountView(
        account.getId(),
        account.getType().name(),
        account.getSubtype(),
        account.getOrigin().name(),
        institutionName(
            account, decrypt(account, account.getInstitutionNameEnc(), "institution_name")),
        displayName,
        providerName,
        nameCustomized,
        account.isInvestmentAccount(),
        account.hasReservedBalance(),
        reservedBalanceTotal(account),
        treatment.name(),
        reservedBalanceTreatmentSource(account, treatment),
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

  private ReservedBalanceTreatment effectiveReservedBalanceTreatment(FinancialAccount account) {
    if (account.getReservedBalanceTreatment() != ReservedBalanceTreatment.UNKNOWN) {
      return account.getReservedBalanceTreatment();
    }
    if (!account.hasReservedBalance()) {
      return ReservedBalanceTreatment.UNKNOWN;
    }
    ReservedBalanceTreatment connectionTreatment =
        connectionReservedBalanceTreatmentPort.treatmentOf(
            account.getUserId(), account.getConnectionId());
    return connectionTreatment == null ? ReservedBalanceTreatment.UNKNOWN : connectionTreatment;
  }

  private String reservedBalanceTreatmentSource(
      FinancialAccount account, ReservedBalanceTreatment effective) {
    if (account.getReservedBalanceTreatment() != ReservedBalanceTreatment.UNKNOWN) {
      return "ACCOUNT";
    }
    return effective == ReservedBalanceTreatment.UNKNOWN ? "UNKNOWN" : "CONNECTION";
  }

  private String reservedBalanceTotal(FinancialAccount account) {
    BigDecimal total = BigDecimal.ZERO;
    for (ReservedBalance reserved :
        reservedBalanceRepository.findByUserIdAndAccountIdAndRemovedAtIsNullOrderByCreatedAtAsc(
            account.getUserId(), account.getId())) {
      if (!account.getCurrency().equalsIgnoreCase(reserved.getCurrency())) {
        continue;
      }
      String value =
          decrypt(
              account.getUserId(),
              "reserved_balance",
              "amount",
              reserved.getId(),
              reserved.getAmountEnc());
      if (value != null) {
        total = total.add(new BigDecimal(value));
      }
    }
    return new Money(total, account.getCurrency()).toPlainString();
  }

  private String connectionStatus(FinancialAccount account) {
    if (account.getConnectionId() == null) {
      return null;
    }
    return connectionStatusPort.statusOf(account.getUserId(), account.getConnectionId());
  }

  /**
   * Nome de instituição para exibição.
   *
   * <p>Contas importadas preferem o nome de exibição da conexão. É o que corrige o agrupamento no
   * MeuPluggy, onde o item se chama "MeuPluggy" e o banco real está no nome da conexão. Contas
   * manuais ou conexões sem nome mantêm o valor gravado na conta.
   */
  private String institutionName(FinancialAccount account, String storedName) {
    if (account.getConnectionId() == null) {
      return storedName;
    }
    String connectionName =
        connectionNamePort.displayNameOf(account.getUserId(), account.getConnectionId());
    if (connectionName != null && !connectionName.isBlank()) {
      return connectionName;
    }
    return storedName;
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
    // Campos de exibição degradam para nulo se um ciphertext antigo estiver ilegível; a consulta
    // continua útil e o valor ausente aparece como "Não informado" na UI.
    return encryptedValueReader.tryRead(userId, table, column, recordId, ciphertext).orElse(null);
  }
}
