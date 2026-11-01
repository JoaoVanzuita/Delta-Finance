import { useState } from 'react'

import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { CircleAlert, Clock3, History, RefreshCw, ShieldCheck } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Drawer } from '@/components/ui/drawer'
import { SectionHeader } from '@/components/ui/page-header'
import { ApiError } from '@/lib/api/problem'

import { financeApi, type Connection, type SyncRun } from '../api'
import {
  connectionStatusLabels,
  errorMessage,
  formatDateTime,
  isTransientError,
  overallSyncState,
  requiresUserAction,
  syncRunStatusLabels,
  syncRunTone,
} from '../syncLabels'

interface SyncStatusPanelProps {
  readonly connections: readonly Connection[]
  readonly integrationConfigured: boolean
}

function toneVariant(tone: 'positive' | 'attention' | 'neutral') {
  if (tone === 'positive') return 'income' as const
  if (tone === 'attention') return 'warning' as const
  return 'neutral' as const
}

function runSummary(run: SyncRun): string {
  const parts: string[] = []
  if (run.readCount !== undefined) parts.push(`${run.readCount} lida(s)`)
  if (run.createdCount !== undefined) parts.push(`${run.createdCount} nova(s)`)
  if (run.updatedCount !== undefined) parts.push(`${run.updatedCount} atualizada(s)`)
  if (run.failedCount) parts.push(`${run.failedCount} com falha`)
  return parts.length > 0 ? parts.join(' · ') : 'Sem contadores informados'
}

/**
 * Estado, ações necessárias e histórico da sincronização.
 *
 * O estado geral considera todas as conexões conhecidas. Falhas temporárias são
 * separadas de autorizações expiradas e credenciais recusadas. Códigos técnicos
 * ficam em detalhes secundários, nunca como mensagem principal.
 */
export function SyncStatusPanel({ connections, integrationConfigured }: SyncStatusPanelProps) {
  const [historyOpen, setHistoryOpen] = useState(false)

  const statusQuery = useQuery({ queryKey: ['syncStatus'], queryFn: financeApi.syncStatus })
  const runsQuery = useInfiniteQuery({
    queryKey: ['syncRuns'],
    queryFn: ({ pageParam }) => financeApi.syncRuns(pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasMore && lastPage.nextCursor ? lastPage.nextCursor : undefined,
  })

  const integrations = statusQuery.data?.integrations ?? []
  const integration = integrations[0]
  const statusConnections = statusQuery.data?.connections ?? connections
  const pendingJobs = statusQuery.data?.pendingJobs ?? 0
  const runs = runsQuery.data?.pages.flatMap((page) => page.items) ?? []
  const recentRuns = runs.slice(0, 5)

  const overall = integrationConfigured
    ? overallSyncState(integration, statusConnections)
    : {
        label: 'Configuração incompleta',
        detail: 'Salve as credenciais do Meu Pluggy para começar a importar dados.',
        tone: 'neutral' as const,
      }

  const actionConnections = statusConnections.filter((connection) =>
    requiresUserAction(connection.status),
  )
  const transient = integration?.lastErrorCode && isTransientError(integration.lastErrorCode)

  return (
    <section aria-labelledby="sync-heading" className="space-y-4">
      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Sincronização"
          description="A sincronização programada lê os dados atualizados pelo provedor. Consultar agora não força a instituição de origem a atualizar e não promete tempo real."
          actions={
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<RefreshCw aria-hidden="true" className="size-4" />}
              onClick={() => {
                void statusQuery.refetch()
                void runsQuery.refetch()
              }}
            >
              Atualizar painel
            </Button>
          }
        />

        <div id="sync-heading" className="sr-only">
          Sincronização
        </div>

        {statusQuery.isLoading ? (
          <p role="status" className="mt-4 text-body-sm text-ink-muted">
            Carregando estado da sincronização…
          </p>
        ) : null}

        {statusQuery.isError ? (
          <Alert variant="danger" className="mt-4">
            {statusQuery.error instanceof ApiError
              ? statusQuery.error.message
              : 'Não foi possível carregar o estado da sincronização.'}
          </Alert>
        ) : null}

        {statusQuery.isSuccess ? (
          <>
            <div className="mt-4 flex flex-wrap items-start justify-between gap-3 rounded-card border border-line bg-surface-muted p-4">
              <div className="flex items-start gap-3">
                <span
                  aria-hidden="true"
                  className="flex size-10 shrink-0 items-center justify-center rounded-control bg-surface text-ink"
                >
                  <ShieldCheck className="size-5" />
                </span>
                <div>
                  <p className="text-body-sm font-semibold text-ink">{overall.label}</p>
                  <p className="mt-0.5 max-w-2xl text-body-sm text-ink-muted">{overall.detail}</p>
                </div>
              </div>
              <Badge variant={toneVariant(overall.tone)}>{overall.label}</Badge>
            </div>

            <dl className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-card border border-line bg-surface p-3">
                <dt className="text-caption font-medium text-ink-muted">Conexões cadastradas</dt>
                <dd className="numeric mt-1 text-body-sm text-ink">{statusConnections.length}</dd>
              </div>
              <div className="rounded-card border border-line bg-surface p-3">
                <dt className="text-caption font-medium text-ink-muted">Última tentativa</dt>
                <dd className="mt-1 text-body-sm text-ink">
                  {formatDateTime(integration?.lastSyncAttemptAt)}
                </dd>
              </div>
              <div className="rounded-card border border-line bg-surface p-3">
                <dt className="text-caption font-medium text-ink-muted">
                  Última sincronização bem-sucedida
                </dt>
                <dd className="mt-1 text-body-sm text-ink">
                  {formatDateTime(integration?.lastSuccessfulSyncAt)}
                </dd>
              </div>
              <div className="rounded-card border border-line bg-surface p-3">
                <dt className="text-caption font-medium text-ink-muted">Consultas na fila</dt>
                <dd className="numeric mt-1 text-body-sm text-ink">{pendingJobs}</dd>
              </div>
            </dl>
          </>
        ) : null}

        {actionConnections.length > 0 ? (
          <Alert variant="warning" title="Ação necessária" className="mt-4" role="status">
            <ul className="list-disc space-y-1 pl-5">
              {actionConnections.map((connection) => (
                <li key={connection.id}>
                  <strong className="text-ink">
                    {connection.displayName ?? connection.institutionName ?? 'Conexão sem nome'}
                  </strong>{' '}
                  — {connectionStatusLabels[connection.status] ?? connection.status}.{' '}
                  {errorMessage(connection.lastErrorCode)}
                </li>
              ))}
            </ul>
            <p className="mt-2">
              Atualize a autorização em “Conexões”. Se o itemId mudou no Meu Pluggy, use “Atualizar
              autorização” na conexão correspondente.
            </p>
          </Alert>
        ) : null}

        {transient && !actionConnections.length ? (
          <Alert
            variant="warning"
            title="Falha temporária do provedor"
            className="mt-4"
            role="status"
          >
            {errorMessage(integration?.lastErrorCode)} Os dados já importados continuam disponíveis.
            Não é necessário trocar credenciais.
          </Alert>
        ) : null}

        {!actionConnections.length && integration?.lastErrorCode && !transient ? (
          <Alert variant="danger" title="Última sincronização falhou" className="mt-4">
            {errorMessage(integration.lastErrorCode)}
            <details className="mt-2">
              <summary className="cursor-pointer text-caption font-medium text-ink-muted">
                Detalhes técnicos
              </summary>
              <p className="mt-1 text-caption text-ink-muted">
                Código de diagnóstico: <code>{integration.lastErrorCode}</code>
              </p>
            </details>
          </Alert>
        ) : null}
      </Card>

      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Histórico de sincronizações"
          description="Cada execução registra o que foi lido, criado e atualizado. Códigos técnicos aparecem apenas nos detalhes."
          actions={
            runs.length > 0 ? (
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<History aria-hidden="true" className="size-4" />}
                onClick={() => setHistoryOpen(true)}
              >
                Ver histórico completo
              </Button>
            ) : null
          }
        />

        {runsQuery.isLoading ? (
          <p role="status" className="mt-3 text-body-sm text-ink-muted">
            Carregando histórico…
          </p>
        ) : null}

        {runsQuery.isError ? (
          <Alert variant="danger" className="mt-3">
            {runsQuery.error instanceof ApiError
              ? runsQuery.error.message
              : 'Não foi possível carregar o histórico.'}
          </Alert>
        ) : null}

        {runsQuery.isSuccess && runs.length === 0 ? (
          <div className="mt-3 flex items-start gap-3 rounded-card border border-dashed border-line-strong bg-surface-muted p-4">
            <Clock3 aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-ink-muted" />
            <div>
              <p className="text-body-sm font-medium text-ink">Nenhuma sincronização registrada</p>
              <p className="mt-0.5 text-body-sm text-ink-muted">
                Assim que a primeira consulta terminar, o resultado aparece aqui.
              </p>
            </div>
          </div>
        ) : null}

        {recentRuns.length > 0 ? (
          <ol className="mt-3 divide-y divide-line rounded-card border border-line">
            {recentRuns.map((run) => (
              <li key={run.id} className="flex flex-col gap-2 p-4 sm:flex-row sm:items-start">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={toneVariant(syncRunTone(run.status))}>
                      {syncRunStatusLabels[run.status] ?? run.status}
                    </Badge>
                    <span className="text-body-sm text-ink">{formatDateTime(run.startedAt)}</span>
                  </div>
                  <p className="mt-1 text-caption text-ink-muted">{runSummary(run)}</p>
                  {run.errorCode ? (
                    <p className="mt-1 text-body-sm text-ink-muted">
                      {errorMessage(run.errorCode)}
                    </p>
                  ) : null}
                  {run.errorCode ? (
                    <details className="mt-1">
                      <summary className="cursor-pointer text-caption text-ink-muted">
                        Detalhes técnicos
                      </summary>
                      <p className="mt-1 text-caption text-ink-muted">
                        Código: <code>{run.errorCode}</code>
                      </p>
                    </details>
                  ) : null}
                </div>
                <p className="text-caption text-ink-muted">
                  Concluída em {formatDateTime(run.finishedAt)}
                </p>
              </li>
            ))}
          </ol>
        ) : null}
      </Card>

      <Drawer
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        title="Histórico de sincronizações"
        description="Execuções ordenadas da mais recente para a mais antiga."
        width="lg"
      >
        {runs.length === 0 ? (
          <p className="text-body-sm text-ink-muted">Nenhuma execução registrada.</p>
        ) : (
          <ol className="space-y-3">
            {runs.map((run) => (
              <li key={run.id} className="rounded-card border border-line bg-surface p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <Badge variant={toneVariant(syncRunTone(run.status))}>
                    {syncRunStatusLabels[run.status] ?? run.status}
                  </Badge>
                  <span className="text-caption text-ink-muted">
                    {formatDateTime(run.startedAt)}
                  </span>
                </div>
                <p className="mt-2 text-body-sm text-ink">{runSummary(run)}</p>
                {run.errorCode ? (
                  <p className="mt-1 text-body-sm text-ink-muted">{errorMessage(run.errorCode)}</p>
                ) : null}
                <details className="mt-2">
                  <summary className="cursor-pointer text-caption font-medium text-ink-muted">
                    Detalhes técnicos
                  </summary>
                  <dl className="mt-2 grid gap-1 text-caption text-ink-muted sm:grid-cols-2">
                    <div>
                      <dt className="inline font-medium text-ink">Identificador: </dt>
                      <dd className="inline">
                        <code>{run.id}</code>
                      </dd>
                    </div>
                    <div>
                      <dt className="inline font-medium text-ink">Concluída em: </dt>
                      <dd className="inline">{formatDateTime(run.finishedAt)}</dd>
                    </div>
                    <div>
                      <dt className="inline font-medium text-ink">Ignoradas: </dt>
                      <dd className="inline">{run.skippedCount ?? 0}</dd>
                    </div>
                    <div>
                      <dt className="inline font-medium text-ink">Falhas: </dt>
                      <dd className="inline">{run.failedCount ?? 0}</dd>
                    </div>
                    {run.errorCode ? (
                      <div className="sm:col-span-2">
                        <dt className="inline font-medium text-ink">Código: </dt>
                        <dd className="inline">
                          <code>{run.errorCode}</code>
                        </dd>
                      </div>
                    ) : null}
                  </dl>
                </details>
              </li>
            ))}
          </ol>
        )}

        {runsQuery.hasNextPage ? (
          <Button
            variant="secondary"
            className="mt-4"
            loading={runsQuery.isFetchingNextPage}
            loadingLabel="Carregando…"
            onClick={() => void runsQuery.fetchNextPage()}
          >
            Carregar mais
          </Button>
        ) : null}

        {runsQuery.isFetching && !runsQuery.isFetchingNextPage ? (
          <p role="status" className="mt-3 text-caption text-ink-muted">
            Atualizando histórico…
          </p>
        ) : null}

        {runs.length === 0 && runsQuery.isError ? (
          <Alert variant="danger" className="mt-3">
            <CircleAlert aria-hidden="true" className="size-4" />
            Não foi possível carregar o histórico.
          </Alert>
        ) : null}
      </Drawer>
    </section>
  )
}
