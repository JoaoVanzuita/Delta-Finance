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

export const EMPTY_TRANSACTION_FILTERS: TransactionFilterState = {}

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

export function normalizeAmountInput(value: string): string | undefined {
  const trimmed = value.trim()
  if (!trimmed) return undefined
  const withoutGrouping = trimmed.includes(',') ? trimmed.replace(/\./g, '') : trimmed
  const normalized = withoutGrouping.replace(',', '.')
  return /^\d+(?:\.\d{1,2})?$/.test(normalized) ? normalized : undefined
}

export function amountRangeError(
  minAmount: string | undefined,
  maxAmount: string | undefined,
): string | undefined {
  if (minAmount && !normalizeAmountInput(minAmount)) return 'Informe um valor mínimo válido.'
  if (maxAmount && !normalizeAmountInput(maxAmount)) return 'Informe um valor máximo válido.'
  const min = minAmount ? normalizeAmountInput(minAmount) : undefined
  const max = maxAmount ? normalizeAmountInput(maxAmount) : undefined
  if (min && max && Number(min) > Number(max)) {
    return 'O valor mínimo não pode ser maior que o valor máximo.'
  }
  return undefined
}

export function dateRangeError(from?: string, to?: string): string | undefined {
  if (from && to && from > to) return 'A data inicial deve ser anterior à data final.'
  return undefined
}
