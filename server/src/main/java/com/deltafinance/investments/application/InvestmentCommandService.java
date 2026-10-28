package com.deltafinance.investments.application;

import com.deltafinance.investments.domain.ProviderInvestment;
import com.deltafinance.investments.domain.ProviderInvestmentRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Ajustes informados pelo usuário em uma posição importada. */
@Service
public class InvestmentCommandService {

  private static final String TABLE = "provider_investment";
  private static final Set<String> ASSET_CLASSES =
      Set.of(
          "FII",
          "ACAO",
          "BDR",
          "DERIVATIVO",
          "RENDA_FIXA",
          "FUNDO",
          "ETF",
          "TITULO",
          "COE",
          "OUTRO");

  private final ProviderInvestmentRepository investmentRepository;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final Clock clock;

  public InvestmentCommandService(
      ProviderInvestmentRepository investmentRepository,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.investmentRepository = investmentRepository;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  /** Atualiza ou limpa o custo informado; o valor do provedor não é alterado. */
  public void updateCostBasis(UUID userId, UUID investmentId, String costBasis) {
    ProviderInvestment investment = require(userId, investmentId);
    byte[] encrypted = null;
    if (costBasis != null && !costBasis.isBlank()) {
      BigDecimal parsed = parseCostBasis(costBasis);
      encrypted =
          cryptoService.encrypt(
              parsed.toPlainString(),
              new CryptoContext(userId, TABLE, "manual_cost_basis", investment.getId()));
    }
    investment.updateManualCostBasis(encrypted, clock.now());
    investmentRepository.save(investment);
    auditService.record(
        userId,
        AuditEventType.INVESTMENT_COST_BASIS_UPDATED,
        TABLE,
        investmentId,
        Map.of("cleared", encrypted == null));
  }

  /** Atualiza ou limpa a classificação informada pelo usuário. */
  public void updateAssetClass(UUID userId, UUID investmentId, String assetClass) {
    ProviderInvestment investment = require(userId, investmentId);
    String normalized = null;
    if (assetClass != null && !assetClass.isBlank()) {
      normalized = assetClass.trim().toUpperCase(Locale.ROOT);
      if (!ASSET_CLASSES.contains(normalized)) {
        throw new ValidationException("assetClass", "Classe de investimento inválida.");
      }
    }
    investment.updateUserAssetClass(normalized);
    investmentRepository.save(investment);
    auditService.record(
        userId,
        AuditEventType.INVESTMENT_ASSET_CLASS_UPDATED,
        TABLE,
        investmentId,
        Map.of("cleared", normalized == null));
  }

  private ProviderInvestment require(UUID userId, UUID investmentId) {
    return investmentRepository
        .findByIdAndUserId(investmentId, userId)
        .orElseThrow(NotFoundException::new);
  }

  private static BigDecimal parseCostBasis(String value) {
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(value.trim());
    } catch (NumberFormatException exception) {
      throw new ValidationException("costBasis", "Custo informado inválido.");
    }
    if (parsed.signum() < 0) {
      throw new ValidationException("costBasis", "O custo informado não pode ser negativo.");
    }
    return parsed.setScale(2, RoundingMode.HALF_EVEN);
  }
}
