import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi } from '../api'

interface ConnectionFormProps {
  readonly disabled?: boolean
}

/** Adiciona um itemId já autorizado pelo usuário no Meu Pluggy. */
export function ConnectionForm({ disabled }: ConnectionFormProps) {
  const queryClient = useQueryClient()
  const [itemId, setItemId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const addConnection = useMutation({
    mutationFn: (value: string) => financeApi.addConnection(value),
    onSuccess: () => {
      setItemId('')
      setError(null)
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
    },
    onError: (caught) => {
      setError(
        caught instanceof ApiError
          ? (caught.fieldMessage('itemId') ?? caught.message)
          : 'Não foi possível validar a conexão. Tente novamente.',
      )
    },
  })

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        if (!itemId.trim()) {
          setError('Informe o itemId da conexão.')
          return
        }
        addConnection.mutate(itemId.trim())
      }}
      className="space-y-4"
    >
      <div>
        <label htmlFor="connection-item-id" className="block text-sm font-medium text-ink">
          itemId da conexão
        </label>
        <p id="connection-item-id-hint" className="mt-1 text-xs text-ink-muted">
          Copie o itemId da conexão autorizada na sua conta Meu Pluggy. A validação é feita no
          servidor antes de salvar. Uma mesma conta deve aparecer em apenas uma conexão; se você
          reconectou a instituição e o itemId mudou, use <strong>Atualizar itemId</strong> na
          conexão existente em vez de adicionar outra.
        </p>
        <input
          id="connection-item-id"
          type="text"
          value={itemId}
          onChange={(event) => setItemId(event.target.value)}
          disabled={disabled || addConnection.isPending}
          aria-describedby={
            error ? 'connection-item-id-hint connection-item-id-error' : 'connection-item-id-hint'
          }
          aria-invalid={Boolean(error)}
          autoComplete="off"
          className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink shadow-sm outline-none transition-colors focus:border-focus disabled:opacity-60"
        />
        {error ? (
          <p id="connection-item-id-error" role="alert" className="mt-1 text-sm text-expense">
            {error}
          </p>
        ) : null}
      </div>

      <button
        type="submit"
        disabled={disabled || addConnection.isPending}
        className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
      >
        {addConnection.isPending ? 'Validando conexão…' : 'Adicionar conexão'}
      </button>
    </form>
  )
}
