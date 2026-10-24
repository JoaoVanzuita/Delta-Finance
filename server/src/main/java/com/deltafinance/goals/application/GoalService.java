package com.deltafinance.goals.application;

import com.deltafinance.goals.api.GoalAllocationRequest;
import com.deltafinance.goals.api.GoalRequest;
import com.deltafinance.goals.domain.AllocationMode;
import com.deltafinance.goals.domain.AllocationSourceType;
import com.deltafinance.goals.domain.FinancialGoal;
import com.deltafinance.goals.domain.FinancialGoalRepository;
import com.deltafinance.goals.domain.GoalAllocation;
import com.deltafinance.goals.domain.GoalAllocationRepository;
import com.deltafinance.goals.domain.GoalProgressRule;
import com.deltafinance.goals.domain.GoalProgressSnapshot;
import com.deltafinance.goals.domain.GoalProgressSnapshotRepository;
import com.deltafinance.goals.domain.GoalStatus;
import com.deltafinance.goals.domain.GoalType;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de metas e composição da visão com progresso e histórico.
 *
 * <p>Não há qualquer efeito financeiro: metas apenas leem agregações e alocações explícitas. As
 * únicas escritas são a própria meta, suas alocações e o snapshot diário de progresso.
 */
@Service
public class GoalService {

  private static final String GOAL_TABLE = "financial_goal";
  private static final String ALLOCATION_TABLE = "goal_allocation";
  private static final String SNAPSHOT_TABLE = "goal_progress_snapshot";
  private static final String RULE_VERSION = "v1";
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final FinancialGoalRepository goalRepository;
  private final GoalAllocationRepository allocationRepository;
  private final GoalProgressSnapshotRepository snapshotRepository;
  private final GoalProgressService progressService;
  private final CryptoService cryptoService;
  private final AuditService auditService;
  private final Clock clock;

  public GoalService(
      FinancialGoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      GoalProgressSnapshotRepository snapshotRepository,
      GoalProgressService progressService,
      CryptoService cryptoService,
      AuditService auditService,
      Clock clock) {
    this.goalRepository = goalRepository;
    this.allocationRepository = allocationRepository;
    this.snapshotRepository = snapshotRepository;
    this.progressService = progressService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional
  public List<GoalView> list(UUID userId) {
    return goalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(goal -> toView(userId, goal))
        .toList();
  }

  @Transactional
  public GoalView get(UUID userId, UUID goalId) {
    return toView(userId, requireGoal(userId, goalId));
  }

  @Transactional
  public GoalView create(UUID userId, GoalRequest request) {
    ParsedGoal parsed = parse(request);
    FinancialGoal goal = new FinancialGoal(userId);
    apply(goal, parsed);
    goalRepository.save(goal);

    List<GoalAllocation> allocations = saveAllocations(userId, goal.getId(), parsed.allocations());
    progressService.validateSourceCapacity(userId, goal.getId(), parsed.target(), allocations);
    auditService.record(
        userId,
        AuditEventType.GOAL_CREATED,
        GOAL_TABLE,
        goal.getId(),
        Map.of("type", goal.getType().name()));
    return toView(userId, goal);
  }

  @Transactional
  public GoalView update(UUID userId, UUID goalId, GoalRequest request) {
    FinancialGoal goal = requireGoal(userId, goalId);
    ParsedGoal parsed = parse(request);
    BigDecimal oldTarget = progressService.targetAmount(goal);
    boolean materialChange =
        oldTarget.compareTo(parsed.target()) != 0
            || !Objects.equals(goal.getTargetDate(), parsed.targetDate())
            || goal.getType() != parsed.type()
            || goal.getProgressRule() != parsed.progressRule();

    apply(goal, parsed);
    if (materialChange) {
      goal.markChanged(clock.now());
    }
    goalRepository.save(goal);

    allocationRepository.deleteByUserIdAndGoalId(userId, goalId);
    List<GoalAllocation> allocations = saveAllocations(userId, goalId, parsed.allocations());
    progressService.validateSourceCapacity(userId, goalId, parsed.target(), allocations);
    auditService.record(
        userId,
        AuditEventType.GOAL_UPDATED,
        GOAL_TABLE,
        goal.getId(),
        Map.of("changed", materialChange));
    return toView(userId, goal);
  }

  @Transactional
  public GoalView updateStatus(UUID userId, UUID goalId, String statusValue) {
    FinancialGoal goal = requireGoal(userId, goalId);
    GoalStatus requested = parseActionStatus(statusValue);
    boolean wasFrozen =
        goal.getStatus() == GoalStatus.PAUSED || goal.getStatus() == GoalStatus.ARCHIVED;

    if (requested == GoalStatus.ACTIVE) {
      goal.acknowledgeChange();
      goal.changeStatus(GoalStatus.ACTIVE);
      goalRepository.save(goal);
    } else {
      if (!wasFrozen) {
        List<GoalAllocation> allocations =
            allocationRepository.findByUserIdAndGoalIdOrderByOrdinalAscIdAsc(userId, goalId);
        persistProgress(
            userId, goal, progressService.evaluate(userId, goal, allocations), clock.today());
      }
      goal.changeStatus(requested);
      goalRepository.save(goal);
    }

    auditService.record(
        userId,
        AuditEventType.GOAL_STATUS_CHANGED,
        GOAL_TABLE,
        goal.getId(),
        Map.of("status", goal.getStatus().name()));
    return toView(userId, goal);
  }

  @Transactional
  public void delete(UUID userId, UUID goalId) {
    FinancialGoal goal = requireGoal(userId, goalId);
    allocationRepository.deleteByUserIdAndGoalId(userId, goalId);
    snapshotRepository.deleteByUserIdAndGoalId(userId, goalId);
    goalRepository.delete(goal);
    auditService.record(userId, AuditEventType.GOAL_DELETED, GOAL_TABLE, goalId);
  }

  private GoalView toView(UUID userId, FinancialGoal goal) {
    List<GoalAllocation> allocations =
        allocationRepository.findByUserIdAndGoalIdOrderByOrdinalAscIdAsc(userId, goal.getId());
    BigDecimal target = progressService.targetAmount(goal);
    boolean frozen =
        goal.getStatus() == GoalStatus.PAUSED || goal.getStatus() == GoalStatus.ARCHIVED;
    GoalProgressService.Evaluation evaluation;
    BigDecimal current;
    BigDecimal percentage;
    BigDecimal remaining;

    if (frozen) {
      GoalProgressSnapshot latest =
          snapshotRepository
              .findFirstByUserIdAndGoalIdOrderByReferenceDateDescIdDesc(userId, goal.getId())
              .orElse(null);
      if (latest == null) {
        evaluation = progressService.evaluate(userId, goal, allocations);
        persistProgress(userId, goal, evaluation, clock.today());
        current = evaluation.currentValue();
        percentage = evaluation.percentage();
        remaining = evaluation.remaining();
      } else {
        current =
            decryptAmount(
                userId,
                latest.getId(),
                SNAPSHOT_TABLE,
                "current_value",
                latest.getCurrentValueEnc());
        percentage = latest.getPercentage().setScale(2, RoundingMode.HALF_EVEN);
        remaining =
            decryptAmount(
                userId, latest.getId(), SNAPSHOT_TABLE, "remaining", latest.getRemainingEnc());
        evaluation = null;
      }
    } else {
      evaluation = progressService.evaluate(userId, goal, allocations);
      persistProgress(userId, goal, evaluation, clock.today());
      current = evaluation.currentValue();
      percentage = evaluation.percentage();
      remaining = evaluation.remaining();
    }

    GoalStatus status =
        frozen ? goal.getStatus() : progressService.effectiveStatus(goal, percentage);
    BigDecimal denominator =
        goal.getProgressRule() == GoalProgressRule.DEBT_REDUCTION
            ? progressService.initialValue(goal)
            : target;
    BigDecimal unallocated = nonNegative(denominator.subtract(current));
    BigDecimal excess =
        goal.getProgressRule() == GoalProgressRule.DEBT_REDUCTION
            ? nonNegative(current.subtract(denominator))
            : nonNegative(current.subtract(target));

    Map<UUID, BigDecimal> contributionByAllocation = new LinkedHashMap<>();
    for (GoalProgressService.AllocationContribution contribution :
        progressService.contributions(target, allocations)) {
      contributionByAllocation.put(contribution.allocation().getId(), contribution.contribution());
    }

    List<GoalAllocationView> allocationViews = new ArrayList<>();
    for (GoalAllocation allocation : allocations) {
      allocationViews.add(
          new GoalAllocationView(
              allocation.getId(),
              allocation.getSourceType().name(),
              allocation.getSourceId(),
              allocation.getAllocationMode().name(),
              allocation.getAllocatedAmountEnc() == null
                  ? null
                  : format(
                      decryptAmount(
                          userId,
                          allocation.getId(),
                          ALLOCATION_TABLE,
                          "allocated_amount",
                          allocation.getAllocatedAmountEnc())),
              allocation.getAllocationRatio() == null
                  ? null
                  : allocation
                      .getAllocationRatio()
                      .multiply(HUNDRED)
                      .stripTrailingZeros()
                      .toPlainString(),
              format(contributionByAllocation.getOrDefault(allocation.getId(), BigDecimal.ZERO))));
    }

    return new GoalView(
        goal.getId(),
        decryptText(userId, goal.getId(), GOAL_TABLE, "name", goal.getNameEnc()),
        decryptText(userId, goal.getId(), GOAL_TABLE, "description", goal.getDescriptionEnc()),
        goal.getType().name(),
        format(target),
        goal.getTargetDate(),
        status.name(),
        goal.getProgressRule().name(),
        goal.getInitialValueEnc() == null ? null : format(progressService.initialValue(goal)),
        goal.getChangedAt(),
        goal.getCreatedAt(),
        format(current),
        format(percentage),
        format(remaining),
        format(unallocated),
        format(excess),
        allocationViews,
        history(userId, goal.getId()));
  }

  private List<GoalProgressEntryView> history(UUID userId, UUID goalId) {
    return snapshotRepository
        .findByUserIdAndGoalIdOrderByReferenceDateDescIdDesc(userId, goalId)
        .stream()
        .map(
            snapshot ->
                new GoalProgressEntryView(
                    snapshot.getReferenceDate(),
                    format(
                        decryptAmount(
                            userId,
                            snapshot.getId(),
                            SNAPSHOT_TABLE,
                            "current_value",
                            snapshot.getCurrentValueEnc())),
                    format(snapshot.getPercentage()),
                    format(
                        decryptAmount(
                            userId,
                            snapshot.getId(),
                            SNAPSHOT_TABLE,
                            "remaining",
                            snapshot.getRemainingEnc())),
                    snapshot.getRuleVersion()))
        .toList();
  }

  private void persistProgress(
      UUID userId,
      FinancialGoal goal,
      GoalProgressService.Evaluation evaluation,
      LocalDate referenceDate) {
    GoalProgressSnapshot snapshot =
        snapshotRepository
            .findByUserIdAndGoalIdAndReferenceDate(userId, goal.getId(), referenceDate)
            .orElseGet(() -> new GoalProgressSnapshot(userId, goal.getId(), referenceDate));
    snapshot.apply(
        encrypt(
            userId,
            snapshot.getId(),
            SNAPSHOT_TABLE,
            "current_value",
            format(evaluation.currentValue())),
        evaluation.percentage(),
        encrypt(
            userId, snapshot.getId(), SNAPSHOT_TABLE, "remaining", format(evaluation.remaining())),
        RULE_VERSION);
    snapshotRepository.save(snapshot);
  }

  private ParsedGoal parse(GoalRequest request) {
    String name = requireText(request.name(), "name", 120, "Informe o nome da meta.");
    String description = optionalText(request.description(), "description", 500);
    GoalType type = parseEnum(GoalType.class, request.type(), "type", "Tipo de meta inválido.");
    BigDecimal target = parsePositiveMoney(request.targetAmount(), "targetAmount");
    GoalProgressRule rule =
        parseEnum(
            GoalProgressRule.class,
            request.progressRule(),
            "progressRule",
            "Regra de progresso inválida.");
    if (type == GoalType.DEBT_REDUCTION && rule != GoalProgressRule.DEBT_REDUCTION) {
      throw new ValidationException(
          "progressRule", "Metas de redução de dívida exigem a regra DEBT_REDUCTION.");
    }
    BigDecimal initial =
        rule == GoalProgressRule.DEBT_REDUCTION
            ? parsePositiveMoney(request.initialValue(), "initialValue")
            : null;
    List<PlannedAllocation> allocations = parseAllocations(request.allocations());
    if (rule == GoalProgressRule.NET_WORTH && !allocations.isEmpty()) {
      throw new ValidationException(
          "allocations", "Metas de patrimônio líquido não usam alocações explícitas.");
    }
    return new ParsedGoal(
        name, description, type, target, request.targetDate(), rule, initial, allocations);
  }

  private List<PlannedAllocation> parseAllocations(List<GoalAllocationRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return List.of();
    }
    List<PlannedAllocation> result = new ArrayList<>();
    BigDecimal totalPercentage = BigDecimal.ZERO;
    for (GoalAllocationRequest request : requests) {
      AllocationSourceType sourceType =
          parseEnum(
              AllocationSourceType.class,
              request.sourceType(),
              "allocations",
              "Origem da alocação inválida.");
      AllocationMode mode =
          parseEnum(
              AllocationMode.class,
              request.allocationMode(),
              "allocations",
              "Modo de alocação inválido.");
      if (sourceType == AllocationSourceType.MANUAL && request.sourceId() != null) {
        throw new ValidationException(
            "allocations", "Alocação MANUAL não aceita identificador de fonte.");
      }
      if (sourceType != AllocationSourceType.MANUAL && request.sourceId() == null) {
        throw new ValidationException("allocations", "Informe a fonte da alocação.");
      }

      boolean hasAmount = request.allocatedAmount() != null && !request.allocatedAmount().isBlank();
      boolean hasPercentage = request.percentage() != null && !request.percentage().isBlank();
      if (mode == AllocationMode.FIXED_AMOUNT) {
        if (!hasAmount || hasPercentage) {
          throw new ValidationException(
              "allocations", "Alocação FIXED_AMOUNT exige apenas allocatedAmount.");
        }
        BigDecimal amount = parseNonNegativeMoney(request.allocatedAmount(), "allocations");
        result.add(new PlannedAllocation(sourceType, request.sourceId(), mode, amount, null));
      } else {
        if (!hasPercentage || hasAmount) {
          throw new ValidationException(
              "allocations", "Alocação PERCENTAGE exige apenas percentage.");
        }
        BigDecimal percentage;
        try {
          percentage = new BigDecimal(request.percentage().trim());
        } catch (NumberFormatException exception) {
          throw new ValidationException("allocations", "Percentual inválido.");
        }
        if (percentage.signum() < 0 || percentage.compareTo(HUNDRED) > 0) {
          throw new ValidationException("allocations", "O percentual deve estar entre 0 e 100.");
        }
        totalPercentage = totalPercentage.add(percentage);
        result.add(
            new PlannedAllocation(
                sourceType,
                request.sourceId(),
                mode,
                null,
                percentage.divide(HUNDRED, 6, RoundingMode.HALF_EVEN)));
      }
    }
    if (totalPercentage.compareTo(HUNDRED) > 0) {
      throw new ValidationException("allocations", "A soma dos percentuais não pode exceder 100%.");
    }
    return result;
  }

  private void apply(FinancialGoal goal, ParsedGoal parsed) {
    UUID userId = goal.getUserId();
    UUID id = goal.getId();
    goal.apply(
        encrypt(userId, id, GOAL_TABLE, "name", parsed.name()),
        parsed.description() == null
            ? null
            : encrypt(userId, id, GOAL_TABLE, "description", parsed.description()),
        parsed.type(),
        encrypt(userId, id, GOAL_TABLE, "target_amount", format(parsed.target())),
        parsed.targetDate(),
        parsed.progressRule(),
        parsed.initial() == null
            ? null
            : encrypt(userId, id, GOAL_TABLE, "initial_value", format(parsed.initial())));
  }

  private List<GoalAllocation> saveAllocations(
      UUID userId, UUID goalId, List<PlannedAllocation> planned) {
    List<GoalAllocation> saved = new ArrayList<>();
    for (int index = 0; index < planned.size(); index++) {
      PlannedAllocation allocation = planned.get(index);
      GoalAllocation entity = new GoalAllocation(userId, goalId, index);
      entity.apply(
          allocation.sourceType(),
          allocation.sourceId(),
          allocation.mode(),
          allocation.amount() == null
              ? null
              : encrypt(
                  userId,
                  entity.getId(),
                  ALLOCATION_TABLE,
                  "allocated_amount",
                  format(allocation.amount())),
          allocation.ratio(),
          clock.today());
      allocationRepository.save(entity);
      saved.add(entity);
    }
    return saved;
  }

  private FinancialGoal requireGoal(UUID userId, UUID goalId) {
    return goalRepository.findByIdAndUserId(goalId, userId).orElseThrow(NotFoundException::new);
  }

  private byte[] encrypt(UUID userId, UUID recordId, String table, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, table, column, recordId));
  }

  private String decryptText(
      UUID userId, UUID recordId, String table, String column, byte[] ciphertext) {
    if (ciphertext == null) {
      return null;
    }
    try {
      return cryptoService.decryptString(
          ciphertext, new CryptoContext(userId, table, column, recordId));
    } catch (CryptoException exception) {
      return null;
    }
  }

  private BigDecimal decryptAmount(
      UUID userId, UUID recordId, String table, String column, byte[] ciphertext) {
    String value = decryptText(userId, recordId, table, column, ciphertext);
    if (value == null) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      return BigDecimal.ZERO;
    }
  }

  private static BigDecimal parsePositiveMoney(String value, String field) {
    BigDecimal parsed = parseNonNegativeMoney(value, field);
    if (parsed.signum() <= 0) {
      throw new ValidationException(field, "Informe um valor maior que zero.");
    }
    return parsed;
  }

  private static BigDecimal parseNonNegativeMoney(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field, "Informe um valor.");
    }
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(value.trim());
    } catch (NumberFormatException exception) {
      throw new ValidationException(field, "Valor inválido.");
    }
    if (parsed.signum() < 0) {
      throw new ValidationException(field, "O valor não pode ser negativo.");
    }
    return new Money(parsed, Money.BRL).amount();
  }

  private static String requireText(String value, String field, int maxLength, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field, message);
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLength) {
      throw new ValidationException(
          field, "O valor deve ter no máximo " + maxLength + " caracteres.");
    }
    return trimmed;
  }

  private static String optionalText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLength) {
      throw new ValidationException(
          field, "O valor deve ter no máximo " + maxLength + " caracteres.");
    }
    return trimmed;
  }

  private static GoalStatus parseActionStatus(String value) {
    GoalStatus status = parseEnum(GoalStatus.class, value, "status", "Estado inválido.");
    if (status != GoalStatus.ACTIVE
        && status != GoalStatus.PAUSED
        && status != GoalStatus.ARCHIVED) {
      throw new ValidationException("status", "Use ACTIVE, PAUSED ou ARCHIVED.");
    }
    return status;
  }

  private static <E extends Enum<E>> E parseEnum(
      Class<E> type, String value, String field, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field, message);
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(field, message);
    }
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value.signum() < 0 ? BigDecimal.ZERO : value;
  }

  private static String format(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
  }

  private record PlannedAllocation(
      AllocationSourceType sourceType,
      UUID sourceId,
      AllocationMode mode,
      BigDecimal amount,
      BigDecimal ratio) {}

  private record ParsedGoal(
      String name,
      String description,
      GoalType type,
      BigDecimal target,
      LocalDate targetDate,
      GoalProgressRule progressRule,
      BigDecimal initial,
      List<PlannedAllocation> allocations) {}
}
