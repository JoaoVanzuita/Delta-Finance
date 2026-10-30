import { api } from '@/lib/api/client'
import type { components } from '@/lib/api/schema'

export type Integration = components['schemas']['Integration']
export type ValidationResult = components['schemas']['ValidationResult']
export type Connection = components['schemas']['Connection']
export type ConnectionPage = components['schemas']['ConnectionPage']
export type SyncStatus = components['schemas']['SyncStatus']
export type SyncRun = components['schemas']['SyncRun']
export type SyncRunPage = components['schemas']['SyncRunPage']
export type SyncAccepted = components['schemas']['SyncAccepted']
export type Account = components['schemas']['Account']
export type AccountList = components['schemas']['AccountList']
export type Transaction = components['schemas']['Transaction']
export type TransactionPage = components['schemas']['TransactionPage']
export type ManualAccountInput = components['schemas']['ManualAccountRequest']
export type AccountUpdateInput = components['schemas']['AccountUpdateRequest']
export type Category = components['schemas']['Category']
export type CategoryList = components['schemas']['CategoryList']
export type CategoryInput = components['schemas']['CategoryRequest']
export type ManualTransactionInput = components['schemas']['ManualTransactionRequest']
export type TransactionCorrectionInput = components['schemas']['TransactionCorrectionRequest']
export type InternalMatch = components['schemas']['InternalMatch']
export type InternalMatchPage = components['schemas']['InternalMatchPage']
export type InternalMatchStatus = InternalMatch['status']
export type Invoice = components['schemas']['Invoice']
export type InvoicePage = components['schemas']['InvoicePage']
export type InvoiceDetail = components['schemas']['InvoiceDetail']
export type InvoiceItem = components['schemas']['InvoiceItem']
export type InvoiceItemInput = components['schemas']['InvoiceItemRequest']
export type InvoiceItemCorrection = components['schemas']['InvoiceItemCorrection']
export type ProviderBill = components['schemas']['ProviderBill']
export type ProviderBillPayment = components['schemas']['ProviderBillPayment']
export type InvoicePayment = components['schemas']['InvoicePayment']
export type InvoicePaymentInput = components['schemas']['InvoicePaymentRequest']
export type InvoiceReconciliationInput = components['schemas']['InvoiceReconciliationRequest']
export type CardProfileInput = components['schemas']['CardProfileRequest']
export type CardProfile = components['schemas']['CardProfile']
export type CardOverview = components['schemas']['CardOverview']
export type CardOverviewItem = components['schemas']['CardOverviewItem']
export type InvestmentOverview = components['schemas']['InvestmentOverview']
export type InvestmentAllocation = components['schemas']['InvestmentAllocation']
export type InvestmentHolding = components['schemas']['InvestmentHolding']
export type InvestmentHoldingList = components['schemas']['InvestmentHoldingList']
export type InvestmentSource = components['schemas']['InvestmentSource']
export type InvestmentSourceList = components['schemas']['InvestmentSourceList']
export type InvestmentIncome = components['schemas']['InvestmentIncome']
export type InvestmentIncomeItem = components['schemas']['InvestmentIncomeItem']
export type InvestmentIncomeEntry = components['schemas']['InvestmentIncomeEntry']
export type InvestmentIncomeInput = components['schemas']['InvestmentIncomeRequest']
export type InvestmentCostBasisInput = components['schemas']['InvestmentCostBasisRequest']
export type InvestmentAssetClassInput = components['schemas']['InvestmentAssetClassRequest']
export type WealthOverview = components['schemas']['WealthOverview']
export type WealthItem = components['schemas']['WealthItem']
export type WealthManualItem = components['schemas']['WealthManualItem']
export type WealthManualItemList = components['schemas']['WealthManualItemList']
export type WealthManualItemInput = components['schemas']['WealthManualItemRequest']
export type Goal = components['schemas']['Goal']
export type GoalList = components['schemas']['GoalList']
export type GoalInput = components['schemas']['GoalRequest']
export type GoalAllocation = components['schemas']['GoalAllocation']
export type GoalAllocationInput = components['schemas']['GoalAllocationRequest']
export type GoalProgressEntry = components['schemas']['GoalProgressEntry']

export interface CredentialsInput {
  readonly clientId: string
  readonly clientSecret: string
}

export interface TransactionFilters {
  readonly cursor?: string
  readonly limit?: number
  readonly accountId?: string
  readonly categoryId?: string
  readonly status?: string
  readonly type?: string
  readonly origin?: string
  readonly from?: string
  readonly to?: string
  readonly minAmount?: string
  readonly maxAmount?: string
}

function transactionQuery(filters: TransactionFilters): string {
  const params = new URLSearchParams()
  if (filters.cursor) params.set('cursor', filters.cursor)
  if (filters.limit) params.set('limit', String(filters.limit))
  if (filters.accountId) params.set('accountId', filters.accountId)
  if (filters.categoryId) params.set('categoryId', filters.categoryId)
  if (filters.status) params.set('status', filters.status)
  if (filters.type) params.set('type', filters.type)
  if (filters.origin) params.set('origin', filters.origin)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.minAmount) params.set('minAmount', filters.minAmount)
  if (filters.maxAmount) params.set('maxAmount', filters.maxAmount)
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const financeApi = {
  integration: () => api.get<Integration>('/integrations/pluggy'),
  saveCredentials: (input: CredentialsInput) => api.put<Integration>('/integrations/pluggy', input),
  validateCredentials: (input: CredentialsInput) =>
    api.post<ValidationResult>('/integrations/pluggy/validate', input),
  disconnect: (deleteImportedData = false) =>
    api.delete<void>(`/integrations/pluggy?deleteImportedData=${deleteImportedData}`),

  connections: () => api.get<ConnectionPage>('/connections'),
  addConnection: (itemId: string) => api.post<Connection>('/connections', { itemId }),
  updateConnection: (id: string, itemId: string) =>
    api.patch<Connection>(`/connections/${id}`, { itemId }),
  renameConnection: (id: string, displayName: string) =>
    api.put<Connection>(`/connections/${id}/display-name`, { displayName }),
  resetConnectionName: (id: string) => api.delete<Connection>(`/connections/${id}/display-name`),
  removeConnection: (id: string, deleteHistory = false) =>
    api.delete<void>(`/connections/${id}?deleteHistory=${deleteHistory}`),
  syncConnection: (id: string) => api.post<SyncAccepted>(`/connections/${id}/sync`),

  syncStatus: () => api.get<SyncStatus>('/sync/status'),
  syncRuns: (cursor?: string, limit = 20) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (cursor) params.set('cursor', cursor)
    return api.get<SyncRunPage>(`/sync/runs?${params.toString()}`)
  },

  accounts: () => api.get<AccountList>('/accounts'),
  account: (id: string) => api.get<Account>(`/accounts/${id}`),
  createAccount: (input: ManualAccountInput) => api.post<Account>('/accounts', input),
  updateAccount: (id: string, input: AccountUpdateInput) =>
    api.patch<Account>(`/accounts/${id}`, input),
  renameAccount: (id: string, displayName: string) =>
    api.put<Account>(`/accounts/${id}/display-name`, { displayName }),
  resetAccountName: (id: string) => api.delete<Account>(`/accounts/${id}/display-name`),

  transactions: (filters: TransactionFilters = {}) =>
    api.get<TransactionPage>(`/transactions${transactionQuery(filters)}`),
  createTransaction: (input: ManualTransactionInput) =>
    api.post<Transaction>('/transactions', input),
  correctTransaction: (id: string, input: TransactionCorrectionInput) =>
    api.patch<Transaction>(`/transactions/${id}`, input),
  deleteTransaction: (id: string) => api.delete<void>(`/transactions/${id}`),

  categories: () => api.get<CategoryList>('/categories'),
  createCategory: (input: CategoryInput) => api.post<Category>('/categories', input),
  updateCategory: (id: string, input: CategoryInput) =>
    api.patch<Category>(`/categories/${id}`, input),
  deactivateCategory: (id: string, reassignTo?: string) => {
    const query = reassignTo ? `?reassignTo=${encodeURIComponent(reassignTo)}` : ''
    return api.delete<void>(`/categories/${id}${query}`)
  },

  internalMatches: (status: InternalMatchStatus = 'PENDING') =>
    api.get<InternalMatchPage>(`/internal-matches?status=${status}&limit=50`),
  confirmMatch: (id: string) => api.post<InternalMatch>(`/internal-matches/${id}/confirm`),
  rejectMatch: (id: string) => api.post<InternalMatch>(`/internal-matches/${id}/reject`),

  investmentOverview: () => api.get<InvestmentOverview>('/investments/overview'),
  investmentHoldings: (status?: string, assetClass?: string) => {
    const params = new URLSearchParams()
    if (status) params.set('status', status)
    if (assetClass) params.set('assetClass', assetClass)
    const query = params.toString()
    return api.get<InvestmentHoldingList>(`/investments/holdings${query ? `?${query}` : ''}`)
  },
  investmentHolding: (id: string) => api.get<InvestmentHolding>(`/investments/holdings/${id}`),
  investmentSources: () => api.get<InvestmentSourceList>('/investments/sources'),
  investmentIncome: (from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const query = params.toString()
    return api.get<InvestmentIncome>(`/investments/income${query ? `?${query}` : ''}`)
  },
  createInvestmentIncome: (input: InvestmentIncomeInput) =>
    api.post<InvestmentIncomeEntry>('/investments/income', input),
  updateInvestmentIncome: (id: string, input: InvestmentIncomeInput) =>
    api.patch<InvestmentIncomeEntry>(`/investments/income/${id}`, input),
  deleteInvestmentIncome: (id: string) => api.delete<void>(`/investments/income/${id}`),
  updateInvestmentCostBasis: (id: string, input: InvestmentCostBasisInput) =>
    api.patch<void>(`/investments/holdings/${id}/cost-basis`, input),
  updateInvestmentAssetClass: (id: string, input: InvestmentAssetClassInput) =>
    api.patch<void>(`/investments/holdings/${id}/asset-class`, input),

  wealthOverview: () => api.get<WealthOverview>('/wealth/overview'),
  wealthManualItems: () => api.get<WealthManualItemList>('/wealth/manual-items'),
  createWealthManualItem: (input: WealthManualItemInput) =>
    api.post<WealthManualItem>('/wealth/manual-items', input),
  updateWealthManualItem: (id: string, input: WealthManualItemInput) =>
    api.patch<WealthManualItem>(`/wealth/manual-items/${id}`, input),
  deleteWealthManualItem: (id: string) => api.delete<void>(`/wealth/manual-items/${id}`),

  goals: () => api.get<GoalList>('/goals'),
  goal: (id: string) => api.get<Goal>(`/goals/${id}`),
  createGoal: (input: GoalInput) => api.post<Goal>('/goals', input),
  updateGoal: (id: string, input: GoalInput) => api.patch<Goal>(`/goals/${id}`, input),
  updateGoalStatus: (id: string, status: 'ACTIVE' | 'PAUSED' | 'ARCHIVED') =>
    api.post<Goal>(`/goals/${id}/status`, { status }),
  deleteGoal: (id: string) => api.delete<void>(`/goals/${id}`),

  updateCardProfile: (accountId: string, input: CardProfileInput) =>
    api.put<CardProfile>(`/cards/${accountId}/profile`, input),
  cardOverview: () => api.get<CardOverview>('/cards/overview'),
  invoices: (accountId: string) => api.get<InvoicePage>(`/cards/${accountId}/invoices`),
  invoice: (id: string) => api.get<InvoiceDetail>(`/invoices/${id}`),
  addInvoiceItem: (invoiceId: string, input: InvoiceItemInput) =>
    api.post<InvoiceItem>(`/invoices/${invoiceId}/items`, input),
  updateInvoiceItem: (itemId: string, input: InvoiceItemInput) =>
    api.patch<InvoiceItem>(`/invoice-items/${itemId}`, input),
  deleteInvoiceItem: (itemId: string) => api.delete<void>(`/invoice-items/${itemId}`),
  invoiceItemCorrections: (itemId: string) =>
    api.get<InvoiceItemCorrection[]>(`/invoice-items/${itemId}/corrections`),
  addInvoicePayment: (invoiceId: string, input: InvoicePaymentInput) =>
    api.post<InvoicePayment>(`/invoices/${invoiceId}/payments`, input),
  reconcileInvoiceProvider: (invoiceId: string, input: InvoiceReconciliationInput) =>
    api.post<InvoiceDetail>(`/invoices/${invoiceId}/reconcile-provider`, input),
}
