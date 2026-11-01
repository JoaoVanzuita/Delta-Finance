/** Formatação local do dashboard; valores e datas autoritativos vêm do servidor. */
import {
  formatDate as formatDateShared,
  formatDateTime as formatDateTimeShared,
} from '@/lib/format'

export function formatDate(value: string | null | undefined): string {
  return formatDateShared(value, { fallback: 'Data não informada' })
}

export function formatDateTime(value: string | null | undefined): string {
  return formatDateTimeShared(value, { fallback: 'Sem sincronização registrada' })
}

export function formatPeriodLabel(start: string, end: string): string {
  return `${formatDate(start)} a ${formatDate(end)}`
}

/**
 * Categorias que existem nos dois sentidos (entradas e saídas) recebem um
 * rótulo específico no gráfico para não parecer que o valor do outro sentido
 * está sendo somado. O nome canônico continua sendo usado para ícone/cor.
 */
export function categoryDisplayName(name: string, direction: 'EXPENSE' | 'INCOME'): string {
  if (name === 'Outros') {
    return direction === 'INCOME' ? 'Outras receitas' : 'Outros gastos'
  }
  if (name === 'Transferências externas') {
    return direction === 'INCOME'
      ? 'Transferências externas (entradas)'
      : 'Transferências externas (saídas)'
  }
  if (name === 'Sem categoria') {
    return direction === 'INCOME' ? 'Sem categoria (receitas)' : 'Sem categoria (despesas)'
  }
  return name
}

export function isStale(value: string | null | undefined, now = Date.now()): boolean {
  if (!value) return true
  const reference = new Date(value).getTime()
  if (Number.isNaN(reference)) return true
  return now - reference > 24 * 60 * 60 * 1000
}

export function formatPercentage(amount: string, total: number): number {
  if (total <= 0) return 0
  const value = Number(amount)
  if (!Number.isFinite(value)) return 0
  return Math.max(0, Math.min(100, Math.round((Math.abs(value) / total) * 100)))
}
