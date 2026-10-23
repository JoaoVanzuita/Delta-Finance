package com.deltafinance.transactions.application;

/** Ação de revisão de uma transação marcada como removida na origem. */
public enum SourceRemovalAction {
  RESTORE,
  CONFIRM_REMOVAL
}
