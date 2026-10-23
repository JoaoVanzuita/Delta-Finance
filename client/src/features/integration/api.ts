import { api } from '@/lib/api/client'
import type { components } from '@/lib/api/schema'

export type Integration = components['schemas']['Integration']
export type ValidationResult = components['schemas']['ValidationResult']
export type Connection = components['schemas']['Connection']
export type ConnectionPage = components['schemas']['ConnectionPage']
export type Account = components['schemas']['Account']
export type AccountList = components['schemas']['AccountList']
export type Transaction = components['schemas']['Transaction']
export type TransactionPage = components['schemas']['TransactionPage']

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
  disconnect: () => api.delete<void>('/integrations/pluggy'),

  connections: () => api.get<ConnectionPage>('/connections'),
  addConnection: (itemId: string) => api.post<Connection>('/connections', { itemId }),
  removeConnection: (id: string) => api.delete<void>(`/connections/${id}`),

  accounts: () => api.get<AccountList>('/accounts'),
  account: (id: string) => api.get<Account>(`/accounts/${id}`),

  transactions: (filters: TransactionFilters = {}) =>
    api.get<TransactionPage>(`/transactions${transactionQuery(filters)}`),
}
