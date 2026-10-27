import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import { financeApi } from '@/features/integration/api'

interface ProviderReconciliationDialogProps {
  readonly invoiceId: string
  /** Ajuste a aplicar: positivo aumenta o total, negativo reduz. */
  readonly adjustment: string
  readonly onClose: () => void
}

/**
 * Ajuste explícito entre o total local e a Bill oficial.
 *
 * O valor é lançado como item manual auditado; nenhum item existente é reescrito. Em fatura paga o
 * status é preservado.
 */
export function ProviderReconciliationDialog({
  invoiceId,
  adjustment,
  onClose,
}: ProviderReconciliationDialogProps) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('Divergência entre a fatura local e a Bill oficial')
  const [error, setError] = useState<string | null>(null)
  const numeric = Number(adjustment)

  const save = useMutation({
    mutationFn: () => financeApi.reconcileInvoiceProvider(invoiceId, { reason: reason.trim() }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoice', invoiceId] })
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-categories'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-timeseries'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-entries'] })
      void queryClient.invalidateQueries({ queryKey: ['cards', 'overview'] })
      onClose()
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível conciliar a fatura.')
    },
  })

  function submit() {
    setError(null)
    if (!reason.trim()) {
      setError('Informe o motivo do ajuste.')
      return
    }
    save.mutate()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40 p-4 sm:items-center">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="provider-reconciliation-heading"
        className="w-full max-w-md rounded-card border border-line bg-surface p-5 shadow-xl"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="provider-reconciliation-heading" className="text-lg font-semibold text-ink">
              Conciliar com a Bill oficial
            </h2>
            <p className="mt-1 text-sm text-ink-muted">
              {numeric >= 0
                ? `O total desta fatura será aumentado em ${formatMoney(adjustment, 'BRL')}.`
                : `O total desta fatura será reduzido em ${formatMoney(adjustment.replace('-', ''), 'BRL')}.`}{' '}
              O ajuste é um item manual auditado; compras e pagamentos existentes não são
              reescritos.
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
            submit()
          }}
        >
          <label className="block text-sm">
            <span className="font-medium text-ink">Motivo do ajuste</span>
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={300}
              rows={3}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>

          <div className="flex flex-wrap justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink hover:bg-surface-muted"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={save.isPending}
              className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface hover:bg-primary-hover disabled:opacity-60"
            >
              {save.isPending ? 'Conciliando…' : 'Registrar ajuste'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
