package com.deltafinance.accounts.application;

import com.deltafinance.accounts.api.AccountUpdateRequest;
import com.deltafinance.accounts.api.ManualAccountRequest;
import com.deltafinance.accounts.domain.AccountOrigin;
import com.deltafinance.accounts.domain.AccountStatus;
import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Criação e edição de contas manuais.
 *
 * <p>O tipo é imutável após a criação. Conta desativada não recebe novos lançamentos, mas preserva
 * histórico e relatórios. Saldo/data são a base do saldo efetivo calculado por {@link
 * AccountBalanceService}.
 */
@Service
public class ManualAccountService {

  private static final String TABLE = "financial_account";

  private final FinancialAccountRepository accountRepository;
  private final AccountQueryService accountQueryService;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final Clock clock;

  public ManualAccountService(
      FinancialAccountRepository accountRepository,
      AccountQueryService accountQueryService,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.accountQueryService = accountQueryService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  public AccountView create(UUID userId, ManualAccountRequest request) {
    AccountType type = parseType(request.type());
    String alias = requireAlias(request.alias());
    String currency = normalizeCurrency(request.currency());
    Money balance = parseMoney(request.referenceBalance(), currency);
    Instant referenceDate = request.referenceDate() == null ? clock.now() : request.referenceDate();

    FinancialAccount account = FinancialAccount.manual(userId, type, currency);
    account.applyManual(
        encrypt(userId, account.getId(), "alias", alias),
        encrypt(userId, account.getId(), "balance", balance.toPlainString()),
        referenceDate,
        AccountStatus.ACTIVE,
        encryptOptional(userId, account.getId(), "notes", request.notes()));
    accountRepository.save(account);
    auditService.record(
        userId, AuditEventType.MANUAL_ACCOUNT_CREATED, "financial_account", account.getId());
    return accountQueryService.get(userId, account.getId());
  }

  public AccountView update(UUID userId, UUID accountId, AccountUpdateRequest request) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    if (account.getOrigin() != AccountOrigin.MANUAL) {
      throw new ConflictException(
          "ACCOUNT_NOT_MANUAL", "Somente contas manuais podem ser editadas.");
    }

    String alias = request.alias() == null ? null : requireAlias(request.alias());
    AccountStatus status =
        request.status() == null ? account.getStatus() : parseStatus(request.status());
    Money balance =
        request.referenceBalance() == null
            ? null
            : parseMoney(request.referenceBalance(), account.getCurrency());
    Instant referenceDate =
        request.referenceDate() == null ? account.getBalanceReferenceAt() : request.referenceDate();

    byte[] aliasEnc =
        alias == null ? account.getAliasEnc() : encrypt(userId, account.getId(), "alias", alias);
    byte[] balanceEnc =
        balance == null
            ? account.getBalanceEnc()
            : encrypt(userId, account.getId(), "balance", balance.toPlainString());

    byte[] notesEnc =
        request.notes() == null
            ? account.getNotesEnc()
            : encryptOptional(userId, account.getId(), "notes", request.notes());
    account.applyManual(aliasEnc, balanceEnc, referenceDate, status, notesEnc);
    accountRepository.save(account);
    auditService.record(
        userId,
        AuditEventType.MANUAL_ACCOUNT_UPDATED,
        "financial_account",
        account.getId(),
        Map.of("active", status == AccountStatus.ACTIVE));
    return accountQueryService.get(userId, accountId);
  }

  private static AccountType parseType(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("type", "Informe o tipo da conta manual.");
    }
    try {
      AccountType type = AccountType.valueOf(value.trim().toUpperCase(Locale.ROOT));
      if (type == AccountType.CREDIT || type == AccountType.UNKNOWN) {
        throw new ValidationException(
            "type",
            type == AccountType.CREDIT
                ? "Cartões de crédito não podem ser criados como conta manual."
                : "O tipo da conta manual deve ser informado.");
      }
      return type;
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("type", "Tipo de conta manual inválido.");
    }
  }

  private static AccountStatus parseStatus(String value) {
    try {
      AccountStatus status = AccountStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
      if (status != AccountStatus.ACTIVE && status != AccountStatus.INACTIVE) {
        throw new ValidationException("status", "Use ACTIVE ou INACTIVE para conta manual.");
      }
      return status;
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("status", "Estado de conta manual inválido.");
    }
  }

  private static String requireAlias(String alias) {
    if (alias == null || alias.isBlank()) {
      throw new ValidationException("alias", "Informe o apelido da conta.");
    }
    String trimmed = alias.strip();
    if (trimmed.length() > 120) {
      throw new ValidationException("alias", "O apelido deve ter no máximo 120 caracteres.");
    }
    return trimmed;
  }

  private static Money parseMoney(String amount, String currency) {
    if (amount == null || amount.isBlank()) {
      throw new ValidationException("referenceBalance", "Informe o saldo de referência.");
    }
    try {
      BigDecimal value = new BigDecimal(amount.trim());
      return new Money(value, currency);
    } catch (NumberFormatException exception) {
      throw new ValidationException("referenceBalance", "Saldo de referência inválido.");
    }
  }

  private static String normalizeCurrency(String currency) {
    if (currency == null || currency.isBlank()) {
      return Money.BRL;
    }
    String normalized = currency.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() != 3) {
      throw new ValidationException("currency", "Use o código ISO de 3 letras.");
    }
    return normalized;
  }

  private byte[] encrypt(UUID userId, UUID accountId, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, TABLE, column, accountId));
  }

  private byte[] encryptOptional(UUID userId, UUID accountId, String column, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return encrypt(userId, accountId, column, value.strip());
  }
}
