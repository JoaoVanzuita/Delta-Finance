import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type Transaction = components['schemas']['Transaction']
export type TransactionPage = components['schemas']['TransactionPage']
export type ManualTransactionInput = components['schemas']['ManualTransactionRequest']
export type TransactionCorrectionInput = components['schemas']['TransactionCorrectionRequest']

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

export const transactionsApi = {
  transactions: (filters: TransactionFilters = {}) =>
    api.get<TransactionPage>(`/transactions${transactionQuery(filters)}`),
  createTransaction: (input: ManualTransactionInput) =>
    api.post<Transaction>('/transactions', input),
  correctTransaction: (id: string, input: TransactionCorrectionInput) =>
    api.patch<Transaction>(`/transactions/${id}`, input),
  deleteTransaction: (id: string) => api.delete<void>(`/transactions/${id}`),
}
