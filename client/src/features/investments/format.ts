/** Formatação de apresentação de investimentos; valores autoritativos vêm do servidor. */
import {
  formatDate as formatDateShared,
  formatMoney as formatMoneyShared,
  formatNumber,
} from '@/lib/format'

export function formatMoney(value: string | null | undefined): string {
  return formatMoneyShared(value, 'BRL')
}

/** Preço unitário pode ter até 8 casas decimais; nunca é arredondado na apresentação. */
export function formatReferencePrice(value: string | null | undefined): string {
  return formatMoneyShared(value, 'BRL', { minimumFractionDigits: 2, maximumFractionDigits: 8 })
}

export function formatQuantity(value: string | null | undefined): string {
  return formatNumber(value, { fallback: 'Não informada', maximumFractionDigits: 8 })
}

/** Preço unitário de posição importada, respeitando a moeda informada. */
export function formatUnitPrice(
  value: string | null | undefined,
  currency: string | null | undefined,
): string {
  return formatMoneyShared(value, currency || 'BRL', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 8,
  })
}

/** Data de referência é um dia civil; não sofre deslocamento de fuso. */
export function formatReferenceDate(value: string | null | undefined): string {
  return formatDateShared(value, { fallback: 'Não informada' })
}
