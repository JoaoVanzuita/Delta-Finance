import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type Transaction } from '@/features/integration/api'

interface SourceRemovedReviewProps {
  readonly transaction: Transaction
}

/**
 * Revisão explícita de uma transação removida na origem.
 *
 * Restaurar valida no servidor que ela voltou a existir; confirmar a remoção mantém a linha e o
 * histórico, apenas fora dos totais correntes. Nenhuma ação apaga registros.
 */
export function SourceRemovedReview({ transaction }: SourceRemovedReviewProps) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const review = useMutation({
    mutationFn: (action: 'RESTORE' | 'CONFIRM_REMOVAL') =>
      financeApi.reviewSourceRemoval(transaction.id, action),
    onSuccess: (updated) => {
      setConfirming(false)
      setError(null)
      setMessage(
        updated.status === 'SOURCE_REMOVED'
          ? 'Remoção confirmada. O histórico foi preservado.'
          : 'Transação restaurada e de volta aos totais.',
      )
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Não foi possível revisar a remoção na origem.',
      )
    },
  })

  if (transaction.status !== 'SOURCE_REMOVED') {
    return null
  }

  return (
    <div className="mt-2 w-full space-y-2 rounded-card border border-warning/40 bg-warning/5 p-2">
      <p className="text-xs text-ink">
        Esta transação deixou de aparecer na origem e está fora dos totais correntes.
      </p>
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={review.isPending}
          onClick={() => {
            setError(null)
            setMessage(null)
            review.mutate('RESTORE')
          }}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-semibold text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
        >
          Restaurar
        </button>
        {confirming ? (
          <>
            <button
              type="button"
              disabled={review.isPending}
              onClick={() => review.mutate('CONFIRM_REMOVAL')}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-expense/50 bg-expense/5 px-3 text-xs font-semibold text-expense disabled:opacity-60"
            >
              Confirmar remoção
            </button>
            <button
              type="button"
              onClick={() => setConfirming(false)}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-medium text-ink"
            >
              Cancelar
            </button>
          </>
        ) : (
          <button
            type="button"
            disabled={review.isPending}
            onClick={() => {
              setError(null)
              setMessage(null)
              setConfirming(true)
            }}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-medium text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
          >
            Confirmar remoção
          </button>
        )}
      </div>
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
