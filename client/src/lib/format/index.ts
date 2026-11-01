/**
 * Formatação de apresentação — pt-BR, America/Sao_Paulo.
 *
 * Valores monetários chegam do backend como string decimal. Este módulo nunca
 * converte o valor inteiro para `number`: separa parte inteira e fracionária,
 * agrupa a parte inteira com `Intl` e preserva a precisão informada.
 */

const LOCALE = 'pt-BR'
const TIME_ZONE = 'America/Sao_Paulo'
const NOT_INFORMED = 'Não informado'
const DECIMAL_PATTERN = /^([+-]?)(\d+)(?:[.,](\d+))?$/

export interface MoneyFormatOptions {
  readonly fallback?: string
  readonly minimumFractionDigits?: number
  readonly maximumFractionDigits?: number
  /** `always` adiciona + em valores positivos; `never` omite o sinal negativo. */
  readonly signDisplay?: 'auto' | 'always' | 'never'
}

export interface NumberFormatOptions {
  readonly fallback?: string
  readonly minimumFractionDigits?: number
  readonly maximumFractionDigits?: number
  readonly useGrouping?: boolean
  readonly signDisplay?: 'auto' | 'always' | 'never'
}

interface DecimalParts {
  readonly negative: boolean
  readonly integer: string
  readonly fraction: string
}

function parseDecimal(value: string | null | undefined): DecimalParts | null {
  if (value == null) {
    return null
  }
  const trimmed = String(value).trim()
  if (!trimmed) {
    return null
  }
  const match = DECIMAL_PATTERN.exec(trimmed)
  if (!match) {
    return null
  }
  const [, sign = '', integer = '', fraction = ''] = match
  return {
    negative: sign === '-',
    integer: integer.replace(/^0+(?=\d)/, '') || '0',
    fraction,
  }
}

function groupInteger(integer: string): string {
  try {
    return new Intl.NumberFormat(LOCALE, {
      useGrouping: true,
      maximumFractionDigits: 0,
    }).format(BigInt(integer))
  } catch {
    return integer
  }
}

function incrementDigits(digits: string): string {
  if (!digits) {
    return '1'
  }
  const characters = digits.split('')
  let index = characters.length - 1
  while (index >= 0) {
    const digit = characters[index]
    if (digit === undefined) {
      break
    }
    if (digit === '9') {
      characters[index] = '0'
      index -= 1
    } else {
      characters[index] = String(Number(digit) + 1)
      return characters.join('')
    }
  }
  return `1${characters.join('')}`
}

interface RoundedDecimal {
  readonly integer: string
  readonly fraction: string
}

/** Arredonda a representação decimal (half-up), preservando a precisão do texto. */
function roundDecimal(
  integer: string,
  fraction: string,
  minimumFractionDigits: number,
  maximumFractionDigits: number,
): RoundedDecimal {
  if (maximumFractionDigits <= 0) {
    const roundUp = fraction.charAt(0) >= '5'
    return { integer: roundUp ? incrementDigits(integer) : integer, fraction: '' }
  }
  if (fraction.length <= maximumFractionDigits) {
    return { integer, fraction: fraction.padEnd(minimumFractionDigits, '0') }
  }
  let kept = fraction.slice(0, maximumFractionDigits)
  const nextDigit = fraction.charAt(maximumFractionDigits)
  let nextInteger = integer
  if (nextDigit >= '5') {
    const incremented = incrementDigits(kept)
    if (incremented.length > kept.length) {
      nextInteger = incrementDigits(integer)
      kept = incremented.slice(1)
    } else {
      kept = incremented
    }
  }
  return { integer: nextInteger, fraction: kept.padEnd(minimumFractionDigits, '0') }
}

function buildFromFormatter(
  formatter: Intl.NumberFormat,
  parts: DecimalParts,
  minimumFractionDigits: number,
  maximumFractionDigits: number,
  signDisplay: MoneyFormatOptions['signDisplay'],
): string {
  const rounded = roundDecimal(
    parts.integer,
    parts.fraction,
    minimumFractionDigits,
    maximumFractionDigits,
  )
  const fraction = rounded.fraction
  const grouped = groupInteger(rounded.integer)
  const negative = parts.negative && signDisplay !== 'never'
  const positiveSign = signDisplay === 'always' && !parts.negative

  let output = ''
  let usedFraction = false
  for (const part of formatter.formatToParts(0)) {
    switch (part.type) {
      case 'currency':
        output += part.value
        break
      case 'integer':
        output += grouped
        break
      case 'group':
        output += '.'
        break
      case 'decimal':
        if (fraction) {
          output += ','
        }
        break
      case 'fraction':
        if (fraction) {
          output += fraction
          usedFraction = true
        }
        break
      case 'minusSign':
        if (negative) {
          output += '−'
        }
        break
      case 'plusSign':
        if (positiveSign) {
          output += '+'
        }
        break
      case 'literal':
        output += part.value
        break
      default:
        output += part.value
        break
    }
  }

  // `Intl` pode escolher variantes que omitem a fração para zero; garante o
  // contrato de duas casas para moeda quando necessário.
  if (fraction && !usedFraction) {
    output += `,${fraction}`
  }

  if (negative && !output.includes('−') && !output.startsWith('+')) {
    output = `−${output}`
  }
  if (positiveSign && !output.startsWith('+')) {
    output = `+${output}`
  }
  return output
}

function formatterFor(
  currency: string,
  minimumFractionDigits: number,
  maximumFractionDigits: number,
): Intl.NumberFormat {
  return new Intl.NumberFormat(LOCALE, {
    style: 'currency',
    currency,
    minimumFractionDigits,
    maximumFractionDigits,
  })
}

/**
 * Formata um valor monetário decimal sem convertê-lo a ponto flutuante.
 *
 * Moeda estrangeira mantém o símbolo/código da própria moeda — nunca R$.
 */
export function formatMoney(
  value: string | null | undefined,
  currency = 'BRL',
  options: MoneyFormatOptions = {},
): string {
  const parts = parseDecimal(value)
  if (!parts) {
    if (value != null && String(value).trim()) {
      return `${value} ${currency}`
    }
    return options.fallback ?? NOT_INFORMED
  }

  const minimumFractionDigits = options.minimumFractionDigits ?? 2
  const maximumFractionDigits = Math.max(minimumFractionDigits, options.maximumFractionDigits ?? 2)

  try {
    return buildFromFormatter(
      formatterFor(currency, minimumFractionDigits, maximumFractionDigits),
      parts,
      minimumFractionDigits,
      maximumFractionDigits,
      options.signDisplay,
    )
  } catch {
    return `${value} ${currency}`
  }
}

/** Igual a `formatMoney`, mas prefixa `+` em valores positivos. */
export function formatSignedMoney(
  value: string | null | undefined,
  currency = 'BRL',
  options: MoneyFormatOptions = {},
): string {
  return formatMoney(value, currency, { ...options, signDisplay: 'always' })
}

function asDecimalString(value: string | number | null | undefined): string | null {
  if (value == null) {
    return null
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) {
      return null
    }
    return String(value)
  }
  return String(value).trim() || null
}

/**
 * Formata número/percentual. `value` é o número já na escala exibida (12,3
 * significa 12,3%, não 0,123).
 */
export function formatNumber(
  value: string | number | null | undefined,
  options: NumberFormatOptions = {},
): string {
  const raw = asDecimalString(value)
  const parts = parseDecimal(raw)
  if (!parts) {
    return options.fallback ?? raw ?? NOT_INFORMED
  }

  const minimumFractionDigits = options.minimumFractionDigits ?? 0
  const maximumFractionDigits = Math.max(
    minimumFractionDigits,
    options.maximumFractionDigits ?? minimumFractionDigits,
  )
  const rounded = roundDecimal(
    parts.integer,
    parts.fraction,
    minimumFractionDigits,
    maximumFractionDigits,
  )
  let fraction = rounded.fraction
  while (fraction.length > minimumFractionDigits && fraction.endsWith('0')) {
    fraction = fraction.slice(0, -1)
  }
  const grouped = options.useGrouping === false ? rounded.integer : groupInteger(rounded.integer)
  const sign = parts.negative && options.signDisplay !== 'never' ? '−' : ''
  const plus = options.signDisplay === 'always' && !parts.negative ? '+' : ''

  return `${plus}${sign}${grouped}${fraction ? `,${fraction}` : ''}`
}

/**
 * Percentual com uma casa decimal, exceto inteiros exatos.
 * Recebe o valor já em pontos percentuais.
 */
export function formatPercent(
  value: string | number | null | undefined,
  options: NumberFormatOptions = {},
): string {
  const raw = asDecimalString(value)
  const parts = parseDecimal(raw)
  if (!parts) {
    return options.fallback ?? raw ?? NOT_INFORMED
  }
  const formatted = formatNumber(value, {
    ...options,
    minimumFractionDigits: 0,
    maximumFractionDigits: 1,
  })
  return `${formatted}%`
}

/** Converte a razão (0–1) em percentual formatado. */
export function formatRatioAsPercent(
  value: string | number | null | undefined,
  options: NumberFormatOptions = {},
): string {
  const raw = asDecimalString(value)
  const parts = parseDecimal(raw)
  if (!parts) {
    return options.fallback ?? NOT_INFORMED
  }
  // Multiplicação apenas sobre a representação decimal, sem float.
  const scaled = scaleDecimal(parts, 2)
  return formatPercent(scaled, options)
}

function scaleDecimal(parts: DecimalParts, exponent: number): string {
  const digits = `${parts.integer}${parts.fraction}`
  const shift = exponent - parts.fraction.length
  if (shift >= 0) {
    return `${parts.negative ? '-' : ''}${digits}${'0'.repeat(shift)}`
  }
  const cut = digits.length + shift
  const integer = digits.slice(0, Math.max(cut, 0)) || '0'
  const fraction = digits.slice(Math.max(cut, 0))
  return `${parts.negative ? '-' : ''}${integer}${fraction ? `.${fraction}` : ''}`
}

function civilDateKey(date: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function parseDateInput(value: string | null | undefined): Date | null {
  if (!value) {
    return null
  }
  const trimmed = String(value).trim()
  // Dia civil (`YYYY-MM-DD`) não pode sofrer deslocamento de fuso.
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(trimmed)
  const date = new Date(dateOnly ? `${trimmed}T12:00:00Z` : trimmed)
  return Number.isNaN(date.getTime()) ? null : date
}

export interface DateFormatOptions {
  readonly fallback?: string
}

export function formatDate(
  value: string | null | undefined,
  options: DateFormatOptions = {},
): string {
  const date = parseDateInput(value)
  if (!date) {
    return options.fallback ?? (value ? String(value) : 'Data não informada')
  }
  return new Intl.DateTimeFormat(LOCALE, {
    dateStyle: 'short',
    timeZone: TIME_ZONE,
  }).format(date)
}

export function formatDateTime(
  value: string | null | undefined,
  options: DateFormatOptions = {},
): string {
  const date = parseDateInput(value)
  if (!date) {
    return options.fallback ?? (value ? String(value) : 'Data não informada')
  }
  return new Intl.DateTimeFormat(LOCALE, {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: TIME_ZONE,
  }).format(date)
}

/** `hoje`, `ontem`, `há N dias`, `em N dias`; acima de 30 dias usa data. */
export function formatRelativeDate(
  value: string | null | undefined,
  now: Date = new Date(),
  options: DateFormatOptions = {},
): string {
  const date = parseDateInput(value)
  if (!date) {
    return options.fallback ?? formatDate(value, options)
  }
  const targetKey = civilDateKey(date)
  const nowKey = civilDateKey(now)
  const target = Date.parse(`${targetKey}T12:00:00Z`)
  const today = Date.parse(`${nowKey}T12:00:00Z`)
  const days = Math.round((target - today) / 86_400_000)
  if (days === 0) return 'hoje'
  if (days === -1) return 'ontem'
  if (days === 1) return 'amanhã'
  if (days < 0 && Math.abs(days) <= 30) return `há ${Math.abs(days)} dias`
  if (days > 1 && days <= 30) return `em ${days} dias`
  return formatDate(value, options)
}

/** Formata mês/ano de um dia civil ou timestamp. */
export function formatMonthYear(
  value: string | null | undefined,
  options: DateFormatOptions = {},
): string {
  const date = parseDateInput(value)
  if (!date) {
    return options.fallback ?? formatDate(value, options)
  }
  return new Intl.DateTimeFormat(LOCALE, {
    month: 'long',
    year: 'numeric',
    timeZone: TIME_ZONE,
  }).format(date)
}

/** Exposto para consumidores que precisam apenas validar o contrato decimal. */
export function isDecimalString(value: string | null | undefined): boolean {
  return typeof value === 'string' && /^[+-]?\d+(?:\.\d+)?$/.test(value.trim())
}

export { addDecimals, subtractDecimals, toFiniteNumber } from './decimal'
