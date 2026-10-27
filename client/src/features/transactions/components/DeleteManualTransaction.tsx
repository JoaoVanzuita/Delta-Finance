import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type Transaction } from '@/features/integration/api'

interface DeleteManualTransactionProps {
  readonly transaction: Transaction
}

/**
 * Exclusão de lançamento manual com confirmação explícita.
 *
 * Transações importadas não são apagadas: a revisão de remoção na origem é o
 * fluxo correto. Transferências manuais removem as duas pernas e despesas com
 * reembolsos vinculados são recusadas pelo servidor.
 */
export function DeleteManualTransaction({ transaction }: DeleteManualTransactionProps) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const deletion = useMutation({
    mutationFn: () => financeApi.deleteTransaction(transaction.id),
    onSuccess: () => {
      setConfirming(false)
      setError(null)
      setMessage('Lançamento excluído.')
      for (const queryKey of [
        'transactions',
        'accounts',
        'account',
        'dashboard-summary',
        'dashboard-categories',
        'dashboard-timeseries',
        'dashboard-entries',
        'internalMatches',
        'invoices',
      ]) {
        void queryClient.invalidateQueries({ queryKey: [queryKey] })
      }
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Não foi possível excluir o lançamento manual.',
      )
    },
  })

  if (transaction.source !== 'MANUAL') {
    return null
  }

  return (
    <div className="mt-2 space-y-2">
      <div className="flex flex-wrap gap-2">
        {confirming ? (
          <>
            <button
              type="button"
              disabled={deletion.isPending}
              onClick={() => deletion.mutate()}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-expense/50 bg-expense/5 px-3 text-xs font-semibold text-expense disabled:opacity-60"
            >
              {deletion.isPending ? 'Excluindo…' : 'Confirmar exclusão'}
            </button>
            <button
              type="button"
              disabled={deletion.isPending}
              onClick={() => {
                setConfirming(false)
                setError(null)
              }}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-medium text-ink disabled:opacity-60"
            >
              Cancelar
            </button>
          </>
        ) : (
          <button
            type="button"
            disabled={deletion.isPending}
            onClick={() => {
              setMessage(null)
              setError(null)
              setConfirming(true)
            }}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-expense/40 bg-surface px-3 text-xs font-medium text-expense transition-colors hover:bg-expense/5 disabled:opacity-60"
          >
            Excluir lançamento
          </button>
        )}
      </div>

      {confirming ? (
        <p className="text-xs text-ink-muted">
          Excluir este lançamento? Transferências manuais removem as duas pernas. Despesas com
          reembolsos ou chargebacks vinculados são recusadas.
        </p>
      ) : null}
      {message ? (
        <p role="status" className="text-xs text-ink">
          {message}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-xs text-expense">
          {error}
        </p>
      ) : null}
    </div>
  )
}
