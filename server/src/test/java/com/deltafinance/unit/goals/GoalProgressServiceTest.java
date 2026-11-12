package com.deltafinance.unit.goals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deltafinance.accounts.application.AccountQueryService;
import com.deltafinance.goals.application.GoalProgressService;
import com.deltafinance.goals.domain.AllocationMode;
import com.deltafinance.goals.domain.AllocationSourceType;
import com.deltafinance.goals.domain.FinancialGoalRepository;
import com.deltafinance.goals.domain.GoalAllocation;
import com.deltafinance.goals.domain.GoalAllocationRepository;
import com.deltafinance.investments.application.InvestmentHoldingView;
import com.deltafinance.investments.application.InvestmentQueryService;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.wealth.application.WealthService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalProgressServiceTest {

  @Mock private FinancialGoalRepository goalRepository;
  @Mock private GoalAllocationRepository allocationRepository;
  @Mock private AccountQueryService accountQueryService;
  @Mock private InvestmentQueryService investmentQueryService;
  @Mock private WealthService wealthService;
  @Mock private EncryptedValueReader encryptedValueReader;
  @Mock private Clock clock;

  @Test
  void recusaInvestimentoEmMoedaDiferenteDeBrl() {
    UUID userId = UUID.randomUUID();
    UUID investmentId = UUID.randomUUID();
    GoalAllocation allocation = new GoalAllocation(userId, UUID.randomUUID(), 0);
    allocation.apply(
        AllocationSourceType.INVESTMENT,
        investmentId,
        AllocationMode.FIXED_AMOUNT,
        new byte[] {1},
        null,
        LocalDate.of(2026, 1, 1));

    when(encryptedValueReader.readMoneyOrZero(any(), any(), any(), any(), any()))
        .thenReturn(new BigDecimal("50.00"));
    InvestmentHoldingView holding = mock(InvestmentHoldingView.class);
    when(holding.currency()).thenReturn("USD");
    when(investmentQueryService.holding(userId, investmentId)).thenReturn(holding);

    GoalProgressService service =
        new GoalProgressService(
            goalRepository,
            allocationRepository,
            accountQueryService,
            investmentQueryService,
            wealthService,
            encryptedValueReader,
            clock);

    assertThatThrownBy(
            () ->
                service.validateSourceCapacity(
                    userId, null, new BigDecimal("1000.00"), List.of(allocation)))
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .anySatisfy(
                        error -> assertThat(error.message()).contains("moeda diferente de BRL")));
  }
}
