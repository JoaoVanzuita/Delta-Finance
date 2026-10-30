package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.AccountOrigin;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Nome de exibição local de contas e cartões importados.
 *
 * <p>O nome personalizado é gravado em coluna própria e nunca substitui o nome recebido do Pluggy.
 * Contas manuais continuam usando o apelido editável pelo formulário existente.
 */
@Service
public class AccountDisplayNameService {

  private static final String TABLE = "financial_account";
  private static final String COLUMN = "display_name";
  private static final int MAX_LENGTH = 120;

  private final FinancialAccountRepository accountRepository;
  private final AccountQueryService accountQueryService;
  private final CryptoService cryptoService;
  private final AuditService auditService;

  public AccountDisplayNameService(
      FinancialAccountRepository accountRepository,
      AccountQueryService accountQueryService,
      CryptoService cryptoService,
      AuditService auditService) {
    this.accountRepository = accountRepository;
    this.accountQueryService = accountQueryService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
  }

  public AccountView rename(UUID userId, UUID accountId, String displayName) {
    FinancialAccount account = requireImported(userId, accountId);
    String normalized = requireDisplayName(displayName);
    account.renameDisplayName(
        cryptoService.encrypt(
            normalized, new CryptoContext(userId, TABLE, COLUMN, account.getId())));
    accountRepository.save(account);
    auditService.record(
        userId,
        AuditEventType.ACCOUNT_RENAMED,
        TABLE,
        account.getId(),
        Map.of("nameCustomized", true));
    return accountQueryService.get(userId, accountId);
  }

  public AccountView reset(UUID userId, UUID accountId) {
    FinancialAccount account = requireImported(userId, accountId);
    account.clearDisplayName();
    accountRepository.save(account);
    auditService.record(
        userId,
        AuditEventType.ACCOUNT_NAME_RESET,
        TABLE,
        account.getId(),
        Map.of("nameCustomized", false));
    return accountQueryService.get(userId, accountId);
  }

  private FinancialAccount requireImported(UUID userId, UUID accountId) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    if (account.getOrigin() != AccountOrigin.IMPORTED) {
      throw new ConflictException(
          "ACCOUNT_NOT_IMPORTED",
          "Contas manuais são renomeadas pelo formulário de edição da conta.");
    }
    return account;
  }

  private static String requireDisplayName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      throw new ValidationException("displayName", "Informe o nome de exibição.");
    }
    String trimmed = displayName.strip();
    if (trimmed.length() > MAX_LENGTH) {
      throw new ValidationException(
          "displayName", "O nome de exibição deve ter no máximo " + MAX_LENGTH + " caracteres.");
    }
    if (trimmed.chars().anyMatch(Character::isISOControl)) {
      throw new ValidationException(
          "displayName", "O nome de exibição contém caracteres inválidos.");
    }
    return trimmed;
  }
}
