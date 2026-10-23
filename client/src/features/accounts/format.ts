/** Formatação de apresentação; valores autoritativos continuam vindo do servidor. */
export function formatMoney(value: string | null | undefined, currency: string): string {
  if (value == null || value === '') {
    return 'Não informado'
  }
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return `${value} ${currency}`
  }
  try {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency,
    }).format(number)
  } catch {
    return `${value} ${currency}`
  }
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return 'Não informado'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 'Não informado'
  }
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}
