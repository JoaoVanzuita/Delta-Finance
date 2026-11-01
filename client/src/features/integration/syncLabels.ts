import type { Connection, Integration, SyncRun } from './api'

export const connectionStatusLabels: Record<string, string> = {
  INCOMPLETE: 'Configuração incompleta',
  ACTIVE: 'Conectado',
  SYNCING: 'Sincronizando',
  SYNCED: 'Atualizado',
  CREDENTIAL_FAILURE: 'Credenciais recusadas',
  ACTION_REQUIRED: 'Ação necessária',
  AUTHORIZATION_EXPIRED: 'Autorização expirada',
  ITEM_UNAVAILABLE: 'Conexão indisponível',
}

export const syncRunStatusLabels: Record<SyncRun['status'], string> = {
  SUCCESS: 'Concluída',
  EMPTY: 'Sem novidades',
  PARTIAL: 'Parcial',
  AUTH_ERROR: 'Falha de autorização',
  RATE_LIMITED: 'Limite do provedor',
  TIMEOUT: 'Tempo esgotado',
  FAILED: 'Falha',
}

export type SyncTone = 'positive' | 'attention' | 'neutral'

export function connectionTone(status: string): SyncTone {
  if (status === 'SYNCED' || status === 'ACTIVE') return 'positive'
  if (
    status === 'AUTHORIZATION_EXPIRED' ||
    status === 'CREDENTIAL_FAILURE' ||
    status === 'ACTION_REQUIRED' ||
    status === 'ITEM_UNAVAILABLE'
  ) {
    return 'attention'
  }
  return 'neutral'
}

export function syncRunTone(status: string): SyncTone {
  if (status === 'SUCCESS') return 'positive'
  if (status === 'EMPTY') return 'neutral'
  return 'attention'
}

const ACTION_STATUSES = new Set([
  'AUTHORIZATION_EXPIRED',
  'CREDENTIAL_FAILURE',
  'ACTION_REQUIRED',
  'ITEM_UNAVAILABLE',
])

/** Falhas que dependem de uma ação do usuário (reautorizar, revisar credenciais). */
export function requiresUserAction(status: string): boolean {
  return ACTION_STATUSES.has(status)
}

/** Falhas temporárias do provedor: não exigem mudança de credencial. */
const TRANSIENT_ERRORS = new Set(['RATE_LIMITED', 'TIMEOUT', 'PROVIDER_UNAVAILABLE'])

export function isTransientError(code: string | null | undefined): boolean {
  return Boolean(code && TRANSIENT_ERRORS.has(code))
}

const ERROR_MESSAGES: Record<string, string> = {
  USER_AUTHORIZATION_EXPIRED:
    'Renove a autorização anual da aplicação no Meu Pluggy e atualize o itemId.',
  CONSENT_EXPIRED:
    'O consentimento bancário expirou. Reautorize a conexão no Meu Pluggy e atualize o itemId.',
  LOGIN_ERROR: 'Houve erro de login na instituição. Reabra a conexão no Meu Pluggy.',
  CREDENTIAL_FAILURE: 'Client ID ou Client Secret recusados. Revise as credenciais.',
  RATE_LIMITED: 'O provedor limitou requisições; a próxima sincronização tentará novamente.',
  TIMEOUT: 'O provedor demorou para responder; os dados já importados continuam disponíveis.',
  PROVIDER_UNAVAILABLE: 'Provedor temporariamente indisponível; os dados já importados continuam.',
  CONNECTION_UNAVAILABLE: 'A conexão não está disponível; reautorize e atualize o itemId.',
  ITEM_UNAVAILABLE: 'A conexão não está disponível; reautorize e atualize o itemId.',
  IMPORT_FAILED: 'A importação falhou; os dados anteriores foram preservados.',
}

/**
 * Mensagem de usuário para um diagnóstico do provedor.
 *
 * Códigos desconhecidos não viram mensagem principal: o código aparece apenas
 * como detalhe técnico para suporte.
 */
export function errorMessage(code: string | null | undefined): string {
  if (!code) return 'Sem detalhes adicionais.'
  return (
    ERROR_MESSAGES[code] ?? 'Não foi possível concluir a operação. Consulte os detalhes técnicos.'
  )
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return 'não informado'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}

export interface OverallSyncState {
  readonly label: string
  readonly detail: string
  readonly tone: SyncTone
}

/**
 * Estado geral a partir de todas as conexões relevantes, não apenas da primeira
 * integração retornada.
 */
export function overallSyncState(
  integration: Integration | undefined,
  connections: readonly Connection[],
): OverallSyncState {
  if (!integration?.clientIdMasked) {
    return {
      label: 'Configuração incompleta',
      detail: 'Salve as credenciais do Meu Pluggy para começar a importar dados.',
      tone: 'neutral',
    }
  }

  const actionConnection = connections.find((connection) => requiresUserAction(connection.status))
  if (actionConnection) {
    return {
      label: 'Ação necessária',
      detail: `${actionConnection.displayName ?? actionConnection.institutionName ?? 'Uma conexão'} precisa ser reautorizada ou revisada.`,
      tone: 'attention',
    }
  }

  if (connections.some((connection) => connection.status === 'SYNCING')) {
    return {
      label: 'Sincronizando',
      detail: 'Uma consulta ao provedor está em andamento.',
      tone: 'neutral',
    }
  }

  if (integration.status === 'SYNCING') {
    return {
      label: 'Sincronizando',
      detail: 'A sincronização programada está em andamento.',
      tone: 'neutral',
    }
  }

  if (integration.lastErrorCode && isTransientError(integration.lastErrorCode)) {
    return {
      label: 'Atualizado com aviso',
      detail:
        'A última tentativa falhou de forma temporária; os dados anteriores continuam disponíveis.',
      tone: 'attention',
    }
  }

  if (integration.lastSuccessfulSyncAt) {
    return {
      label: 'Atualizado',
      detail: `Última sincronização bem-sucedida em ${formatDateTime(integration.lastSuccessfulSyncAt)}.`,
      tone: 'positive',
    }
  }

  return {
    label: 'Conectado',
    detail: 'Credenciais válidas. Nenhuma sincronização concluída ainda.',
    tone: 'neutral',
  }
}
