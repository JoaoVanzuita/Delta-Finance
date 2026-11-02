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
  accountReservedBalances: (id: string) => ['account', id, 'reserved-balances'] as const,
  reservedBalanceHistory: (id: string) => ['wealth', 'reserved-balance', id, 'history'] as const,
  accountPrefix: ['account'] as const,

  transactions: ['transactions'] as const,
  transactionFilters: (filters: Record<string, unknown>) => ['transactions', filters] as const,
  transactionRefundOriginals: (accountId: string) =>
    ['transactions', 'refund-originals', accountId] as const,
  transactionReconciliation: ['transactions', 'reconciliation'] as const,
  invoiceTransactions: (cardAccountId: string | undefined) =>
    ['transactions', 'card', cardAccountId] as const,
  invoiceItems: (cardAccountId: string | undefined) =>
    ['transactions', 'invoice-items', cardAccountId] as const,

  cards: ['cards'] as const,
  cardOverview: ['cards', 'overview'] as const,

  invoices: ['invoices'] as const,
  invoicesByCard: (accountId: string) => ['invoices', accountId] as const,
  invoice: (id: string) => ['invoice', id] as const,
  invoicePrefix: ['invoice'] as const,
  invoiceItemCorrections: (itemId: string) => ['invoiceItemCorrections', itemId] as const,

  categories: ['categories'] as const,

  dashboardSummary: ['dashboard-summary'] as const,
  dashboardCategories: ['dashboard-categories'] as const,
  dashboardTimeseries: ['dashboard-timeseries'] as const,
  dashboardEntries: ['dashboard-entries'] as const,
  dashboardSummaryFor: (filters: unknown) => ['dashboard-summary', filters] as const,
  dashboardCategoriesFor: (filters: unknown, kind: string) =>
    ['dashboard-categories', filters, kind] as const,
  dashboardTimeseriesFor: (filters: unknown) => ['dashboard-timeseries', filters] as const,
  dashboardEntriesFor: (kind: string, filters: unknown) =>
    ['dashboard-entries', kind, filters] as const,

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
  investmentHistory: ['investments', 'history'] as const,
  investmentHistoryRange: (range: unknown) => ['investments', 'history', range] as const,
  investmentHoldingsPositions: (status?: string, assetClass?: string) =>
    ['investments', 'holdings', status ?? '', assetClass ?? '', 'positions'] as const,
  investmentHoldingsActive: ['investments', 'holdings', 'ACTIVE', '', 'active'] as const,
  investmentContributions: (months: unknown) => ['investments', 'contributions', months] as const,

  wealth: ['wealth'] as const,
  wealthOverview: ['wealth', 'overview'] as const,
  wealthManualItems: ['wealth', 'manual-items'] as const,
  wealthHistory: ['wealth', 'history'] as const,
  wealthHistoryRange: (range: unknown) => ['wealth', 'history', range] as const,
  wealthSnapshot: (id: string | null | undefined) => ['wealth', 'history', id] as const,

  goals: ['goals'] as const,
  goalProgress: (id: string) => ['goals', id, 'progress'] as const,
  goalInvestmentSources: ['investments', 'goal-sources'] as const,
  goalManualSources: ['wealth', 'manual', 'goal-sources'] as const,
} as const
