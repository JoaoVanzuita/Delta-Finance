/** Rótulos de apresentação do módulo de investimentos. */

export const ASSET_CLASS_LABELS: Record<string, string> = {
  FII: 'Fundos imobiliários',
  ACAO: 'Ações',
  BDR: 'BDRs',
  DERIVATIVO: 'Derivativos',
  RENDA_FIXA: 'Renda fixa',
  FUNDO: 'Fundos',
  ETF: 'ETFs',
  TITULO: 'Títulos e previdência',
  COE: 'COE',
  OUTRO: 'Outros',
}

export const CLASSIFICATION_SOURCE_LABELS: Record<string, string> = {
  PROVIDER: 'Classificação do provedor',
  CODE_HEURISTIC: 'Classificação por código',
  USER: 'Classificação informada por você',
}

export const HOLDING_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativa',
  CLOSED: 'Encerrada',
  PENDING: 'Pendente',
  UNKNOWN: 'Não informada',
}

export const SCOPE_LABELS: Record<string, string> = {
  AVAILABLE: 'Compartilha investimentos',
  NOT_SHARED: 'Não compartilha investimentos',
  PENDING_AUTHORIZATION: 'Autorização pendente',
  UNKNOWN: 'Ainda não verificado',
  ERROR: 'Falha ao verificar',
}

export const SCOPE_HINTS: Record<string, string> = {
  AVAILABLE: 'A instituição declarou recursos de investimento para este consentimento.',
  NOT_SHARED:
    'A instituição não compartilhou investimentos neste consentimento; nenhuma posição foi inventada.',
  PENDING_AUTHORIZATION:
    'Autorize o compartilhamento de investimentos no aplicativo da instituição e sincronize novamente.',
  UNKNOWN: 'A lista de recursos ainda não foi lida. Sincronize para verificar a cobertura.',
  ERROR: 'Não foi possível ler a cobertura desta conexão. Os demais dados continuam disponíveis.',
}

export function assetClassLabel(value: string | null | undefined): string {
  if (!value) return 'Outros'
  return ASSET_CLASS_LABELS[value] ?? value
}

export function positionCountLabel(count: number): string {
  return count === 1 ? '1 posição' : `${count} posições`
}

export function assetClassCountLabel(count: number): string {
  return count === 1 ? '1 classe' : `${count} classes`
}
