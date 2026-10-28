/** Formatação de apresentação do patrimônio; valores autoritativos vêm do servidor. */

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

/** Datas de referência são dias civis; não sofrem deslocamento de fuso. */
export function formatDate(value: string | null | undefined): string {
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

export const COMPLETENESS_LABELS: Record<string, string> = {
  COMPLETE: 'Completo',
  PARTIAL: 'Parcial',
  ESTIMATED: 'Estimado',
}

export const SOURCE_LABELS: Record<string, string> = {
  ACCOUNT: 'Conta',
  CARD: 'Cartão',
  INVESTMENT: 'Investimento',
  MANUAL_ASSET: 'Ativo manual',
  MANUAL_LIABILITY: 'Passivo manual',
}

export const ORIGIN_LABELS: Record<string, string> = {
  PROVIDER: 'Importado',
  IMPORTED: 'Importado',
  MANUAL: 'Manual',
}
