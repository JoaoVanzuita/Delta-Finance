import { useQuery } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type Connection } from '../api'
import {
  connectionStatusLabels,
  errorMessage,
  formatDateTime,
  syncRunStatusLabels,
} from '../syncLabels'

const ACTION_STATUSES = new Set([
  'AUTHORIZATION_EXPIRED',
  'CREDENTIAL_FAILURE',
  'ACTION_REQUIRED',
  'ITEM_UNAVAILABLE',
])

interface SyncStatusPanelProps {
  readonly connections: readonly Connection[]
}

/**
 * Painel de estado e histórico da sincronização.
 *
 * Deixa explícito que consultar os dados disponíveis não força a instituição de origem a atualizar
 * e que a sincronização programada é a reconciliação principal.
 */
export function SyncStatusPanel({ connections }: SyncStatusPanelProps) {
  const statusQuery = useQuery({ queryKey: ['syncStatus'], queryFn: financeApi.syncStatus })
  const runsQuery = useQuery({ queryKey: ['syncRuns'], queryFn: () => financeApi.syncRuns() })

  const integration = statusQuery.data?.integrations?.[0]
  const pendingJobs = statusQuery.data?.pendingJobs ?? 0
  const runs = runsQuery.data?.items ?? []
  const actionRequired = connections.filter((connection) => ACTION_STATUSES.has(connection.status))

  return (
    <section
      aria-labelledby="sync-heading"
      aria-live="polite"
      className="space-y-4 rounded-card border border-line bg-surface p-5 sm:p-6"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="sync-heading" className="text-lg font-semibold text-ink">
            Sincronização
          </h2>
          <p className="mt-1 max-w-2xl text-sm text-ink-muted">
            A sincronização programada lê os dados atualizados pelo provedor. Consultar agora lê o
            que já está disponível, mas não força a instituição de origem a atualizar e não promete
            tempo real.
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            void statusQuery.refetch()
            void runsQuery.refetch()
          }}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted"
        >
          Atualizar painel
        </button>
      </div>

      {statusQuery.isLoading ? (
        <p role="status" className="text-sm text-ink-muted">
          Carregando estado da sincronização…
        </p>
      ) : null}

      {statusQuery.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-3 text-sm text-ink"
        >
          {statusQuery.error instanceof ApiError
            ? statusQuery.error.message
            : 'Não foi possível carregar o estado da sincronização.'}
        </p>
      ) : null}

      {statusQuery.isSuccess ? (
        <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs font-medium text-ink-muted">Estado</dt>
            <dd className="mt-1 text-sm font-semibold text-ink">
              {integration
                ? (connectionStatusLabels[integration.status] ?? integration.status)
                : 'Sem integração'}
            </dd>
          </div>
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs font-medium text-ink-muted">Última tentativa</dt>
            <dd className="mt-1 text-sm text-ink">
              {formatDateTime(integration?.lastSyncAttemptAt)}
            </dd>
          </div>
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs font-medium text-ink-muted">
              Última sincronização bem-sucedida
            </dt>
            <dd className="mt-1 text-sm text-ink">
              {formatDateTime(integration?.lastSuccessfulSyncAt)}
            </dd>
          </div>
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs font-medium text-ink-muted">Consultas na fila</dt>
            <dd className="numeric mt-1 text-sm text-ink">{pendingJobs}</dd>
          </div>
        </dl>
      ) : null}

      {integration?.lastErrorCode ? (
        <p className="rounded-card border border-warning/40 bg-warning/5 p-3 text-sm text-ink">
          {errorMessage(integration.lastErrorCode)}
        </p>
      ) : null}

      {actionRequired.length > 0 ? (
        <div
          role="status"
          className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-4"
        >
          <p className="text-sm font-semibold text-ink">Ação necessária</p>
          <ul className="list-disc space-y-1 pl-5 text-sm text-ink">
            {actionRequired.map((connection) => (
              <li key={connection.id}>
                {connection.institutionName ?? 'Conexão'} —{' '}
                {connectionStatusLabels[connection.status] ?? connection.status}.{' '}
                {errorMessage(connection.lastErrorCode)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <div>
        <h3 className="text-sm font-semibold text-ink">Histórico recente</h3>
        {runsQuery.isLoading ? (
          <p role="status" className="mt-2 text-sm text-ink-muted">
            Carregando histórico…
          </p>
        ) : null}
        {runsQuery.isError ? (
          <p role="alert" className="mt-2 text-sm text-expense">
            {runsQuery.error instanceof ApiError
              ? runsQuery.error.message
              : 'Não foi possível carregar o histórico.'}
          </p>
        ) : null}
        {runsQuery.isSuccess && runs.length === 0 ? (
          <p className="mt-2 text-sm text-ink-muted">Nenhuma sincronização registrada ainda.</p>
        ) : null}
        {runs.length > 0 ? (
          <ol className="mt-2 divide-y divide-line rounded-card border border-line">
            {runs.slice(0, 5).map((run) => (
              <li
                key={run.id}
                className="flex flex-col gap-1 p-3 text-sm sm:flex-row sm:items-center sm:justify-between"
              >
                <div>
                  <p className="font-medium text-ink">
                    {syncRunStatusLabels[run.status] ?? run.status}
                  </p>
                  <p className="text-xs text-ink-muted">
                    {formatDateTime(run.startedAt)} · {run.readCount ?? 0} lida(s) ·{' '}
                    {run.createdCount ?? 0} nova(s) · {run.updatedCount ?? 0} atualizada(s)
                  </p>
                </div>
                {run.errorCode ? (
                  <p className="max-w-sm text-xs text-ink-muted">{errorMessage(run.errorCode)}</p>
                ) : null}
              </li>
            ))}
          </ol>
        ) : null}
      </div>
    </section>
  )
}
