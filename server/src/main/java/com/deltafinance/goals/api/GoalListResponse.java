package com.deltafinance.goals.api;

import com.deltafinance.goals.application.GoalView;
import java.util.List;

/** Lista de metas com progresso calculado. */
public record GoalListResponse(List<GoalView> items) {}
