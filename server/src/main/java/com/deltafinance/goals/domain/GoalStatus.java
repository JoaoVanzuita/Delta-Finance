package com.deltafinance.goals.domain;

/** Estado persistido ou derivado de uma meta. */
public enum GoalStatus {
  ACTIVE,
  ACHIEVED,
  PAUSED,
  ARCHIVED,
  OVERDUE,
  CHANGED
}
