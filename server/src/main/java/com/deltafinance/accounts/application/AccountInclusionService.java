package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.NotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inclusão explícita da conta no saldo consolidado.
 *
 * <p>A regra de elegibilidade continua sendo de natureza da conta (dinheiro, ativa, em BRL), mas o
 * usuário pode excluir uma conta do consolidado — por exemplo, uma conta de passagem ou uma reserva
 * que não quer somar. A exclusão afeta apenas dashboard, patrimônio e resumo de contas; relatórios
 * por conta, filtros e histórico permanecem inalterados.
 */
@Service
public class AccountInclusionService {

  private static final String TABLE = "financial_account";

  private final FinancialAccountRepository accountRepository;
  private final AccountQueryService accountQueryService;
  private final AuditService auditService;

  public AccountInclusionService(
      FinancialAccountRepository accountRepository,
      AccountQueryService accountQueryService,
      AuditService auditService) {
    this.accountRepository = accountRepository;
    this.accountQueryService = accountQueryService;
    this.auditService = auditService;
  }

  @Transactional
  public AccountView update(UUID userId, UUID accountId, boolean includeInTotal) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    account.updateIncludeInTotal(includeInTotal);
    accountRepository.save(account);
    auditService.record(
        userId,
        AuditEventType.ACCOUNT_INCLUSION_UPDATED,
        TABLE,
        account.getId(),
        Map.of("includeInTotal", includeInTotal));
    return accountQueryService.get(userId, accountId);
  }
}
