import type { SyncRun } from './api'

export const connectionStatusLabels: Record<string, string> = {
  INCOMPLETE: 'Configuração incompleta',
  ACTIVE: 'Ativa',
  SYNCING: 'Sincronizando',
  SYNCED: 'Sincronizada',
  CREDENTIAL_FAILURE: 'Falha de credenciais',
  ACTION_REQUIRED: 'Requer ação',
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

const ERROR_MESSAGES: Record<string, string> = {
  USER_AUTHORIZATION_EXPIRED:
    'Renove a autorização anual da aplicação no Meu Pluggy e atualize o itemId.',
  CONSENT_EXPIRED:
    'O consentimento bancário expirou. Reautorize a conexão no Meu Pluggy e atualize o itemId.',
  LOGIN_ERROR: 'Houve erro de login na instituição. Reabra a conexão no Meu Pluggy.',
  CREDENTIAL_FAILURE: 'Client ID ou Client Secret recusados. Revise as credenciais.',
  RATE_LIMITED: 'O provedor limitou requisições; a tentativa será repetida.',
  TIMEOUT: 'O provedor demorou para responder; os dados já importados continuam disponíveis.',
  PROVIDER_UNAVAILABLE: 'Provedor indisponível; os dados já importados continuam disponíveis.',
  CONNECTION_UNAVAILABLE: 'A conexão não está disponível; reautorize e atualize o itemId.',
  ITEM_UNAVAILABLE: 'A conexão não está disponível; reautorize e atualize o itemId.',
  IMPORT_FAILED: 'A importação falhou; os dados anteriores foram preservados.',
}

export function errorMessage(code: string | null | undefined): string {
  if (!code) return 'Sem detalhes adicionais.'
  return ERROR_MESSAGES[code] ?? `Código ${code}.`
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
