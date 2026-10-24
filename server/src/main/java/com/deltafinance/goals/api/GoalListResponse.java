package com.deltafinance.goals.api;

import java.util.List;

/** Lista de metas com progresso calculado. */
public record GoalListResponse(List<GoalResponse> items) {}
