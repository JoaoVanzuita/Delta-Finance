/**
 * Chaves de consulta compartilhadas.
 *
 * Prefixos são intencionais: invalidar `['transactions']` atualiza todas as
 * variações de filtro, e `['invoices']` atualiza a lista de qualquer cartão.
 * Nenhum dado financeiro é armazenado aqui.
 */
export const queryKeys = {
  accounts: ['accounts'] as const,
  account: (id: string) => ['account', id] as const,
  accountPrefix: ['account'] as const,

  transactions: ['transactions'] as const,
  transactionFilters: (filters: Record<string, unknown>) => ['transactions', filters] as const,

  cards: ['cards'] as const,
  cardOverview: ['cards', 'overview'] as const,

  invoices: ['invoices'] as const,
  invoicesByCard: (accountId: string) => ['invoices', accountId] as const,
  invoice: (id: string) => ['invoice', id] as const,
  invoicePrefix: ['invoice'] as const,

  categories: ['categories'] as const,

  dashboardSummary: ['dashboard-summary'] as const,
  dashboardCategories: ['dashboard-categories'] as const,
  dashboardTimeseries: ['dashboard-timeseries'] as const,
  dashboardEntries: ['dashboard-entries'] as const,

  internalMatches: ['internalMatches'] as const,
  syncStatus: ['syncStatus'] as const,
  syncRuns: ['syncRuns'] as const,
  integration: ['integration'] as const,
  connections: ['connections'] as const,

  investments: ['investments'] as const,
  investmentOverview: ['investments', 'overview'] as const,
  investmentHoldings: (status?: string, assetClass?: string) =>
    ['investments', 'holdings', status ?? '', assetClass ?? ''] as const,
  investmentIncome: ['investments', 'income'] as const,

  wealth: ['wealth'] as const,
  wealthOverview: ['wealth', 'overview'] as const,
  wealthManualItems: ['wealth', 'manual-items'] as const,

  goals: ['goals'] as const,
} as const
