import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { DisplayNameEditor } from '@/components/DisplayNameEditor'
import { financeApi, type Connection } from '../api'
import { connectionStatusLabels, errorMessage, formatDateTime } from '../syncLabels'

interface ConnectionsListProps {
  readonly connections: readonly Connection[]
  readonly configured: boolean
}

/**
 * Lista de conexões (itens) com ações de recuperação.
 *
 * O itemId bruto nunca é exibido; apenas a máscara. Atualizar o itemId valida a nova autorização no
 * servidor e reconcilia o histórico sem duplicar registros.
 */
export function ConnectionsList({ connections, configured }: ConnectionsListProps) {
  const queryClient = useQueryClient()
  const [editingId, setEditingId] = useState<string | null>(null)
  const [newItemId, setNewItemId] = useState('')
  const [confirmingRemoveId, setConfirmingRemoveId] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['connections'] })
    void queryClient.invalidateQueries({ queryKey: ['accounts'] })
    void queryClient.invalidateQueries({ queryKey: ['transactions'] })
    void queryClient.invalidateQueries({ queryKey: ['integration'] })
    void queryClient.invalidateQueries({ queryKey: ['syncStatus'] })
    void queryClient.invalidateQueries({ queryKey: ['syncRuns'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-categories'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-timeseries'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-entries'] })
    void queryClient.invalidateQueries({ queryKey: ['cards', 'overview'] })
  }

  const sync = useMutation({
    mutationFn: (id: string) => financeApi.syncConnection(id),
    onSuccess: () => {
      setError(null)
      setMessage('Consulta enfileirada. O resultado aparece no histórico em instantes.')
      invalidate()
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
      setMessage('itemId validado e histórico reconciliado sem duplicar registros.')
      invalidate()
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível validar o novo itemId.',
      )
    },
  })

  const remove = useMutation({
    mutationFn: (input: { id: string; deleteHistory: boolean }) =>
      financeApi.removeConnection(input.id, input.deleteHistory),
    onSuccess: (_result, input) => {
      setConfirmingRemoveId(null)
      setError(null)
      setMessage(
        input.deleteHistory
          ? 'Conexão e dados importados removidos. Contas manuais e categorias foram preservadas.'
          : 'Conexão desativada. O histórico importado foi preservado e saiu dos saldos correntes.',
      )
      invalidate()
    },
    onError: (caught) => {
      setMessage(null)
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível remover a conexão.')
    },
  })

  const busy = sync.isPending || update.isPending || remove.isPending

  async function renameConnection(id: string, displayName: string) {
    const updated = await financeApi.renameConnection(id, displayName)
    invalidate()
    return updated
  }

  async function resetConnectionName(id: string) {
    const updated = await financeApi.resetConnectionName(id)
    invalidate()
    return updated
  }

  return (
    <section
      aria-labelledby="connections-heading"
      className="space-y-4 rounded-card border border-line bg-surface p-5 sm:p-6"
    >
      <div>
        <h2 id="connections-heading" className="text-lg font-semibold text-ink">
          Conexões
        </h2>
        <p className="mt-1 text-sm text-ink-muted">
          Cada itemId corresponde a uma autorização bancária. Desativar interrompe novas
          sincronizações e preserva o histórico; excluir remove também as contas e transações
          importadas por esta conexão.
        </p>
      </div>

      {message ? (
        <p
          role="status"
          className="rounded-card border border-income/30 bg-income/5 p-3 text-sm text-ink"
        >
          {message}
        </p>
      ) : null}
      {error ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-3 text-sm text-ink"
        >
          {error}
        </p>
      ) : null}

      {!configured ? (
        <p className="text-sm text-ink-muted">Salve credenciais válidas para adicionar conexões.</p>
      ) : null}

      {configured && connections.length === 0 ? (
        <p className="text-sm text-ink-muted">Nenhuma conexão configurada.</p>
      ) : null}

      {connections.length > 0 ? (
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line">
          {connections.map((connection) => {
            const editing = editingId === connection.id
            const confirming = confirmingRemoveId === connection.id
            return (
              <li key={connection.id} className="space-y-3 p-4">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <p className="font-medium text-ink">
                      {connection.displayName ??
                        connection.institutionName ??
                        'Instituição não informada'}
                    </p>
                    {connection.nameCustomized && connection.institutionName ? (
                      <p className="text-xs text-ink-muted">
                        Instituição no Pluggy: {connection.institutionName}
                      </p>
                    ) : null}
                    <p className="numeric mt-1 text-xs text-ink-muted">
                      itemId {connection.itemIdMasked ?? 'não disponível'} ·{' '}
                      {connection.accountCount} conta(s)
                    </p>
                    <p className="mt-1 flex flex-wrap gap-x-2 gap-y-1 text-xs text-ink-muted">
                      <span>{connectionStatusLabels[connection.status] ?? connection.status}</span>
                      <span aria-hidden="true">·</span>
                      <span>Última leitura: {formatDateTime(connection.lastSyncAt)}</span>
                      {connection.consentExpiresAt ? (
                        <>
                          <span aria-hidden="true">·</span>
                          <span>
                            Consentimento até {formatDateTime(connection.consentExpiresAt)}
                          </span>
                        </>
                      ) : null}
                    </p>
                    {connection.lastErrorCode ? (
                      <p className="mt-1 text-xs text-ink">
                        {errorMessage(connection.lastErrorCode)}
                      </p>
                    ) : null}
                  </div>

                  <div className="flex flex-col gap-2 sm:items-end">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => {
                          setError(null)
                          setMessage(null)
                          sync.mutate(connection.id)
                        }}
                        className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-3 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60"
                      >
                        {sync.isPending ? 'Enfileirando…' : 'Consultar agora'}
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => {
                          setError(null)
                          setMessage(null)
                          setEditingId(editing ? null : connection.id)
                          setNewItemId('')
                        }}
                        className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
                      >
                        {editing ? 'Cancelar' : 'Atualizar itemId'}
                      </button>
                      {confirming ? (
                        <>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              remove.mutate({ id: connection.id, deleteHistory: false })
                            }
                            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
                          >
                            Desativar e manter dados
                          </button>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              remove.mutate({ id: connection.id, deleteHistory: true })
                            }
                            className="inline-flex min-h-11 items-center justify-center rounded-card border border-expense/50 bg-expense/5 px-3 text-sm font-semibold text-expense disabled:opacity-60"
                          >
                            Excluir conexão e dados
                          </button>
                          <button
                            type="button"
                            onClick={() => setConfirmingRemoveId(null)}
                            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink"
                          >
                            Cancelar
                          </button>
                        </>
                      ) : (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => {
                            setError(null)
                            setMessage(null)
                            setConfirmingRemoveId(connection.id)
                          }}
                          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
                        >
                          Remover
                        </button>
                      )}
                    </div>
                    <DisplayNameEditor
                      currentName={connection.displayName ?? null}
                      originalName={connection.institutionName ?? null}
                      customized={Boolean(connection.nameCustomized)}
                      onRename={(displayName) => renameConnection(connection.id, displayName)}
                      onReset={() => resetConnectionName(connection.id)}
                    />
                  </div>
                </div>

                {confirming ? (
                  <p className="text-xs leading-relaxed text-ink-muted">
                    <strong className="text-ink">Desativar</strong> interrompe novas sincronizações
                    e tira as contas importadas dos saldos correntes, preservando o histórico.{' '}
                    <strong className="text-ink">Excluir</strong> apaga também as contas e
                    transações importadas por esta conexão; contas manuais e categorias permanecem.
                  </p>
                ) : null}

                {editing ? (
                  <form
                    noValidate
                    className="flex flex-col gap-2 rounded-card border border-line bg-surface-muted p-3 sm:flex-row sm:items-end"
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
                    <div className="flex-1">
                      <label
                        htmlFor={`new-item-${connection.id}`}
                        className="block text-sm font-medium text-ink"
                      >
                        Novo itemId
                      </label>
                      <p className="mt-1 text-xs text-ink-muted">
                        Use o itemId da nova autorização no Meu Pluggy. O servidor valida e
                        reconcilia o histórico.
                      </p>
                      <input
                        id={`new-item-${connection.id}`}
                        type="text"
                        value={newItemId}
                        onChange={(event) => setNewItemId(event.target.value)}
                        autoComplete="off"
                        className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
                      />
                    </div>
                    <button
                      type="submit"
                      disabled={busy}
                      className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60"
                    >
                      {update.isPending ? 'Validando…' : 'Validar e reconciliar'}
                    </button>
                  </form>
                ) : null}
              </li>
            )
          })}
        </ul>
      ) : null}
    </section>
  )
}
