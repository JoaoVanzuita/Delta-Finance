export interface TransactionFilterState {
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

export function filtersFromSearchParams(params: URLSearchParams): TransactionFilterState {
  return {
    accountId: params.get('accountId') || undefined,
    categoryId: params.get('categoryId') || undefined,
    status: params.get('status') || undefined,
    type: params.get('type') || undefined,
    origin: params.get('origin') || undefined,
    from: params.get('from') || undefined,
    to: params.get('to') || undefined,
    minAmount: params.get('minAmount') || undefined,
    maxAmount: params.get('maxAmount') || undefined,
  }
}

export function searchParamsFromFilters(filters: TransactionFilterState): URLSearchParams {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value) params.set(key, value)
  }
  return params
}

export function filterCount(filters: TransactionFilterState): number {
  return Object.values(filters).filter(Boolean).length
}

export { amountRangeError, dateRangeError, normalizeAmountInput } from '@/lib/filters'
