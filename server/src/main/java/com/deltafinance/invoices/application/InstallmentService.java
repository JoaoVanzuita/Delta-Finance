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
    if (installmentNumber == null || totalInstallments == null) {
      throw new ValidationException("installments", "Informe número e total de parcelas juntos.");
    }
    if (installmentNumber < 1 || totalInstallments < 1) {
      throw new ValidationException("installments", "As parcelas devem ser positivas.");
    }
    if (installmentNumber > totalInstallments) {
      throw new ValidationException(
          "installmentNumber", "O número da parcela não pode exceder o total.");
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
