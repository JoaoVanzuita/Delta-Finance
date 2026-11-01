import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, Settings2, Trash2, Unplug } from 'lucide-react'

import { DisplayNameEditor } from '@/components/DisplayNameEditor'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api/problem'
import { invalidateIntegrationViews } from '@/lib/invalidation'

import { financeApi, type Connection } from '../api'
import {
  connectionStatusLabels,
  errorMessage,
  formatDateTime,
  requiresUserAction,
} from '../syncLabels'

interface ConnectionsListProps {
  readonly connections: readonly Connection[]
  readonly configured: boolean
  readonly connectionLimit?: number
}

function statusVariant(connection: Connection) {
  if (requiresUserAction(connection.status)) return 'warning' as const
  if (connection.status === 'SYNCED' || connection.status === 'ACTIVE') return 'income' as const
  return 'neutral' as const
}

/**
 * Conexões bancárias (itens Pluggy).
 *
 * Ações seguras (sincronizar, atualizar autorização, renomear) ficam separadas
 * da remoção. Remover abre uma confirmação com duas consequências explícitas:
 * desativar preservando histórico ou excluir os dados importados.
 */
export function ConnectionsList({
  connections,
  configured,
  connectionLimit,
}: ConnectionsListProps) {
  const queryClient = useQueryClient()
  const [editingId, setEditingId] = useState<string | null>(null)
  const [newItemId, setNewItemId] = useState('')
  const [removingId, setRemovingId] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const removing = connections.find((connection) => connection.id === removingId) ?? null

  const refresh = () => {
    void invalidateIntegrationViews(queryClient)
  }

  const sync = useMutation({
    mutationFn: (id: string) => financeApi.syncConnection(id),
    onSuccess: () => {
      setError(null)
      setMessage('Consulta enfileirada. O resultado aparece no histórico em instantes.')
      refresh()
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível enfileirar a consulta.',
      )
    },
  })

  const update = useMutation({
    mutationFn: (input: { id: string; itemId: string }) =>
      financeApi.updateConnection(input.id, input.itemId),
    onSuccess: () => {
      setEditingId(null)
      setNewItemId('')
      setError(null)
      setMessage('Autorização validada. O histórico foi reconciliado sem duplicar registros.')
      refresh()
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Não foi possível validar a nova autorização.',
      )
    },
  })

  const remove = useMutation({
    mutationFn: (input: { id: string; deleteHistory: boolean }) =>
      financeApi.removeConnection(input.id, input.deleteHistory),
    onSuccess: (_result, input) => {
      setRemovingId(null)
      setError(null)
      setMessage(
        input.deleteHistory
          ? 'Conexão e dados importados removidos. Contas manuais e categorias foram preservadas.'
          : 'Conexão desativada. O histórico importado foi preservado e saiu dos saldos correntes.',
      )
      refresh()
    },
    onError: (caught) => {
      setMessage(null)
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível remover a conexão.')
    },
  })

  const busy = sync.isPending || update.isPending || remove.isPending

  async function renameConnection(id: string, displayName: string) {
    const updated = await financeApi.renameConnection(id, displayName)
    refresh()
    return updated
  }

  async function resetConnectionName(id: string) {
    const updated = await financeApi.resetConnectionName(id)
    refresh()
    return updated
  }

  return (
    <section aria-labelledby="connections-heading" className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="connections-heading" className="text-section-title font-semibold text-ink">
            Conexões
          </h2>
          <p className="mt-1 max-w-2xl text-body-sm text-ink-muted">
            Cada conexão corresponde a uma autorização bancária. Desativar interrompe novas
            sincronizações e preserva o histórico; excluir remove também as contas e transações
            importadas por esta conexão.
          </p>
        </div>
        <Badge variant={configured ? 'neutral' : 'warning'}>
          {connectionLimit !== undefined
            ? `${connections.length} de ${connectionLimit} conexões`
            : `${connections.length} conexão(ões)`}
        </Badge>
      </div>

      {message ? (
        <Alert variant="success" role="status">
          {message}
        </Alert>
      ) : null}
      {error ? (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      ) : null}

      {!configured ? (
        <Alert variant="neutral">Salve credenciais válidas para adicionar conexões.</Alert>
      ) : null}

      {configured && connections.length === 0 ? (
        <div className="rounded-card border border-dashed border-line-strong bg-surface p-6">
          <p className="text-body-sm font-medium text-ink">Nenhuma conexão configurada</p>
          <p className="mt-1 max-w-prose text-body-sm text-ink-muted">
            Adicione o itemId de uma autorização criada no Meu Pluggy para começar a importar contas
            e transações.
          </p>
        </div>
      ) : null}

      {connections.length > 0 ? (
        <ul className="grid gap-4 lg:grid-cols-2">
          {connections.map((connection) => {
            const editing = editingId === connection.id
            const actionRequired = requiresUserAction(connection.status)
            return (
              <li key={connection.id}>
                <Card className="flex h-full flex-col p-5">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-body font-semibold text-ink">
                        {connection.displayName ??
                          connection.institutionName ??
                          'Instituição não informada'}
                      </p>
                      <p className="mt-0.5 truncate text-caption text-ink-muted">
                        {connection.nameCustomized && connection.institutionName
                          ? `Instituição no Pluggy: ${connection.institutionName}`
                          : `itemId ${connection.itemIdMasked ?? 'não disponível'}`}
                      </p>
                    </div>
                    <Badge variant={statusVariant(connection)}>
                      {connectionStatusLabels[connection.status] ?? connection.status}
                    </Badge>
                  </div>

                  <dl className="mt-4 grid grid-cols-2 gap-3 text-body-sm">
                    <div>
                      <dt className="text-caption text-ink-muted">Última leitura</dt>
                      <dd className="mt-0.5 text-ink">{formatDateTime(connection.lastSyncAt)}</dd>
                    </div>
                    <div>
                      <dt className="text-caption text-ink-muted">Contas</dt>
                      <dd className="numeric mt-0.5 text-ink">{connection.accountCount}</dd>
                    </div>
                    {connection.consentExpiresAt ? (
                      <div className="col-span-2">
                        <dt className="text-caption text-ink-muted">Consentimento até</dt>
                        <dd className="mt-0.5 text-ink">
                          {formatDateTime(connection.consentExpiresAt)}
                        </dd>
                      </div>
                    ) : null}
                  </dl>

                  {connection.lastErrorCode ? (
                    <Alert variant={actionRequired ? 'warning' : 'info'} className="mt-4">
                      {errorMessage(connection.lastErrorCode)}
                    </Alert>
                  ) : null}

                  <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-line pt-4">
                    <Button
                      size="sm"
                      leadingIcon={<RefreshCw aria-hidden="true" className="size-4" />}
                      disabled={busy}
                      loading={sync.isPending && sync.variables === connection.id}
                      loadingLabel="Enfileirando…"
                      onClick={() => {
                        setError(null)
                        setMessage(null)
                        sync.mutate(connection.id)
                      }}
                    >
                      Sincronizar agora
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      leadingIcon={<Settings2 aria-hidden="true" className="size-4" />}
                      disabled={busy}
                      onClick={() => {
                        setError(null)
                        setMessage(null)
                        setEditingId(editing ? null : connection.id)
                        setNewItemId('')
                      }}
                    >
                      {editing ? 'Cancelar ajuste' : 'Atualizar autorização'}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="ml-auto text-expense hover:bg-expense-soft"
                      leadingIcon={<Trash2 aria-hidden="true" className="size-4" />}
                      disabled={busy}
                      onClick={() => {
                        setError(null)
                        setMessage(null)
                        setRemovingId(connection.id)
                      }}
                    >
                      Remover
                    </Button>
                  </div>

                  <div className="mt-3">
                    <DisplayNameEditor
                      currentName={connection.displayName ?? null}
                      originalName={connection.institutionName ?? null}
                      customized={Boolean(connection.nameCustomized)}
                      disabled={busy}
                      onRename={(displayName) => renameConnection(connection.id, displayName)}
                      onReset={() => resetConnectionName(connection.id)}
                    />
                  </div>

                  {editing ? (
                    <form
                      noValidate
                      className="mt-3 space-y-2 rounded-card border border-line bg-surface-muted p-3"
                      onSubmit={(event) => {
                        event.preventDefault()
                        if (!newItemId.trim()) {
                          setError('Informe o novo itemId.')
                          return
                        }
                        setError(null)
                        update.mutate({ id: connection.id, itemId: newItemId.trim() })
                      }}
                    >
                      <label
                        htmlFor={`new-item-${connection.id}`}
                        className="block text-body-sm font-medium text-ink"
                      >
                        Novo itemId
                      </label>
                      <p className="text-caption text-ink-muted">
                        Use o itemId da nova autorização no Meu Pluggy. O servidor valida antes de
                        reconciliar o histórico existente.
                      </p>
                      <Input
                        id={`new-item-${connection.id}`}
                        value={newItemId}
                        onChange={(event) => setNewItemId(event.target.value)}
                        autoComplete="off"
                      />
                      <Button type="submit" size="sm" disabled={busy} loading={update.isPending}>
                        Validar e reconciliar
                      </Button>
                    </form>
                  ) : null}
                </Card>
              </li>
            )
          })}
        </ul>
      ) : null}

      <Dialog
        open={Boolean(removing)}
        onClose={() => setRemovingId(null)}
        title="Remover conexão"
        description="Escolha com cuidado: as duas opções interrompem novas sincronizações desta conexão."
        size="md"
        busy={remove.isPending}
      >
        {removing ? (
          <div className="space-y-4">
            <div className="rounded-card border border-line bg-surface-muted p-3">
              <p className="text-body-sm font-semibold text-ink">
                {removing.displayName ?? removing.institutionName ?? 'Conexão'}
              </p>
              <p className="mt-1 text-caption text-ink-muted">
                {removing.accountCount} conta(s) importada(s) · itemId{' '}
                {removing.itemIdMasked ?? 'não disponível'}
              </p>
            </div>

            <div className="rounded-card border border-line bg-surface p-3">
              <p className="flex items-center gap-2 text-body-sm font-semibold text-ink">
                <Unplug aria-hidden="true" className="size-4 text-ink-muted" />
                Desativar e manter dados
              </p>
              <p className="mt-1 text-body-sm text-ink-muted">
                Interrompe novas sincronizações e tira as contas importadas dos saldos correntes. O
                histórico importado permanece disponível.
              </p>
              <Button
                variant="secondary"
                size="sm"
                className="mt-3"
                loading={remove.isPending}
                loadingLabel="Desativando…"
                onClick={() => {
                  if (removing) remove.mutate({ id: removing.id, deleteHistory: false })
                }}
              >
                Desativar e manter dados
              </Button>
            </div>

            <div className="rounded-card border border-expense/35 bg-expense-soft p-3">
              <p className="flex items-center gap-2 text-body-sm font-semibold text-ink">
                <Trash2 aria-hidden="true" className="size-4 text-expense" />
                Excluir conexão e dados
              </p>
              <p className="mt-1 text-body-sm text-ink-muted">
                Apaga as contas, transações, faturas e investimentos importados por esta conexão.
                Contas manuais, lançamentos nessas contas e categorias são preservados.
              </p>
              <Button
                variant="danger"
                size="sm"
                className="mt-3"
                loading={remove.isPending}
                loadingLabel="Excluindo…"
                onClick={() => {
                  if (removing) remove.mutate({ id: removing.id, deleteHistory: true })
                }}
              >
                Excluir conexão e dados
              </Button>
            </div>

            <div className="flex justify-end">
              <Button
                variant="ghost"
                onClick={() => setRemovingId(null)}
                disabled={remove.isPending}
              >
                Cancelar
              </Button>
            </div>
          </div>
        ) : null}
      </Dialog>
    </section>
  )
}
