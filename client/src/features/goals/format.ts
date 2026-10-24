/** Formatação e rótulos de metas; valores autoritativos vêm do servidor. */

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

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return 'Sem prazo'
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
  DEBT_REDUCTION: 'Redução de dívida',
  NET_WORTH: 'Patrimônio líquido',
}

export const RULE_LABELS: Record<string, string> = {
  ALLOCATIONS: 'Alocações explícitas',
  NET_WORTH: 'Patrimônio líquido',
  DEBT_REDUCTION: 'Redução de dívida',
}

export const SOURCE_LABELS: Record<string, string> = {
  MANUAL: 'Manual',
  ACCOUNT: 'Conta',
  INVESTMENT: 'Investimento',
  MANUAL_ASSET: 'Ativo manual',
  MANUAL_LIABILITY: 'Passivo manual',
}

export const MODE_LABELS: Record<string, string> = {
  FIXED_AMOUNT: 'Valor fixo',
  PERCENTAGE: 'Percentual',
}
