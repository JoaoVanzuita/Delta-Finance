package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.AccountOrigin;
import com.deltafinance.accounts.domain.AccountType;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.accounts.domain.ReservedBalanceTreatment;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Define como o saldo reservado de uma conta entra no patrimônio.
 *
 * <p>O Mercado Pago retorna o saldo reservado fora do {@code balance}; nesse caso o tratamento é
 * {@code SEPARATE_ASSET}. Outros conectores podem incluir a reserva no saldo e usar {@code
 * INCLUDED_IN_BALANCE}. Enquanto não validado, {@code UNKNOWN} não soma nada.
 */
@Service
public class ReservedBalanceTreatmentService {

  private static final String TABLE = "financial_account";

  private final FinancialAccountRepository accountRepository;
  private final AccountQueryService accountQueryService;
  private final AuditService auditService;

  public ReservedBalanceTreatmentService(
      FinancialAccountRepository accountRepository,
      AccountQueryService accountQueryService,
      AuditService auditService) {
    this.accountRepository = accountRepository;
    this.accountQueryService = accountQueryService;
    this.auditService = auditService;
  }

  @Transactional
  public AccountView update(UUID userId, UUID accountId, String treatmentValue) {
    FinancialAccount account =
        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(NotFoundException::new);
    if (account.getOrigin() != AccountOrigin.IMPORTED) {
      throw new ConflictException(
          "ACCOUNT_NOT_IMPORTED",
          "Saldos reservados são importados do provedor; contas manuais não têm essa marcação.");
    }
    if (account.getType() == AccountType.CREDIT) {
      throw new ConflictException(
          "ACCOUNT_IS_CARD", "Cartões não possuem saldo reservado bancário.");
    }
    ReservedBalanceTreatment treatment = parse(treatmentValue);
    account.updateReservedBalanceTreatment(treatment);
    accountRepository.save(account);
    auditService.record(
        userId,
        AuditEventType.RESERVED_BALANCE_TREATMENT_UPDATED,
        TABLE,
        account.getId(),
        Map.of("treatment", treatment.name()));
    return accountQueryService.get(userId, accountId);
  }

  private static ReservedBalanceTreatment parse(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("treatment", "Informe o tratamento do saldo reservado.");
    }
    try {
      return ReservedBalanceTreatment.valueOf(value.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(
          "treatment", "Tratamento inválido. Use UNKNOWN, INCLUDED_IN_BALANCE ou SEPARATE_ASSET.");
    }
  }
}
