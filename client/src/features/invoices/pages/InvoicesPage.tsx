import { Link, useParams } from 'react-router'

import { useQuery } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import { financeApi, type Invoice, type Transaction } from '@/features/integration/api'

import { CardCycleSettings } from '../components/CardCycleSettings'

const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
}

export function InvoicesPage() {
  const { accountId = '' } = useParams<{ accountId: string }>()
  const accountQuery = useQuery({
    queryKey: ['account', accountId],
    queryFn: () => financeApi.account(accountId),
    enabled: Boolean(accountId),
  })
  const invoicesQuery = useQuery({
    queryKey: ['invoices', accountId],
    queryFn: () => financeApi.invoices(accountId),
    enabled: Boolean(accountId),
  })
  const transactionsQuery = useQuery({
    queryKey: ['transactions', 'card', accountId],
    queryFn: () => financeApi.transactions({ limit: 100, accountId }),
    enabled: Boolean(accountId),
  })

  const account = accountQuery.data
  const invoices = invoicesQuery.data?.items ?? []
  const currentInvoice = invoices.find((invoice) => invoice.status === 'OPEN') ?? null
  const history = currentInvoice
    ? invoices.filter((invoice) => invoice.id !== currentInvoice.id)
    : invoices
  const pendingTransactions = currentInvoice
    ? (transactionsQuery.data?.items ?? []).filter(
        (transaction) =>
          transaction.status === 'PENDING' &&
          transaction.type === 'DEBIT' &&
          transaction.postedOn >= currentInvoice.cycleStart &&
          transaction.postedOn <= currentInvoice.cycleEnd,
      )
    : []
  const pendingTotal = pendingTransactions.reduce(
    (sum, transaction) => sum + Math.abs(Number(transaction.amount) || 0),
    0,
  )
  const unallocated = (transactionsQuery.data?.items ?? []).filter(
    (transaction) =>
      !transaction.invoiceId &&
      (transaction.status === 'POSTED' || transaction.status === 'RECONCILED'),
  )

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6">
      <Link to="/cards" className="text-sm font-medium text-action underline underline-offset-2">
        ← Cartões
      </Link>

      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">Cartões</p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          Faturas · {account?.alias ?? 'Cartão'}
        </h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Faturas organizadas por ciclo local. Compras no cartão contam como despesa uma vez;
          pagamentos apenas reduzem o saldo devedor.
        </p>
      </header>

      {accountQuery.isError || invoicesQuery.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {(accountQuery.error ?? invoicesQuery.error) instanceof ApiError
            ? ((accountQuery.error ?? invoicesQuery.error) as ApiError).message
            : 'Não foi possível carregar as faturas.'}
        </p>
      ) : null}

      <CardCycleSettings accountId={accountId} profile={account?.cardProfile} />

      {invoicesQuery.isLoading ? (
        <p role="status" className="text-sm text-ink-muted">
          Carregando faturas…
        </p>
      ) : null}

      {currentInvoice ? (
        <CurrentInvoiceSection
          invoice={currentInvoice}
          pendingTransactions={pendingTransactions}
          pendingTotal={pendingTotal}
        />
      ) : null}

      {!currentInvoice && invoices.length > 0 && account?.cardProfile ? (
        <p className="rounded-card border border-line bg-surface p-4 text-sm text-ink-muted">
          Nenhuma fatura aberta no ciclo atual. O histórico abaixo permanece disponível.
        </p>
      ) : null}

      {invoicesQuery.isSuccess && invoices.length === 0 ? (
        <p className="rounded-card border border-line bg-surface p-6 text-sm text-ink-muted">
          {account?.cardProfile
            ? 'Nenhuma fatura gerada ainda. Compras efetivadas aparecem aqui automaticamente.'
            : 'Configure o ciclo do cartão para gerar faturas.'}
        </p>
      ) : null}

      {history.length > 0 ? (
        <section aria-labelledby="invoice-history-heading" className="space-y-3">
          <h2 id="invoice-history-heading" className="text-lg font-semibold text-ink">
            Histórico de faturas
          </h2>
          <ul className="grid gap-4 sm:grid-cols-2">
            {history.map((invoice) => (
              <li key={invoice.id}>
                <Link
                  to={`/invoices/${invoice.id}`}
                  className="block h-full rounded-card border border-line bg-surface p-4 transition-colors hover:bg-surface-muted"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="text-sm font-semibold text-ink">
                        {formatDate(invoice.cycleStart)} a {formatDate(invoice.cycleEnd)}
                      </p>
                      <p className="mt-1 text-xs text-ink-muted">
                        Vence em {formatDate(invoice.dueDate)}
                      </p>
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <span className="rounded-card border border-line bg-surface-muted px-2 py-1 text-xs font-medium text-ink">
                        {STATUS_LABELS[invoice.status] ?? invoice.status}
                      </span>
                      {invoice.missingPurchases ? (
                        <span className="rounded-card border border-warning/40 bg-warning/5 px-2 py-1 text-xs font-medium text-warning">
                          Histórico incompleto
                        </span>
                      ) : null}
                      {invoice.providerBill ? (
                        <span
                          className={
                            invoice.providerBill.reconciled
                              ? 'rounded-card border border-income/30 bg-income/5 px-2 py-1 text-xs font-medium text-income'
                              : 'rounded-card border border-warning/40 bg-warning/5 px-2 py-1 text-xs font-medium text-warning'
                          }
                        >
                          {invoice.providerBill.reconciled ? 'Conciliada' : 'Divergente'}
                        </span>
                      ) : null}
                    </div>
                  </div>
                  <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                    <dt className="text-ink-muted">Total</dt>
                    <dd className="numeric text-right font-medium text-ink">
                      {formatMoney(invoice.total, 'BRL')}
                    </dd>
                    <dt className="text-ink-muted">Pago</dt>
                    <dd className="numeric text-right font-medium text-ink">
                      {formatMoney(invoice.paymentsTotal, 'BRL')}
                    </dd>
                    <dt className="text-ink-muted">Saldo devedor</dt>
                    <dd className="numeric text-right font-medium text-ink">
                      {formatMoney(invoice.outstanding, 'BRL')}
                    </dd>
                    {Number(invoice.appliedCredit) > 0 ? (
                      <>
                        <dt className="text-ink-muted">Crédito aplicado</dt>
                        <dd className="numeric text-right font-medium text-ink">
                          {formatMoney(invoice.appliedCredit, 'BRL')}
                        </dd>
                      </>
                    ) : null}
                    {invoice.minimumPayment ? (
                      <>
                        <dt className="text-ink-muted">Pagamento mínimo</dt>
                        <dd className="numeric text-right font-medium text-ink">
                          {formatMoney(invoice.minimumPayment, 'BRL')}
                        </dd>
                      </>
                    ) : null}
                  </dl>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {unallocated.length > 0 ? (
        <section className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-4">
          <h2 className="text-sm font-semibold text-ink">Transações não alocadas</h2>
          <p className="text-xs text-ink-muted">
            {account?.cardProfile
              ? 'Estas compras ainda não pertencem a uma fatura gerada.'
              : 'Sem ciclo configurado, as compras permanecem não alocadas.'}
          </p>
          <ul className="space-y-1 text-sm">
            {unallocated.map((transaction) => (
              <li key={transaction.id} className="flex justify-between gap-2">
                <span className="truncate text-ink">
                  {transaction.postedOn} · {transaction.description ?? 'Sem descrição'}
                </span>
                <span className="numeric text-ink">{formatMoney(transaction.amount, 'BRL')}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  )
}

function CurrentInvoiceSection({
  invoice,
  pendingTransactions,
  pendingTotal,
}: {
  readonly invoice: Invoice
  readonly pendingTransactions: readonly Transaction[]
  readonly pendingTotal: number
}) {
  return (
    <section
      aria-labelledby="current-invoice-heading"
      className="space-y-4 rounded-card border border-line bg-surface p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
            Ciclo em andamento
          </p>
          <h2 id="current-invoice-heading" className="mt-1 text-lg font-semibold text-ink">
            Fatura atual
          </h2>
          <p className="mt-1 text-sm text-ink-muted">
            {formatDate(invoice.cycleStart)} a {formatDate(invoice.cycleEnd)} · fecha{' '}
            {formatDate(invoice.closingDate)} · vence {formatDate(invoice.dueDate)}
          </p>
        </div>
        <span className="rounded-card border border-line bg-surface-muted px-3 py-1 text-sm font-semibold text-ink">
          {STATUS_LABELS[invoice.status] ?? invoice.status}
        </span>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Total efetivado</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(invoice.total, 'BRL')}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Pago</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(invoice.paymentsTotal, 'BRL')}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Saldo devedor</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(invoice.outstanding, 'BRL')}
          </dd>
        </div>
        {invoice.minimumPayment ? (
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs text-ink-muted">Pagamento mínimo</dt>
            <dd className="numeric mt-1 text-sm font-semibold text-ink">
              {formatMoney(invoice.minimumPayment, 'BRL')}
            </dd>
          </div>
        ) : null}
        {Number(invoice.appliedCredit) > 0 ? (
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs text-ink-muted">Crédito aplicado</dt>
            <dd className="numeric mt-1 text-sm font-semibold text-ink">
              {formatMoney(invoice.appliedCredit, 'BRL')}
            </dd>
          </div>
        ) : null}
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Pendentes ({pendingTransactions.length})</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(pendingTotal.toFixed(2), 'BRL')}
          </dd>
        </div>
      </dl>

      <p className="text-xs leading-relaxed text-ink-muted">
        Compras pendentes ainda não entram no total nem no saldo devedor; elas passam a compor a
        fatura quando forem efetivadas.
      </p>

      {pendingTransactions.length > 0 ? (
        <div>
          <h3 className="text-sm font-semibold text-ink">Compras pendentes</h3>
          <ul className="mt-2 divide-y divide-line overflow-hidden rounded-card border border-line">
            {pendingTransactions.map((transaction) => (
              <li
                key={transaction.id}
                className="flex items-baseline justify-between gap-3 px-3 py-2 text-sm"
              >
                <span className="min-w-0 truncate text-ink">
                  {transaction.postedOn} · {transaction.description ?? 'Sem descrição'}
                </span>
                <span className="numeric shrink-0 text-ink">
                  {formatMoney(transaction.amount, transaction.currency)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="text-sm text-ink-muted">Nenhuma compra pendente neste ciclo.</p>
      )}

      <Link
        to={`/invoices/${invoice.id}`}
        className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
      >
        Abrir fatura atual
      </Link>
    </section>
  )
}

function formatDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}
