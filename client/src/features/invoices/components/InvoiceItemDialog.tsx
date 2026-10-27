import { useMemo, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type InvoiceItem, type InvoiceItemInput } from '@/features/integration/api'

const KINDS = [
  { value: 'PURCHASE', label: 'Compra' },
  { value: 'INSTALLMENT', label: 'Parcela' },
  { value: 'INTEREST', label: 'Juros' },
  { value: 'FEE', label: 'Encargo' },
  { value: 'TAX', label: 'Tarifa' },
  { value: 'CREDIT', label: 'Crédito' },
  { value: 'REFUND', label: 'Estorno' },
  { value: 'MANUAL_ADJUSTMENT', label: 'Ajuste manual' },
] as const

const EFFECTS = [
  { value: 'EXPENSE_INCREASE', label: 'Aumenta a despesa' },
  { value: 'EXPENSE_DECREASE', label: 'Reduz a despesa' },
  { value: 'NONE', label: 'Sem efeito (espelho)' },
] as const

type Kind = (typeof KINDS)[number]['value']
type Effect = (typeof EFFECTS)[number]['value']

function defaultEffect(kind: Kind): Effect {
  if (kind === 'INTEREST' || kind === 'FEE' || kind === 'TAX') return 'EXPENSE_INCREASE'
  if (kind === 'CREDIT' || kind === 'REFUND') return 'EXPENSE_DECREASE'
  return 'NONE'
}

interface InvoiceItemDialogProps {
  readonly invoiceId: string
  readonly cardAccountId: string
  readonly item?: InvoiceItem
  readonly onClose: () => void
}

/** Item manual ou espelho de transação, com efeito obrigatório e histórico auditado. */
export function InvoiceItemDialog({
  invoiceId,
  cardAccountId,
  item,
  onClose,
}: InvoiceItemDialogProps) {
  const queryClient = useQueryClient()
  const [kind, setKind] = useState<Kind>((item?.kind as Kind) ?? 'INTEREST')
  const [reportingEffect, setReportingEffect] = useState<Effect>(
    (item?.reportingEffect as Effect) ?? 'EXPENSE_INCREASE',
  )
  const [description, setDescription] = useState(item?.description ?? '')
  const [amount, setAmount] = useState(item?.amount ?? '')
  const [occurredOn, setOccurredOn] = useState(item?.occurredOn ?? today())
  const [transactionId, setTransactionId] = useState(item?.transactionId ?? '')
  const [installmentNumber, setInstallmentNumber] = useState(
    item?.installmentNumber ? String(item.installmentNumber) : '',
  )
  const [totalInstallments, setTotalInstallments] = useState(
    item?.totalInstallments ? String(item.totalInstallments) : '',
  )
  const [error, setError] = useState<string | null>(null)

  const transactionsQuery = useQuery({
    queryKey: ['transactions', 'invoice-items', cardAccountId],
    queryFn: () => financeApi.transactions({ limit: 100, accountId: cardAccountId }),
  })
  const candidates = useMemo(
    () =>
      (transactionsQuery.data?.items ?? []).filter(
        (transaction) =>
          !transaction.invoiceId ||
          transaction.invoiceId === invoiceId ||
          transaction.id === item?.transactionId,
      ),
    [transactionsQuery.data, invoiceId, item?.transactionId],
  )

  const save = useMutation({
    mutationFn: (input: InvoiceItemInput) =>
      item
        ? financeApi.updateInvoiceItem(item.id, input)
        : financeApi.addInvoiceItem(invoiceId, input),
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
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível salvar o item.')
    },
  })

  const requiresTransaction = kind === 'PURCHASE' || kind === 'INSTALLMENT'

  function submit() {
    setError(null)
    if (!description.trim()) {
      setError('Informe a descrição.')
      return
    }
    const parsed = Number(amount.replace(',', '.'))
    if (!amount.trim() || Number.isNaN(parsed) || parsed <= 0) {
      setError('Informe um valor positivo.')
      return
    }
    if (requiresTransaction && !transactionId) {
      setError('Compras e parcelas exigem uma transação vinculada.')
      return
    }
    save.mutate({
      kind,
      reportingEffect,
      description: description.trim(),
      amount: amount.replace(',', '.'),
      occurredOn,
      transactionId: transactionId || undefined,
      installmentNumber: installmentNumber ? Number(installmentNumber) : undefined,
      totalInstallments: totalInstallments ? Number(totalInstallments) : undefined,
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40 p-4 sm:items-center">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="invoice-item-heading"
        className="max-h-[92dvh] w-full max-w-xl overflow-y-auto rounded-card border border-line bg-surface p-5 shadow-xl"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="invoice-item-heading" className="text-lg font-semibold text-ink">
              {item ? 'Editar item da fatura' : 'Adicionar item à fatura'}
            </h2>
            <p className="mt-1 text-sm text-ink-muted">
              O valor é sempre positivo; o efeito define a direção. Correções preservam o histórico.
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
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="font-medium text-ink">Tipo</span>
              <select
                value={kind}
                onChange={(event) => {
                  const value = event.target.value as Kind
                  setKind(value)
                  setReportingEffect(defaultEffect(value))
                }}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              >
                {KINDS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-sm">
              <span className="font-medium text-ink">Efeito</span>
              <select
                value={reportingEffect}
                onChange={(event) => setReportingEffect(event.target.value as Effect)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              >
                {EFFECTS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label className="block text-sm">
            <span className="font-medium text-ink">Descrição</span>
            <input
              type="text"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={500}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="font-medium text-ink">
                {Number(totalInstallments) > 1 ? 'Valor de cada parcela (R$)' : 'Valor (R$)'}
              </span>
              <input
                type="text"
                inputMode="decimal"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              />
            </label>
            <label className="block text-sm">
              <span className="font-medium text-ink">Data</span>
              <input
                type="date"
                value={occurredOn}
                onChange={(event) => setOccurredOn(event.target.value)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              />
            </label>
          </div>

          {requiresTransaction ? (
            <label className="block text-sm">
              <span className="font-medium text-ink">Transação vinculada</span>
              <select
                value={transactionId}
                onChange={(event) => setTransactionId(event.target.value)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              >
                <option value="">Selecione</option>
                {candidates.map((transaction) => (
                  <option key={transaction.id} value={transaction.id}>
                    {transaction.description ?? transaction.id} · {transaction.postedOn} ·{' '}
                    {transaction.amount}
                  </option>
                ))}
              </select>
              <span className="mt-1 block text-xs text-ink-muted">
                A compra original não é alterada; o item é um espelho na fatura.
              </span>
            </label>
          ) : null}

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="font-medium text-ink">Parcela</span>
              <input
                type="number"
                min={1}
                value={installmentNumber}
                onChange={(event) => setInstallmentNumber(event.target.value)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              />
            </label>
            <label className="block text-sm">
              <span className="font-medium text-ink">Total de parcelas</span>
              <input
                type="number"
                min={1}
                value={totalInstallments}
                onChange={(event) => setTotalInstallments(event.target.value)}
                className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
              />
            </label>
          </div>
          {!item && Number(totalInstallments) > 1 ? (
            <p className="rounded-card border border-line bg-surface-muted p-3 text-xs text-ink-muted">
              Serão criadas {totalInstallments} parcelas, uma por ciclo. A primeira fica nesta
              fatura e as seguintes nas faturas futuras.
            </p>
          ) : null}

          <button
            type="submit"
            disabled={save.isPending}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface disabled:opacity-60"
          >
            {save.isPending ? 'Salvando…' : 'Salvar item'}
          </button>
        </form>
      </div>
    </div>
  )
}

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}
