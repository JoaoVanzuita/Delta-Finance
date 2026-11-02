/** Normalização e validação comuns aos filtros de URL (transações e dashboard). */

export interface DateRangeOptions {
  /** Limite inclusivo em dias; omitido quando não há teto. */
  readonly maxDays?: number
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

export function dateRangeError(
  from?: string,
  to?: string,
  options: DateRangeOptions = {},
): string | undefined {
  if (!from || !to) return undefined
  const start = Date.parse(`${from}T12:00:00Z`)
  const end = Date.parse(`${to}T12:00:00Z`)
  if (Number.isNaN(start) || Number.isNaN(end)) return 'Informe datas válidas.'
  if (from > to) return 'A data inicial deve ser anterior à data final.'
  if (options.maxDays && (end - start) / 86_400_000 + 1 > options.maxDays) {
    return `O intervalo máximo é de ${options.maxDays} dias. Estreite o período.`
  }
  return undefined
}
