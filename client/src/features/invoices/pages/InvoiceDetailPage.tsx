import { useState } from 'react'

import { Link, useParams } from 'react-router'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'
import { invalidateInvoiceViews } from '@/lib/invalidation'
import { PageHeader } from '@/components/ui/page-header'

import { formatDate, formatMoney } from '@/lib/format'
import {
  financeApi,
  type InvoiceItem,
  type InvoiceItemCorrection,
  type ProviderBill,
} from '@/features/integration/api'

import { InvoiceItemDialog } from '../components/InvoiceItemDialog'
import { InvoicePaymentDialog } from '../components/InvoicePaymentDialog'
import { InvoiceStatus } from '../components/InvoiceStatus'
import { ProviderReconciliationDialog } from '../components/ProviderReconciliationDialog'

const PAYMENT_MODE_LABELS: Record<string, string> = {
  PIX: 'Pix',
  TED: 'TED',
  DOC: 'DOC',
  BANK_SLIP: 'Boleto',
  BOLETO: 'Boleto',
  CREDIT_CARD: 'Cartão de crédito',
  DEBIT_CARD: 'Cartão de débito',
  ACCOUNT_DEBIT: 'Débito em conta',
}

const PAYMENT_VALUE_TYPE_LABELS: Record<string, string> = {
  PARTIAL_PAYMENT: 'Pagamento parcial',
  TOTAL_PAYMENT: 'Pagamento total',
  MINIMUM_PAYMENT: 'Pagamento mínimo',
  OTHER: 'Outro valor',
}

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

const CORRECTION_FIELD_LABELS: Record<string, string> = {
  amount: 'Valor',
  description: 'Descrição',
  occurredOn: 'Data',
  kind: 'Tipo',
  reportingEffect: 'Efeito no total',
  installmentNumber: 'Número da parcela',
  totalInstallments: 'Total de parcelas',
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
  const cardAccountId = invoiceQuery.data?.cardAccountId
  const transactionsQuery = useQuery({
    queryKey: ['transactions', 'card', cardAccountId],
    queryFn: () => financeApi.transactions({ limit: 200, accountId: cardAccountId ?? '' }),
    enabled: Boolean(cardAccountId),
  })

  const remove = useMutation({
    mutationFn: (itemId: string) => financeApi.deleteInvoiceItem(itemId),
    onSuccess: async () => {
      await invalidateInvoiceViews(queryClient, id)
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
  const currency = cardAccount?.currency ?? 'BRL'
  const pendingTransactions =
    invoice.status === 'OPEN'
      ? (transactionsQuery.data?.items ?? []).filter(
          (transaction) =>
            transaction.status === 'PENDING' &&
            transaction.type === 'DEBIT' &&
            transaction.postedOn >= invoice.cycleStart &&
            transaction.postedOn <= invoice.cycleEnd,
        )
      : []

  return (
    <div className="mx-auto w-full max-w-4xl space-y-6">
      <Link
        to={`/cards/${invoice.cardAccountId}/invoices`}
        className="text-sm font-medium text-action underline underline-offset-2"
      >
        ← Faturas do cartão
      </Link>

      <PageHeader
        overline="Fatura do cartão"
        title={paid ? 'Fatura paga' : 'Detalhe da fatura'}
        description={`Ciclo de ${formatDate(invoice.cycleStart)} a ${formatDate(invoice.cycleEnd)} · vencimento em ${formatDate(invoice.dueDate)}`}
        meta={
          cardAccount ? (
            <span className="text-body-sm text-ink-muted">
              {cardAccount.alias ?? cardAccount.institutionName ?? 'Cartão'}
            </span>
          ) : null
        }
      />

      <InvoiceStatus
        invoice={invoice}
        creditBalance={cardAccount?.cardProfile?.creditBalance}
        currency={currency}
      />

      {invoice.providerBill ? (
        <ProviderReconciliation
          invoiceId={invoice.id}
          bill={invoice.providerBill}
          currency={currency}
        />
      ) : null}

      {invoice.missingPurchases ? (
        <p className="rounded-card border border-warning/40 bg-warning/5 p-3 text-sm leading-relaxed text-ink">
          Esta fatura tem pagamento registrado, mas nenhuma compra local no ciclo. O histórico do
          conector pode estar incompleto; concilie com o total informado pelo provedor ou registre
          um ajuste explícito.
        </p>
      ) : null}

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

      {invoice.status === 'OPEN' ? (
        <section
          aria-labelledby="invoice-pending-heading"
          className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-4"
        >
          <h2 id="invoice-pending-heading" className="text-sm font-semibold text-ink">
            Compras pendentes neste ciclo
          </h2>
          <p className="text-xs leading-relaxed text-ink-muted">
            Elas ainda não compõem o total nem o saldo devedor desta fatura.
          </p>
          {pendingTransactions.length === 0 ? (
            <p className="text-sm text-ink-muted">Nenhuma compra pendente neste ciclo.</p>
          ) : (
            <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
              {pendingTransactions.map((transaction) => (
                <li
                  key={transaction.id}
                  className="flex items-baseline justify-between gap-3 px-3 py-2 text-sm"
                >
                  <span className="min-w-0 truncate text-ink">
                    {formatDate(transaction.postedOn)} ·{' '}
                    {transaction.description ?? 'Sem descrição'}
                  </span>
                  <span className="numeric shrink-0 text-ink">
                    {formatMoney(transaction.amount, transaction.currency)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}

      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold text-ink">Itens da fatura</h2>
          {!paid ? (
            <button
              type="button"
              onClick={() => setItemDialog('new')}
              className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-on-primary transition-colors hover:bg-primary-hover"
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
                      {formatDate(item.occurredOn)}
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
                          Vinculado a uma transação
                        </span>
                      ) : null}
                    </div>
                  </div>
                  <p
                    className={[
                      'numeric shrink-0 text-body-sm font-semibold',
                      item.reportingEffect === 'EXPENSE_DECREASE'
                        ? 'text-income'
                        : item.reportingEffect === 'EXPENSE_INCREASE'
                          ? 'text-expense'
                          : 'text-ink',
                    ].join(' ')}
                  >
                    {formatMoney(
                      item.reportingEffect === 'EXPENSE_DECREASE' ? `-${item.amount}` : item.amount,
                      currency,
                    )}
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
                  {formatDate(payment.paidOn)} ·{' '}
                  {PAYMENT_STATUS_LABELS[payment.status] ?? payment.status} ·{' '}
                  {payment.source === 'IMPORTED' ? 'Importado' : 'Manual'}
                </span>
                <span className="numeric font-medium text-ink">
                  {formatMoney(payment.amount, currency)}
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
          <span className="font-medium text-ink">
            {CORRECTION_FIELD_LABELS[correction.fieldName] ?? 'Campo'}
          </span>
          : {correction.oldValue ?? '—'} → {correction.newValue}
        </li>
      ))}
    </ol>
  )
}

function effectLabel(effect: string): string {
  if (effect === 'EXPENSE_INCREASE') return 'Aumenta a fatura'
  if (effect === 'EXPENSE_DECREASE') return 'Reduz a fatura'
  return 'Sem efeito no total'
}

function ProviderReconciliation({
  invoiceId,
  bill,
  currency,
}: {
  readonly invoiceId: string
  readonly bill: ProviderBill
  readonly currency: string
}) {
  const [dialog, setDialog] = useState(false)
  const totalDifference = Number(bill.totalDifference) || 0
  const paymentsDifference = Number(bill.paymentsDifference) || 0
  const hasDifference = totalDifference !== 0 || paymentsDifference !== 0
  const adjustment = (-totalDifference).toFixed(2)
  return (
    <section
      aria-labelledby="provider-bill-heading"
      className={
        hasDifference
          ? 'space-y-3 rounded-card border border-warning/40 bg-warning/5 p-4'
          : 'space-y-3 rounded-card border border-income/30 bg-income/5 p-4'
      }
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 id="provider-bill-heading" className="text-sm font-semibold text-ink">
            Total informado pelo provedor
          </h2>
          <p className="mt-1 text-xs leading-relaxed text-ink-muted">
            Referência importada do provedor. O ajuste de conciliação é sempre explícito e auditado.
          </p>
        </div>
        <span
          className={
            hasDifference
              ? 'rounded-card border border-warning/40 bg-surface px-2 py-1 text-xs font-semibold text-warning'
              : 'rounded-card border border-income/30 bg-surface px-2 py-1 text-xs font-semibold text-income'
          }
        >
          {hasDifference ? 'Divergente' : 'Conciliada'}
        </span>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Total oficial</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(bill.total, currency)}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Pagamento mínimo</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(bill.minimumPayment, currency)}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Pagamentos oficiais</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(bill.paymentsTotal, currency)}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Vencimento oficial</dt>
          <dd className="mt-1 text-sm font-semibold text-ink">{formatDate(bill.dueDate)}</dd>
        </div>
      </dl>

      {hasDifference ? (
        <dl className="grid gap-2 text-sm sm:grid-cols-2">
          <div className="flex items-baseline justify-between gap-2 rounded-card border border-line bg-surface px-3 py-2">
            <dt className="text-ink-muted">Diferença de total (local − oficial)</dt>
            <dd className="numeric font-semibold text-ink">
              {formatMoney(bill.totalDifference, currency)}
            </dd>
          </div>
          <div className="flex items-baseline justify-between gap-2 rounded-card border border-line bg-surface px-3 py-2">
            <dt className="text-ink-muted">Diferença de pagamentos (local − oficial)</dt>
            <dd className="numeric font-semibold text-ink">
              {formatMoney(bill.paymentsDifference, currency)}
            </dd>
          </div>
        </dl>
      ) : null}

      {bill.payments.length > 0 ? (
        <div>
          <h3 className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
            Pagamentos informados pelo provedor
          </h3>
          <ul className="mt-2 divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
            {bill.payments.map((payment, index) => (
              <li
                key={`${payment.paidOn}-${payment.amount}-${index}`}
                className="flex items-baseline justify-between gap-3 px-3 py-2 text-sm"
              >
                <span className="text-ink">
                  {formatDate(payment.paidOn)}
                  {payment.paymentMode
                    ? ` · ${PAYMENT_MODE_LABELS[payment.paymentMode] ?? 'Meio não informado'}`
                    : ''}
                  {payment.valueType
                    ? ` · ${PAYMENT_VALUE_TYPE_LABELS[payment.valueType] ?? 'Tipo não informado'}`
                    : ''}
                </span>
                <span className="numeric font-medium text-ink">
                  {formatMoney(payment.amount, currency)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="text-xs text-ink-muted">
          Nenhum pagamento oficial informado para esta fatura.
        </p>
      )}

      {bill.ignoredPayments > 0 ? (
        <p className="text-xs text-ink-muted">
          {bill.ignoredPayments} pagamento(s) da Bill foram ignorados por estarem fora da janela
          deste ciclo.
        </p>
      ) : null}

      {totalDifference !== 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-line bg-surface p-3">
          <p className="max-w-xl text-xs leading-relaxed text-ink-muted">
            O ajuste lança a diferença como item manual auditado. Em fatura paga, o status é
            preservado e o crédito aplicado é normalizado.
          </p>
          <button
            type="button"
            onClick={() => setDialog(true)}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink hover:bg-surface-muted"
          >
            Conciliar fatura
          </button>
        </div>
      ) : null}

      {dialog ? (
        <ProviderReconciliationDialog
          invoiceId={invoiceId}
          adjustment={adjustment}
          onClose={() => setDialog(false)}
        />
      ) : null}
    </section>
  )
}
