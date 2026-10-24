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
export type InvoicePayment = components['schemas']['InvoicePayment']
export type InvoicePaymentInput = components['schemas']['InvoicePaymentRequest']
export type CardProfileInput = components['schemas']['CardProfileRequest']
export type CardProfile = components['schemas']['CardProfile']

export interface CredentialsInput {
  readonly clientId: string
  readonly clientSecret: string
}

export interface TransactionFilters {
  readonly cursor?: string
  readonly limit?: number
  readonly accountId?: string
  readonly status?: string
  readonly type?: string
  readonly origin?: string
  readonly from?: string
  readonly to?: string
}

function transactionQuery(filters: TransactionFilters): string {
  const params = new URLSearchParams()
  if (filters.cursor) params.set('cursor', filters.cursor)
  if (filters.limit) params.set('limit', String(filters.limit))
  if (filters.accountId) params.set('accountId', filters.accountId)
  if (filters.status) params.set('status', filters.status)
  if (filters.type) params.set('type', filters.type)
  if (filters.origin) params.set('origin', filters.origin)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
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
  removeConnection: (id: string, deleteHistory = false) =>
    api.delete<void>(`/connections/${id}?deleteHistory=${deleteHistory}`),
  syncConnection: (id: string) => api.post<SyncAccepted>(`/connections/${id}/sync`),

  syncStatus: () => api.get<SyncStatus>('/sync/status'),
  syncRuns: (cursor?: string, limit = 20) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (cursor) params.set('cursor', cursor)
    return api.get<SyncRunPage>(`/sync/runs?${params.toString()}`)
  },

  reviewSourceRemoval: (transactionId: string, action: 'RESTORE' | 'CONFIRM_REMOVAL') =>
    api.post<Transaction>(`/transactions/${transactionId}/source-removal`, { action }),

  accounts: () => api.get<AccountList>('/accounts'),
  account: (id: string) => api.get<Account>(`/accounts/${id}`),
  createAccount: (input: ManualAccountInput) => api.post<Account>('/accounts', input),
  updateAccount: (id: string, input: AccountUpdateInput) =>
    api.patch<Account>(`/accounts/${id}`, input),

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

  updateCardProfile: (accountId: string, input: CardProfileInput) =>
    api.put<CardProfile>(`/cards/${accountId}/profile`, input),
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
}
