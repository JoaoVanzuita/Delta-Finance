/** Rótulos de domínio compartilhados entre telas do mesmo recurso. */

export const ACCOUNT_TYPE_LABELS: Record<string, string> = {
  BANK: 'Conta bancária',
  CREDIT: 'Cartão de crédito',
  CASH: 'Dinheiro',
  WALLET: 'Carteira digital',
  UNKNOWN: 'Tipo não informado',
}

export const ACCOUNT_SUBTYPE_LABELS: Record<string, string> = {
  CHECKING_ACCOUNT: 'Conta corrente',
  SAVINGS_ACCOUNT: 'Poupança',
  PAYMENT_ACCOUNT: 'Conta de pagamento',
  CREDIT_CARD: 'Cartão de crédito',
  OTHER: 'Outro',
}

export const ACCOUNT_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativa',
  INACTIVE: 'Inativa',
  UNAVAILABLE: 'Indisponível',
  CLOSED: 'Encerrada',
}

/** Cartões usam o masculino na interface. */
export const CARD_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativo',
  INACTIVE: 'Inativo',
  UNAVAILABLE: 'Indisponível',
  CLOSED: 'Encerrado',
}

export const TRANSACTION_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  POSTED: 'Efetivada',
  SOURCE_REMOVED: 'Removida na origem',
  RECONCILED: 'Reconciliada',
}

export const TRANSACTION_TYPE_LABELS: Record<string, string> = {
  DEBIT: 'Despesa',
  CREDIT: 'Receita',
  TRANSFER: 'Transferência',
  REFUND: 'Reembolso',
  CHARGEBACK: 'Chargeback',
  ADJUSTMENT: 'Ajuste',
}

export const TRANSACTION_ORIGIN_LABELS: Record<string, string> = {
  IMPORTED: 'Importada',
  MANUAL: 'Manual',
}

export const INVOICE_STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
}
