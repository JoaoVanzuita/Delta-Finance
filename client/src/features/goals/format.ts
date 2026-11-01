/** Formatação e rótulos de metas; valores autoritativos vêm do servidor. */
import { formatDate as formatDateShared, formatMoney as formatMoneyShared } from '@/lib/format'

export function formatMoney(value: string | null | undefined): string {
  return formatMoneyShared(value, 'BRL')
}

export function formatDate(value: string | null | undefined): string {
  return formatDateShared(value, { fallback: 'Sem prazo' })
}

export const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativa',
  ACHIEVED: 'Concluída',
  PAUSED: 'Pausada',
  ARCHIVED: 'Arquivada',
  OVERDUE: 'Atrasada',
  CHANGED: 'Alterada',
}

export const TYPE_LABELS: Record<string, string> = {
  EMERGENCY_FUND: 'Reserva de emergência',
  TRAVEL: 'Viagem',
  PLANNED_PURCHASE: 'Compra planejada',
  NET_WORTH: 'Patrimônio líquido',
}

export const RULE_LABELS: Record<string, string> = {
  ALLOCATIONS: 'Alocações explícitas',
  NET_WORTH: 'Patrimônio líquido',
}

export const SOURCE_LABELS: Record<string, string> = {
  MANUAL: 'Manual',
  ACCOUNT: 'Conta',
  INVESTMENT: 'Investimento',
  MANUAL_ASSET: 'Ativo manual',
}

export const MODE_LABELS: Record<string, string> = {
  FIXED_AMOUNT: 'Valor fixo',
  PERCENTAGE: 'Percentual',
}
