/** Formatação de apresentação de investimentos; valores autoritativos vêm do servidor. */

export function formatMoney(value: string | null | undefined): string {
  if (value == null || value === '') {
    return 'Não informado'
  }
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return value
  }
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(number)
}

/** Preço unitário pode ter até 8 casas decimais; nunca é arredondado na apresentação. */
export function formatReferencePrice(value: string | null | undefined): string {
  if (value == null || value === '') {
    return 'Não informado'
  }
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return value
  }
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 8,
  }).format(number)
}

export function formatQuantity(value: string | null | undefined): string {
  if (value == null || value === '') {
    return 'Não informada'
  }
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return value
  }
  return new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 8 }).format(number)
}

/** Preço unitário de posição importada, respeitando a moeda informada. */
export function formatUnitPrice(
  value: string | null | undefined,
  currency: string | null | undefined,
): string {
  if (value == null || value === '') {
    return 'Não informado'
  }
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return value
  }
  const code = currency || 'BRL'
  try {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: code,
      minimumFractionDigits: 2,
      maximumFractionDigits: 8,
    }).format(number)
  } catch {
    return `${value} ${code}`
  }
}

/** Data de referência é um dia civil; não sofre deslocamento de fuso. */
export function formatReferenceDate(value: string | null | undefined): string {
  if (!value) {
    return 'Não informada'
  }
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}
