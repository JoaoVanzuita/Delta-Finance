import { useState } from 'react'

import { Link, useParams } from 'react-router'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import {
  financeApi,
  type InvoiceItem,
  type InvoiceItemCorrection,
} from '@/features/integration/api'

import { InvoiceItemDialog } from '../components/InvoiceItemDialog'
import { InvoicePaymentDialog } from '../components/InvoicePaymentDialog'
import { InvoiceStatus } from '../components/InvoiceStatus'

const KIND_LABELS: Record<string, string> = {
  PURCHASE: 'Compra',
  INSTALLMENT: 'Parcela',
  INTEREST: 'Juros',
  FEE: 'Encargo',
  TAX: 'Tarifa',
  CREDIT: 'Crédito',
  REFUND: 'Estorno',
  MANUAL_ADJUSTMENT: 'Ajuste manual',
}

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  CONFIRMED: 'Confirmado',
  REVERSED: 'Revertido',
}

export function InvoiceDetailPage() {
  const { id = '' } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const [itemDialog, setItemDialog] = useState<'new' | InvoiceItem | null>(null)
  const [paymentDialog, setPaymentDialog] = useState(false)
  const [historyItemId, setHistoryItemId] = useState<string | null>(null)

  const invoiceQuery = useQuery({
    queryKey: ['invoice', id],
    queryFn: () => financeApi.invoice(id),
    enabled: Boolean(id),
  })
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })

  const remove = useMutation({
    mutationFn: (itemId: string) => financeApi.deleteInvoiceItem(itemId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['invoice', id] })
      void queryClient.invalidateQueries({ queryKey: ['invoices'] })
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
  })

  if (invoiceQuery.isLoading) {
    return (
      <p role="status" className="text-sm text-ink-muted">
        Carregando fatura…
      </p>
    )
  }

  if (invoiceQuery.isError || !invoiceQuery.data) {
    return (
      <p
        role="alert"
        className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
      >
        {invoiceQuery.error instanceof ApiError
          ? invoiceQuery.error.message
          : 'Não foi possível carregar a fatura.'}
      </p>
    )
  }

  const invoice = invoiceQuery.data
  const items = invoice.items ?? []
  const payments = invoice.payments ?? []
  const paid = invoice.status === 'PAID'
  const cardAccount = (accountsQuery.data?.items ?? []).find(
    (account) => account.id === invoice.cardAccountId,
  )

  return (
    <div className="mx-auto w-full max-w-4xl space-y-6">
      <Link
        to={`/cards/${invoice.cardAccountId}/invoices`}
        className="text-sm font-medium text-action underline underline-offset-2"
      >
        ← Faturas do cartão
      </Link>

      <InvoiceStatus invoice={invoice} creditBalance={cardAccount?.cardProfile?.creditBalance} />

      {paid ? (
        <p className="rounded-card border border-line bg-surface-muted p-3 text-sm text-ink-muted">
          Fatura paga é histórica: itens, valores e pagamentos não podem mais ser alterados.
        </p>
      ) : null}

      {remove.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-3 text-sm text-ink"
        >
          {remove.error instanceof ApiError
            ? remove.error.message
            : 'Não foi possível remover o item.'}
        </p>
      ) : null}

      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold text-ink">Itens da fatura</h2>
          {!paid ? (
            <button
              type="button"
              onClick={() => setItemDialog('new')}
              className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
            >
              Adicionar item
            </button>
          ) : null}
        </div>

        {items.length === 0 ? (
          <p className="rounded-card border border-line bg-surface p-4 text-sm text-ink-muted">
            Nenhum item nesta fatura.
          </p>
        ) : (
          <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
            {items.map((item) => (
              <li key={item.id} className="space-y-2 p-4">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ink">
                      {item.description ?? 'Sem descrição'}
                    </p>
                    <p className="mt-1 text-xs text-ink-muted">
                      {KIND_LABELS[item.kind] ?? item.kind} · {effectLabel(item.reportingEffect)} ·{' '}
                      {item.occurredOn}
                      {item.totalInstallments
                        ? ` · parcela ${item.installmentNumber}/${item.totalInstallments}`
                        : ''}
                    </p>
                    <div className="mt-1 flex flex-wrap gap-2 text-xs">
                      <span className="rounded-card border border-line bg-surface-muted px-2 py-0.5 text-ink">
                        {item.source === 'IMPORTED' ? 'Importado' : 'Manual'}
                      </span>
                      {item.transactionId ? (
                        <span className="rounded-card border border-line bg-surface-muted px-2 py-0.5 text-ink">
                          Espelho de transação
                        </span>
                      ) : null}
                    </div>
                  </div>
                  <p className="numeric shrink-0 text-sm font-semibold text-ink">
                    {formatMoney(item.amount, 'BRL')}
                  </p>
                </div>

                <div className="flex flex-wrap gap-2">
                  {!paid && !item.transactionId ? (
                    <>
                      <button
                        type="button"
                        onClick={() => setItemDialog(item)}
                        className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-medium text-ink hover:bg-surface-muted"
                      >
                        Editar
                      </button>
                      <button
                        type="button"
                        onClick={() => remove.mutate(item.id)}
                        disabled={remove.isPending}
                        className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-medium text-ink hover:bg-surface-muted disabled:opacity-60"
                      >
                        Remover
                      </button>
                    </>
                  ) : null}
                  {(item.correctionCount ?? 0) > 0 ? (
                    <button
                      type="button"
                      onClick={() =>
                        setHistoryItemId((current) => (current === item.id ? null : item.id))
                      }
                      className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-medium text-ink hover:bg-surface-muted"
                    >
                      Histórico ({item.correctionCount ?? 0})
                    </button>
                  ) : null}
                </div>

                {historyItemId === item.id ? <CorrectionsList itemId={item.id} /> : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold text-ink">Pagamentos</h2>
          {!paid ? (
            <button
              type="button"
              onClick={() => setPaymentDialog(true)}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
            >
              Registrar pagamento
            </button>
          ) : null}
        </div>
        {payments.length === 0 ? (
          <p className="rounded-card border border-line bg-surface p-4 text-sm text-ink-muted">
            Nenhum pagamento registrado.
          </p>
        ) : (
          <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
            {payments.map((payment) => (
              <li key={payment.id} className="flex items-center justify-between gap-2 p-4 text-sm">
                <span className="text-ink">
                  {payment.paidOn} · {PAYMENT_STATUS_LABELS[payment.status] ?? payment.status}
                </span>
                <span className="numeric font-medium text-ink">
                  {formatMoney(payment.amount, 'BRL')}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {itemDialog ? (
        <InvoiceItemDialog
          invoiceId={invoice.id}
          cardAccountId={invoice.cardAccountId}
          item={itemDialog === 'new' ? undefined : itemDialog}
          onClose={() => setItemDialog(null)}
        />
      ) : null}

      {paymentDialog ? (
        <InvoicePaymentDialog
          invoiceId={invoice.id}
          outstanding={invoice.outstanding}
          onClose={() => setPaymentDialog(false)}
        />
      ) : null}
    </div>
  )
}

function CorrectionsList({ itemId }: { readonly itemId: string }) {
  const query = useQuery({
    queryKey: ['invoiceItemCorrections', itemId],
    queryFn: () => financeApi.invoiceItemCorrections(itemId),
  })
  if (query.isLoading) {
    return <p className="text-xs text-ink-muted">Carregando histórico…</p>
  }
  if (query.isError) {
    return <p className="text-xs text-expense">Não foi possível carregar o histórico.</p>
  }
  const corrections = (query.data ?? []) as InvoiceItemCorrection[]
  if (corrections.length === 0) {
    return <p className="text-xs text-ink-muted">Sem correções.</p>
  }
  return (
    <ol className="space-y-1 rounded-card border border-line bg-surface-muted p-2 text-xs">
      {corrections.map((correction) => (
        <li key={correction.id}>
          <span className="font-medium text-ink">{correction.fieldName}</span>:{' '}
          {correction.oldValue ?? '—'} → {correction.newValue}
        </li>
      ))}
    </ol>
  )
}

function effectLabel(effect: string): string {
  if (effect === 'EXPENSE_INCREASE') return 'aumenta despesa'
  if (effect === 'EXPENSE_DECREASE') return 'reduz despesa'
  return 'sem efeito'
}
