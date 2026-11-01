package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.AccountOrigin;
import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca a conta usada como fronteira de caixa do perímetro de investimento.
 *
 * <p>A marcação é o que separa aporte de aplicação: créditos que entram nessa conta são aportes;
 * débitos de corretagem são aplicações; transferências entre contas marcadas são movimentos
 * internos. Sem a marca, o relatório não consegue distinguir uma conta de passagem de uma conta de
 * investimento.
 */
@Service
public class AccountInvestmentService {

  private static final String TABLE = "financial_account";

  private final FinancialAccountRepository accountRepository;
  private final AccountQueryService accountQueryService;
  private final AuditService auditService;

  public AccountInvestmentService(
      FinancialAccountRepository accountRepository,
      AccountQueryService accountQueryService,
      AuditService auditService) {
    this.accountRepository = accountRepository;
    this.accountQueryService = accountQueryService;
    this.auditService = auditService;
  }

  @Transactional
  public AccountView update(UUID userId, UUID accountId, boolean investmentAccount) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    if (account.getType() == AccountType.CREDIT) {
      throw new ConflictException(
          "ACCOUNT_IS_CARD", "Cartões não podem ser marcados como conta de investimento.");
    }
    if (account.getOrigin() != AccountOrigin.IMPORTED) {
      throw new ConflictException(
          "ACCOUNT_NOT_IMPORTED",
          "Apenas contas importadas podem ser marcadas como conta de investimento.");
    }
    account.updateInvestmentAccount(investmentAccount);
    accountRepository.save(account);
    auditService.record(
        userId,
        AuditEventType.ACCOUNT_INVESTMENT_FLAG_UPDATED,
        TABLE,
        account.getId(),
        Map.of("investmentAccount", investmentAccount));
    return accountQueryService.get(userId, accountId);
  }
}
