/** Formatação local do dashboard; valores e datas autoritativos vêm do servidor. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return 'Data não informada'
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return 'Sem sincronização registrada'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}

export function formatPeriodLabel(start: string, end: string): string {
  return `${formatDate(start)} a ${formatDate(end)}`
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
