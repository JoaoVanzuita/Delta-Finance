package com.deltafinance.accounts.domain;

/** Como o saldo reservado entra no patrimônio consolidado. */
public enum ReservedBalanceTreatment {
  /** Ainda não validado; não soma ao patrimônio até o usuário decidir. */
  UNKNOWN,
  /** O saldo da conta já inclui a reserva; exibir apenas como detalhamento. */
  INCLUDED_IN_BALANCE,
  /** O saldo da conta não inclui a reserva; somar como ativo separado. */
  SEPARATE_ASSET
}
