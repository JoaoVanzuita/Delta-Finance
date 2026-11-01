/**
 * Aritmética decimal para apresentação.
 *
 * Use somente para agregações visuais (por exemplo, agrupar pontos do gráfico ou
 * somar o bucket "Outras"). Totais financeiros oficiais continuam vindo do
 * backend. A implementação usa `BigInt` sobre a representação textual para não
 * introduzir erro de ponto flutuante.
 */

const DECIMAL_PATTERN = /^([+-]?)(\d+)(?:[.,](\d+))?$/

interface ParsedDecimal {
  readonly negative: boolean
  readonly integer: string
  readonly fraction: string
}

function parseDecimal(value: string): ParsedDecimal | null {
  const match = DECIMAL_PATTERN.exec(value.trim())
  if (!match) return null
  const [, sign = '', integer = '', fraction = ''] = match
  return {
    negative: sign === '-',
    integer: integer.replace(/^0+(?=\d)/, '') || '0',
    fraction,
  }
}

/** Soma valores decimais preservando a maior escala de casas encontrada. */
export function addDecimals(values: readonly string[]): string {
  if (values.length === 0) return '0.00'

  const parsed = values.map((value) => parseDecimal(value))
  if (parsed.some((item) => item === null)) {
    throw new Error('Valor decimal inválido para agregação de apresentação.')
  }

  const scale = Math.max(...parsed.map((item) => item?.fraction.length ?? 0), 2)
  let total = 0n
  for (const item of parsed) {
    if (!item) continue
    const digits = `${item.integer}${item.fraction.padEnd(scale, '0')}`
    const amount = BigInt(digits)
    total += item.negative ? -amount : amount
  }

  const negative = total < 0n
  const absolute = (negative ? -total : total).toString().padStart(scale + 1, '0')
  const cut = absolute.length - scale
  const integer = absolute.slice(0, cut)
  const fraction = absolute.slice(cut)
  return `${negative ? '-' : ''}${integer}.${fraction}`
}

/** Converte um decimal textual em `number` apenas para geometria de gráfico. */
export function toFiniteNumber(value: string): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

/** Diferença `left - right`, preservando a maior escala encontrada. */
export function subtractDecimals(left: string, right: string): string {
  return addDecimals([left, right.startsWith('-') ? right.slice(1) : `-${right}`])
}
