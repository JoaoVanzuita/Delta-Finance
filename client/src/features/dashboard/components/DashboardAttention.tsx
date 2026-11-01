import { Link } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button-variants'
import type { DashboardSummary } from '@/features/dashboard/api'
import type { Connection, SyncStatus } from '@/features/integration/api'
import { connectionStatusLabels } from '@/features/integration/syncLabels'
import { formatDateTime, formatMoney } from '@/lib/format'
import { cn } from '@/lib/cn'

interface DashboardAttentionProps {
  readonly summary: DashboardSummary
  readonly syncStatus?: SyncStatus
  readonly syncError: boolean
  readonly syncLoading: boolean
  readonly onRetrySync: () => void
}

const ACTION_STATUSES = new Set([
  'AUTHORIZATION_EXPIRED',
  'CREDENTIAL_FAILURE',
  'ACTION_REQUIRED',
  'ITEM_UNAVAILABLE',
])

const TEMPORARY_CODES = new Set([
  'RATE_LIMITED',
  'TIMEOUT',
  'PROVIDER_UNAVAILABLE',
  'IMPORT_FAILED',
])

const ACTION_MESSAGES: Record<string, string> = {
  USER_AUTHORIZATION_EXPIRED: 'Renove a autorização anual no Meu Pluggy.',
  CONSENT_EXPIRED: 'O consentimento bancário expirou; renove a autorização no Meu Pluggy.',
  CREDENTIAL_FAILURE: 'Revise o Client ID e o Client Secret da integração.',
  LOGIN_ERROR: 'A instituição recusou o acesso; reabra a conexão no Meu Pluggy.',
  CONNECTION_UNAVAILABLE: 'A conexão não está disponível; reconecte a instituição.',
  ITEM_UNAVAILABLE: 'A conexão não está disponível; reconecte a instituição.',
}

const DEFAULT_ACTION_MESSAGE = 'A conexão precisa ser revista para voltar a sincronizar.'

function actionMessage(connection: Connection): string {
  if (connection.lastErrorCode && ACTION_MESSAGES[connection.lastErrorCode]) {
    return ACTION_MESSAGES[connection.lastErrorCode] ?? DEFAULT_ACTION_MESSAGE
  }
  return DEFAULT_ACTION_MESSAGE
}

function latestTimestamp(values: ReadonlyArray<string | null | undefined>): string | undefined {
  let latest: string | undefined
  let latestValue = -Infinity
  for (const value of values) {
    if (!value) continue
    const parsed = Date.parse(value)
    if (!Number.isFinite(parsed) || parsed <= latestValue) continue
    latestValue = parsed
    latest = value
  }
  return latest
}

/**
 * Pendências, reconciliações e saúde da sincronização.
 *
 * O estado geral considera todas as conexões retornadas por `/sync/status`, não
 * apenas a primeira integração. Falha temporária não recebe o mesmo tratamento
 * de autorização expirada ou credencial inválida.
 */
export function DashboardAttention({
  summary,
  syncStatus,
  syncError,
  syncLoading,
  onRetrySync,
}: DashboardAttentionProps) {
  const connections = syncStatus?.connections ?? []
  const integrations = syncStatus?.integrations ?? []
  const actionConnections = connections.filter((connection) =>
    ACTION_STATUSES.has(connection.status),
  )
  const temporaryFailures = connections.filter(
    (connection) =>
      !ACTION_STATUSES.has(connection.status) &&
      Boolean(connection.lastErrorCode && TEMPORARY_CODES.has(connection.lastErrorCode)),
  )
  const integrationsIncomplete =
    !syncLoading &&
    (integrations.length === 0 && connections.length === 0
      ? true
      : integrations.some((integration) => integration.status === 'INCOMPLETE'))
  const syncing =
    connections.some((connection) => connection.status === 'SYNCING') ||
    integrations.some((integration) => integration.status === 'SYNCING')
  const pendingJobs = syncStatus?.pendingJobs ?? 0
  const lastSync = latestTimestamp([
    ...connections.map((connection) => connection.lastSyncAt),
    ...integrations.map((integration) => integration.lastSuccessfulSyncAt),
    summary.lastSuccessfulSyncAt,
  ])

  const pendingCount = summary.pendingCount ?? 0
  const pendingIncome = summary.pendingIncome ?? '0.00'
  const pendingExpenses = summary.pendingExpenses ?? '0.00'
  const matches = summary.pendingInternalMatches ?? 0
  const hasPending = pendingCount > 0 || matches > 0
  const hasSyncAction = actionConnections.length > 0 || integrationsIncomplete
  const needsAttention = hasPending || hasSyncAction || syncLoading || syncError

  return (
    <section aria-labelledby="attention-heading" className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="attention-heading" className="text-section-title font-semibold text-ink">
            Precisa de atenção
          </h2>
          <p className="text-caption text-ink-muted">
            Pendências não entram nos totais até serem efetivadas.
          </p>
        </div>
        <Link
          to="/transactions"
          className="text-body-sm font-semibold text-action underline underline-offset-4 hover:text-action-hover"
        >
          Abrir transações
        </Link>
      </div>

      {needsAttention ? (
        <div className="grid items-start gap-4 lg:grid-cols-2">
          <div className="space-y-3">
            {pendingCount > 0 ? (
              <div className="rounded-card border border-warning/40 bg-warning-soft p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-body-sm font-semibold text-ink">
                      Transações pendentes
                      <Badge variant="warning" className="ml-2 align-middle">
                        {pendingCount}
                      </Badge>
                    </p>
                    <p className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-caption text-ink-muted">
                      <span>
                        A receber:{' '}
                        <strong className="numeric font-semibold text-income">
                          {formatMoney(pendingIncome, 'BRL')}
                        </strong>
                      </span>
                      <span>
                        A pagar:{' '}
                        <strong className="numeric font-semibold text-expense">
                          {formatMoney(pendingExpenses, 'BRL')}
                        </strong>
                      </span>
                    </p>
                  </div>
                  <Link
                    to="/transactions"
                    className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }))}
                  >
                    Revisar
                  </Link>
                </div>
              </div>
            ) : null}

            {matches > 0 ? (
              <div className="rounded-card border border-info/40 bg-info-soft p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-body-sm font-semibold text-ink">
                      Movimentos internos aguardando confirmação
                      <Badge variant="info" className="ml-2 align-middle">
                        {matches}
                      </Badge>
                    </p>
                    <p className="mt-1 text-caption text-ink-muted">
                      Transferências entre contas próprias e pagamentos de fatura.
                    </p>
                  </div>
                  <Link
                    to="/reconciliation"
                    className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }))}
                  >
                    Revisar
                  </Link>
                </div>
              </div>
            ) : null}

            {!hasPending ? (
              <p className="rounded-card border border-income/35 bg-income-soft p-4 text-body-sm text-ink">
                Nenhuma transação pendente ou reconciliação aberta no período.
              </p>
            ) : null}
          </div>

          <div className="rounded-card border border-line bg-surface p-4">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <p className="text-body-sm font-semibold text-ink">Sincronização</p>
                <p className="mt-0.5 text-caption text-ink-muted">
                  {lastSync ? `Mais recente em ${formatDateTime(lastSync)}` : 'Sem registro'}
                </p>
              </div>
              {syncLoading ? (
                <Badge variant="neutral">Consultando</Badge>
              ) : syncing ? (
                <Badge variant="info">Sincronizando</Badge>
              ) : hasSyncAction ? (
                <Badge variant="warning">Ação necessária</Badge>
              ) : (
                <Badge variant="income">Em dia</Badge>
              )}
            </div>

            {syncError ? (
              <Alert
                variant="warning"
                className="mt-3"
                title="Estado da sincronização indisponível"
              >
                Não foi possível consultar as conexões agora. Os dados já importados continuam
                disponíveis.{' '}
                <button
                  type="button"
                  onClick={onRetrySync}
                  className="font-semibold text-action underline underline-offset-4"
                >
                  Tentar novamente
                </button>
              </Alert>
            ) : null}

            {actionConnections.length > 0 && !syncError ? (
              <ul className="mt-3 space-y-2" aria-label="Conexões com ação necessária">
                {actionConnections.slice(0, 3).map((connection) => (
                  <li
                    key={connection.id}
                    className="rounded-control border border-warning/35 bg-warning-soft p-3"
                  >
                    <p className="text-body-sm font-semibold text-ink">
                      {connection.institutionName ?? connection.displayName ?? 'Conexão'}
                    </p>
                    <p className="text-caption text-ink-muted">
                      {connectionStatusLabels[connection.status] ?? 'Requer atenção'} —{' '}
                      {actionMessage(connection)}
                    </p>
                  </li>
                ))}
                {actionConnections.length > 3 ? (
                  <li className="text-caption text-ink-muted">
                    Mais {actionConnections.length - 3} conexão(ões) com ação necessária.
                  </li>
                ) : null}
              </ul>
            ) : null}

            {integrationsIncomplete && !syncError ? (
              <p className="mt-3 text-body-sm text-ink">
                Configure a integração para importar dados bancários.
              </p>
            ) : null}

            {temporaryFailures.length > 0 && !syncError && actionConnections.length === 0 ? (
              <Alert variant="info" className="mt-3" title="Tentativa temporária sem sucesso">
                O provedor não respondeu na última tentativa. Os dados já importados continuam
                disponíveis e uma nova sincronização será tentada.
              </Alert>
            ) : null}

            {pendingJobs > 0 && !syncError ? (
              <p className="mt-3 text-caption text-ink-muted">
                {pendingJobs} consulta(s) na fila de sincronização.
              </p>
            ) : null}

            {hasSyncAction || integrationsIncomplete ? (
              <Link
                to="/settings/integration"
                className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }), 'mt-3')}
              >
                Revisar integração
              </Link>
            ) : null}
          </div>
        </div>
      ) : (
        <Alert variant="success" title="Nenhuma pendência crítica">
          Totais oficiais em dia. A sincronização mais recente entre as conexões foi{' '}
          {lastSync ? formatDateTime(lastSync) : 'ainda não registrada'}.
        </Alert>
      )}
    </section>
  )
}
