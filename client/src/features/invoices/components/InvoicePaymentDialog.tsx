import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import { financeApi, type InvoicePaymentInput } from '@/features/integration/api'

interface InvoicePaymentDialogProps {
  readonly invoiceId: string
  readonly outstanding: string
  readonly onClose: () => void
}

/** Pagamento total ou parcial, sempre em conta ativa do próprio usuário. */
export function InvoicePaymentDialog({
  invoiceId,
  outstanding,
  onClose,
}: InvoicePaymentDialogProps) {
  const queryClient = useQueryClient()
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })
  const [paymentAccountId, setPaymentAccountId] = useState('')
  const [amount, setAmount] = useState(outstanding)
  const [paidOn, setPaidOn] = useState(today())
  const [error, setError] = useState<string | null>(null)
  const parsedAmount = Number(amount.replace(',', '.'))
  const exceedsOutstanding = Number.isFinite(parsedAmount) && parsedAmount > Number(outstanding)

  const save = useMutation({
    mutationFn: (input: InvoicePaymentInput) => financeApi.addInvoicePayment(invoiceId, input),
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
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível registrar o pagamento.',
      )
    },
  })

  function submit() {
    setError(null)
    if (!paymentAccountId) {
      setError('Selecione a conta de pagamento.')
      return
    }
    const parsed = Number(amount.replace(',', '.'))
    if (!amount.trim() || Number.isNaN(parsed) || parsed <= 0) {
      setError('Informe um valor positivo.')
      return
    }
    save.mutate({
      paymentAccountId,
      amount: amount.replace(',', '.'),
      paidOn,
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40 p-4 sm:items-center">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="invoice-payment-heading"
        className="w-full max-w-md rounded-card border border-line bg-surface p-5 shadow-xl"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="invoice-payment-heading" className="text-lg font-semibold text-ink">
              Registrar pagamento
            </h2>
            <p className="mt-1 text-sm text-ink-muted">
              O pagamento reduz o saldo devedor e não cria nova despesa. O valor acima do saldo vira
              crédito para faturas futuras.
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
            <span className="font-medium text-ink">Conta de pagamento</span>
            <select
              value={paymentAccountId}
              onChange={(event) => setPaymentAccountId(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Selecione</option>
              {(accountsQuery.data?.items ?? []).map((account) => (
                <option key={account.id} value={account.id} disabled={account.status !== 'ACTIVE'}>
                  {account.alias ?? account.institutionName ?? account.id}
                  {account.status !== 'ACTIVE' ? ' (inativa)' : ''}
                </option>
              ))}
            </select>
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

          {exceedsOutstanding ? (
            <p className="rounded-card border border-line bg-surface-muted p-2 text-xs text-ink-muted">
              O excedente de {formatMoney((parsedAmount - Number(outstanding)).toFixed(2), 'BRL')}{' '}
              será mantido como crédito do cartão.
            </p>
          ) : null}

          <label className="block text-sm">
            <span className="font-medium text-ink">Data do pagamento</span>
            <input
              type="date"
              value={paidOn}
              onChange={(event) => setPaidOn(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>

          <button
            type="submit"
            disabled={save.isPending}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface disabled:opacity-60"
          >
            {save.isPending ? 'Registrando…' : 'Registrar pagamento'}
          </button>
        </form>
      </div>
    </div>
  )
}

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}
