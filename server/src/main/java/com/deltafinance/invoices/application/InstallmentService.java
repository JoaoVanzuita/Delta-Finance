package com.deltafinance.invoices.application;

import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.ValidationException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Validação e agrupamento heurístico de parcelas de um mesmo plano. */
@Service
public class InstallmentService {

  private final IndexHmacService hmacService;

  public InstallmentService(IndexHmacService hmacService) {
    this.hmacService = hmacService;
  }

  public void validate(Integer installmentNumber, Integer totalInstallments) {
    if (installmentNumber == null && totalInstallments == null) {
      return;
    }
    if (totalInstallments == null || totalInstallments < 1) {
      throw new ValidationException("installments", "Informe um total de parcelas positivo.");
    }
    if (totalInstallments > 120) {
      throw new ValidationException("installments", "O total de parcelas deve ser no máximo 120.");
    }
    if (installmentNumber != null
        && (installmentNumber < 1 || installmentNumber > totalInstallments)) {
      throw new ValidationException(
          "installmentNumber", "O número da parcela deve estar entre 1 e o total.");
    }
  }

  /** Chave estável para agrupar parcelas do mesmo plano sem depender do ID externo. */
  public byte[] planKey(UUID userId, String description, Integer totalInstallments) {
    if (totalInstallments == null || totalInstallments < 2) {
      return null;
    }
    String value =
        userId
            + "|"
            + (description == null ? "" : description.strip().toLowerCase(java.util.Locale.ROOT))
            + "|"
            + totalInstallments;
    return hmacService.hmac("installment-plan", value);
  }
}
