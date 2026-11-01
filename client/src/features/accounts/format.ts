/** Formatação de apresentação; valores autoritativos continuam vindo do servidor. */
import {
  formatDateTime as formatDateTimeShared,
  formatMoney as formatMoneyShared,
} from '@/lib/format'

export function formatMoney(value: string | null | undefined, currency: string): string {
  return formatMoneyShared(value, currency)
}

export function formatDateTime(value: string | null | undefined): string {
  return formatDateTimeShared(value, { fallback: 'Não informado' })
}
