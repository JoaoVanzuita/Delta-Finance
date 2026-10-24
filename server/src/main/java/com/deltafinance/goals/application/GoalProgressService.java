package com.deltafinance.goals.application;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.accounts.application.AccountView;
import com.deltafinance.goals.domain.AllocationMode;
import com.deltafinance.goals.domain.AllocationSourceType;
import com.deltafinance.goals.domain.FinancialGoal;
import com.deltafinance.goals.domain.FinancialGoalRepository;
import com.deltafinance.goals.domain.GoalAllocation;
import com.deltafinance.goals.domain.GoalAllocationRepository;
import com.deltafinance.goals.domain.GoalProgressRule;
import com.deltafinance.goals.domain.GoalStatus;
import com.deltafinance.investments.application.InvestmentPositionService;
import com.deltafinance.investments.application.InvestmentPositionView;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.wealth.application.ManualItemView;
import com.deltafinance.wealth.application.WealthService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Cálculo de progresso, resíduo, saldo não alocado e capacidade das fontes.
 *
 * <p>Regras:
 *
 * <ul>
 *   <li>{@code FIXED_AMOUNT} é exato; {@code PERCENTAGE} usa {@code floor(valor-alvo × percentual ÷
 *       100, 2)} e o resíduo fica na última alocação percentual;
 *   <li>o excesso é permitido, mas o percentual é limitado a 100;
 *   <li>percentuais abaixo de 100% deixam saldo não alocado visível e não redistribuído;
 *   <li>a soma das alocações de uma mesma fonte entre metas não pode exceder o saldo da fonte;
 *   <li>metas de redução de dívida usam a dívida inicial como denominador;
 *   <li>metas de patrimônio líquido usam a visão patrimonial atual.
 * </ul>
 */
@Service
public class GoalProgressService {

  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
  private static final String TABLE = "financial_goal";

  private final FinancialGoalRepository goalRepository;
  private final GoalAllocationRepository allocationRepository;
  private final AccountQueryService accountQueryService;
  private final InvestmentPositionService investmentPositionService;
  private final WealthService wealthService;
  private final CryptoService cryptoService;
  private final Clock clock;

  public GoalProgressService(
      FinancialGoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      AccountQueryService accountQueryService,
      InvestmentPositionService investmentPositionService,
      WealthService wealthService,
      CryptoService cryptoService,
      Clock clock) {
    this.goalRepository = goalRepository;
    this.allocationRepository = allocationRepository;
    this.accountQueryService = accountQueryService;
    this.investmentPositionService = investmentPositionService;
    this.wealthService = wealthService;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  public record AllocationContribution(GoalAllocation allocation, BigDecimal contribution) {}

  public record Evaluation(
      BigDecimal currentValue,
      BigDecimal percentage,
      BigDecimal remaining,
      BigDecimal unallocated,
      BigDecimal excess,
      List<AllocationContribution> contributions) {}

  public Evaluation evaluate(UUID userId, FinancialGoal goal, List<GoalAllocation> allocations) {
    BigDecimal target = targetAmount(goal);
    List<AllocationContribution> contributions = contributions(target, allocations);
    BigDecimal current;
    BigDecimal denominator;
    BigDecimal unallocated;

    switch (goal.getProgressRule()) {
      case NET_WORTH -> {
        current = nonNegative(parse(wealthService.overview(userId).netWorth()));
        denominator = target;
        unallocated = nonNegative(target.subtract(current));
        contributions = List.of();
      }
      case DEBT_REDUCTION -> {
        BigDecimal initial = initialValue(goal);
        current = sum(contributions);
        denominator = initial;
        unallocated = nonNegative(initial.subtract(current));
      }
      default -> {
        current = sum(contributions);
        denominator = target;
        unallocated = nonNegative(target.subtract(current));
      }
    }

    BigDecimal percentage = percentage(current, denominator);
    BigDecimal remaining = nonNegative(denominator.subtract(current));
    BigDecimal excess =
        goal.getProgressRule() == GoalProgressRule.DEBT_REDUCTION
            ? nonNegative(current.subtract(denominator))
            : nonNegative(current.subtract(target));
    return new Evaluation(
        scale(current), percentage, remaining, unallocated, excess, contributions);
  }

  /** Contribuições calculadas na ordem das alocações, com resíduo na última percentual. */
  public List<AllocationContribution> contributions(
      BigDecimal target, List<GoalAllocation> allocations) {
    List<GoalAllocation> ordered =
        allocations.stream()
            .sorted(
                java.util.Comparator.comparingInt(GoalAllocation::getOrdinal)
                    .thenComparing(GoalAllocation::getId))
            .toList();
    List<GoalAllocation> percentages =
        ordered.stream()
            .filter(allocation -> allocation.getAllocationMode() == AllocationMode.PERCENTAGE)
            .toList();
    BigDecimal totalRatio =
        percentages.stream()
            .map(GoalAllocation::getAllocationRatio)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalPercentageValue = target.multiply(totalRatio).setScale(2, RoundingMode.FLOOR);

    List<AllocationContribution> result = new ArrayList<>();
    BigDecimal accumulatedPercentage = ZERO;
    int percentageIndex = 0;
    for (GoalAllocation allocation : ordered) {
      BigDecimal contribution;
      if (allocation.getAllocationMode() == AllocationMode.FIXED_AMOUNT) {
        contribution = allocationAmount(allocation);
      } else {
        percentageIndex++;
        if (percentageIndex == percentages.size()) {
          contribution = totalPercentageValue.subtract(accumulatedPercentage);
        } else {
          contribution =
              target.multiply(allocation.getAllocationRatio()).setScale(2, RoundingMode.FLOOR);
        }
        accumulatedPercentage = accumulatedPercentage.add(contribution);
      }
      result.add(new AllocationContribution(allocation, scale(contribution)));
    }
    return result;
  }

  public GoalStatus effectiveStatus(FinancialGoal goal, BigDecimal percentage) {
    if (goal.getStatus() == GoalStatus.PAUSED) {
      return GoalStatus.PAUSED;
    }
    if (goal.getStatus() == GoalStatus.ARCHIVED) {
      return GoalStatus.ARCHIVED;
    }
    if (goal.getChangedAt() != null) {
      return GoalStatus.CHANGED;
    }
    if (percentage.compareTo(HUNDRED) >= 0) {
      return GoalStatus.ACHIEVED;
    }
    if (goal.getTargetDate() != null && goal.getTargetDate().isBefore(clock.today())) {
      return GoalStatus.OVERDUE;
    }
    return GoalStatus.ACTIVE;
  }

  /**
   * Garante que as alocações de uma fonte não excedem o saldo disponível, somando todas as metas.
   *
   * <p>Fontes {@code MANUAL} não têm saldo associado e não participam desta checagem.
   */
  public void validateSourceCapacity(
      UUID userId, UUID excludingGoalId, BigDecimal target, List<GoalAllocation> allocations) {
    Map<SourceKey, BigDecimal> required = new LinkedHashMap<>();
    for (AllocationContribution contribution : contributions(target, allocations)) {
      GoalAllocation allocation = contribution.allocation();
      if (allocation.getSourceType() == AllocationSourceType.MANUAL) {
        continue;
      }
      required.merge(
          new SourceKey(allocation.getSourceType(), allocation.getSourceId()),
          contribution.contribution(),
          BigDecimal::add);
    }

    for (Map.Entry<SourceKey, BigDecimal> entry : required.entrySet()) {
      SourceKey key = entry.getKey();
      if (key.sourceId() == null) {
        throw new ValidationException("allocations", "Informe a fonte da alocação.");
      }
      BigDecimal balance = sourceBalance(userId, key);
      BigDecimal reserved = reservedFromOtherGoals(userId, excludingGoalId, key);
      if (reserved.add(entry.getValue()).compareTo(balance) > 0) {
        throw new ValidationException(
            "allocations",
            "A soma das alocações da fonte excede o saldo disponível de " + format(balance) + ".");
      }
    }
  }

  public BigDecimal targetAmount(FinancialGoal goal) {
    return decryptAmount(
        goal.getUserId(), goal.getId(), TABLE, "target_amount", goal.getTargetAmountEnc());
  }

  public BigDecimal initialValue(FinancialGoal goal) {
    if (goal.getInitialValueEnc() == null) {
      throw new ValidationException(
          "initialValue", "Informe a dívida inicial para metas de redução de dívida.");
    }
    return decryptAmount(
        goal.getUserId(), goal.getId(), TABLE, "initial_value", goal.getInitialValueEnc());
  }

  private BigDecimal reservedFromOtherGoals(UUID userId, UUID excludingGoalId, SourceKey key) {
    BigDecimal total = ZERO;
    for (FinancialGoal other : goalRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
      if (other.getId().equals(excludingGoalId)
          || other.getProgressRule() == GoalProgressRule.NET_WORTH) {
        continue;
      }
      List<GoalAllocation> otherAllocations =
          allocationRepository.findByUserIdAndGoalIdOrderByOrdinalAscIdAsc(userId, other.getId());
      if (otherAllocations.isEmpty()) {
        continue;
      }
      BigDecimal target = targetAmount(other);
      for (AllocationContribution contribution : contributions(target, otherAllocations)) {
        GoalAllocation allocation = contribution.allocation();
        if (allocation.getSourceType() == key.sourceType()
            && Objects.equals(allocation.getSourceId(), key.sourceId())) {
          total = total.add(contribution.contribution());
        }
      }
    }
    return total;
  }

  private BigDecimal sourceBalance(UUID userId, SourceKey key) {
    try {
      return switch (key.sourceType()) {
        case ACCOUNT -> {
          AccountView account = accountQueryService.get(userId, key.sourceId());
          if (account.currentBalance() == null || account.currentBalance().isBlank()) {
            throw new ValidationException("allocations", "Saldo da fonte não informado.");
          }
          yield parse(account.currentBalance());
        }
        case INVESTMENT -> {
          InvestmentPositionView position = investmentPositionService.get(userId, key.sourceId());
          if (position.total() == null) {
            throw new ValidationException("allocations", "Valor da fonte não disponível.");
          }
          yield parse(position.total());
        }
        case MANUAL_ASSET, MANUAL_LIABILITY -> {
          ManualItemView item = wealthService.getManual(userId, key.sourceId());
          boolean asset = "ASSET".equals(item.kind());
          if ((key.sourceType() == AllocationSourceType.MANUAL_ASSET && !asset)
              || (key.sourceType() == AllocationSourceType.MANUAL_LIABILITY && asset)) {
            throw new ValidationException(
                "allocations", "Tipo da fonte não corresponde à alocação.");
          }
          yield parse(item.referenceValue());
        }
        default -> ZERO;
      };
    } catch (NotFoundException exception) {
      throw new ValidationException("allocations", "Fonte não encontrada.");
    }
  }

  private BigDecimal allocationAmount(GoalAllocation allocation) {
    return decryptAmount(
        allocation.getUserId(),
        allocation.getId(),
        "goal_allocation",
        "allocated_amount",
        allocation.getAllocatedAmountEnc());
  }

  private BigDecimal decryptAmount(
      UUID userId, UUID recordId, String table, String column, byte[] ciphertext) {
    try {
      return new BigDecimal(
          cryptoService.decryptString(
              ciphertext, new CryptoContext(userId, table, column, recordId)));
    } catch (CryptoException | NumberFormatException exception) {
      return ZERO;
    }
  }

  private static BigDecimal parse(String value) {
    return new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN);
  }

  private static BigDecimal percentage(BigDecimal current, BigDecimal denominator) {
    if (denominator.signum() <= 0) {
      return ZERO;
    }
    return current.multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_EVEN).min(HUNDRED);
  }

  private static BigDecimal sum(List<AllocationContribution> contributions) {
    return contributions.stream()
        .map(AllocationContribution::contribution)
        .reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value.signum() < 0 ? ZERO : scale(value);
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_EVEN);
  }

  static String format(BigDecimal value) {
    return scale(value).toPlainString();
  }

  private record SourceKey(AllocationSourceType sourceType, UUID sourceId) {}
}
