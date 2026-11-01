/** Formatação de apresentação do patrimônio; valores autoritativos vêm do servidor. */
import { formatDate as formatDateShared, formatMoney as formatMoneyShared } from '@/lib/format'

export function formatMoney(value: string | null | undefined): string {
  return formatMoneyShared(value, 'BRL')
}

/** Datas de referência são dias civis; não sofrem deslocamento de fuso. */
export function formatDate(value: string | null | undefined): string {
  return formatDateShared(value, { fallback: 'Não informada' })
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
