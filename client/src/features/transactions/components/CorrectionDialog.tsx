import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type Transaction } from '@/features/integration/api'

interface CorrectionDialogProps {
  readonly transaction: Transaction
  readonly onClose: () => void
}

/** Correção de descrição, data, valor, categoria e observações com auditoria no servidor. */
export function CorrectionDialog({ transaction, onClose }: CorrectionDialogProps) {
  const queryClient = useQueryClient()
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: financeApi.categories })
  const [description, setDescription] = useState(transaction.description ?? '')
  const [postedOn, setPostedOn] = useState(transaction.postedOn)
  const [amount, setAmount] = useState(transaction.amount)
  const [categoryId, setCategoryId] = useState(transaction.categoryId ?? '')
  const [notes, setNotes] = useState(transaction.notes ?? '')
  const [relatedTransactionId, setRelatedTransactionId] = useState(
    transaction.relatedTransactionId ?? '',
  )
  const [error, setError] = useState<string | null>(null)
  const refund = transaction.type === 'REFUND' || transaction.type === 'CHARGEBACK'
  const originalsQuery = useQuery({
    queryKey: ['transactions', 'refund-originals', transaction.accountId],
    queryFn: () =>
      financeApi.transactions({
        accountId: transaction.accountId,
        type: 'DEBIT',
        limit: 100,
      }),
    enabled: refund,
  })

  const correct = useMutation({
    mutationFn: () =>
      financeApi.correctTransaction(transaction.id, {
        description: description.trim(),
        postedOn,
        amount: amount.replace(',', '.'),
        categoryId: categoryId || undefined,
        notes,
        relatedTransactionId: relatedTransactionId || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['categories'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-categories'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-timeseries'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-entries'] })
      void queryClient.invalidateQueries({ queryKey: ['cards', 'overview'] })
      onClose()
    },
    onError: (caught) => {
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível corrigir a transação.',
      )
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40 p-4 sm:items-center">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="correction-heading"
        className="max-h-[90dvh] w-full max-w-lg overflow-y-auto rounded-card border border-line bg-surface p-5 shadow-xl"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="correction-heading" className="text-lg font-semibold text-ink">
              Corrigir transação
            </h2>
            <p className="mt-1 text-sm text-ink-muted">
              A correção prevalece sobre sincronizações futuras e preserva o valor original para
              auditoria.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink"
          >
            Fechar
          </button>
        </div>

        {error ? (
          <p
            role="alert"
            className="mt-3 rounded-card border border-expense/30 bg-expense/5 p-3 text-sm text-ink"
          >
            {error}
          </p>
        ) : null}

        <form
          noValidate
          className="mt-4 space-y-3"
          onSubmit={(event) => {
            event.preventDefault()
            if (!description.trim()) {
              setError('Informe a descrição.')
              return
            }
            const parsed = Number(amount.replace(',', '.'))
            if (Number.isNaN(parsed) || parsed === 0) {
              setError('Informe um valor diferente de zero.')
              return
            }
            correct.mutate()
          }}
        >
          <label className="block text-sm">
            <span className="font-medium text-ink">Descrição</span>
            <input
              type="text"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={500}
              autoFocus
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="font-medium text-ink">Data</span>
              <input
                type="date"
                value={postedOn}
                onChange={(event) => setPostedOn(event.target.value)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              />
            </label>
            <label className="block text-sm">
              <span className="font-medium text-ink">Valor (R$)</span>
              <input
                type="text"
                inputMode="decimal"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              />
            </label>
          </div>

          <label className="block text-sm">
            <span className="font-medium text-ink">Categoria</span>
            <select
              value={categoryId}
              onChange={(event) => setCategoryId(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Sem categoria</option>
              {(categoriesQuery.data?.items ?? [])
                .filter((category) => category.active)
                .map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
            </select>
          </label>

          {refund ? (
            <label className="block text-sm">
              <span className="font-medium text-ink">Despesa original</span>
              <select
                value={relatedTransactionId}
                onChange={(event) => setRelatedTransactionId(event.target.value)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              >
                <option value="">Sem vínculo</option>
                {(originalsQuery.data?.items ?? []).map((original) => (
                  <option key={original.id} value={original.id}>
                    {original.description ?? original.id} · {original.postedOn} · {original.amount}
                  </option>
                ))}
              </select>
              <span className="mt-1 block text-xs text-ink-muted">
                O vínculo limita a redução ao valor da despesa original e herda a categoria.
              </span>
            </label>
          ) : null}

          <label className="block text-sm">
            <span className="font-medium text-ink">Observações</span>
            <textarea
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              maxLength={2000}
              rows={3}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>

          <button
            type="submit"
            disabled={correct.isPending}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface disabled:opacity-60"
          >
            {correct.isPending ? 'Salvando…' : 'Salvar correção'}
          </button>
        </form>
      </div>
    </div>
  )
}
