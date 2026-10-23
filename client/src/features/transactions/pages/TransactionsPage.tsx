import { useState } from 'react'

import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import { financeApi, type Transaction } from '@/features/integration/api'
import { CorrectionDialog } from '@/features/transactions/components/CorrectionDialog'
import { SourceRemovedReview } from '@/features/transactions/components/SourceRemovedReview'

const statusLabels: Record<string, string> = {
  PENDING: 'Pendente',
  POSTED: 'Efetivada',
  SOURCE_REMOVED: 'Removida na origem',
  RECONCILED: 'Reconciliada',
}

const typeLabels: Record<string, string> = {
  DEBIT: 'Despesa',
  CREDIT: 'Receita',
  TRANSFER: 'Transferência',
  REFUND: 'Reembolso',
  CHARGEBACK: 'Chargeback',
  ADJUSTMENT: 'Ajuste',
}

export function TransactionsPage() {
  const [searchParams] = useSearchParams()
  const [accountId, setAccountId] = useState(searchParams.get('accountId') ?? '')
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [origin, setOrigin] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [correcting, setCorrecting] = useState<Transaction | null>(null)

  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })
  const filters = {
    accountId: accountId || undefined,
    status: status || undefined,
    type: type || undefined,
    origin: origin || undefined,
    from: from || undefined,
    to: to || undefined,
  }

  const transactionsQuery = useInfiniteQuery({
    queryKey: ['transactions', filters],
    queryFn: ({ pageParam }) =>
      financeApi.transactions({ ...filters, cursor: pageParam, limit: 50 }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore ? (last.nextCursor ?? undefined) : undefined),
  })

  const transactions = transactionsQuery.data?.pages.flatMap((page) => page.items) ?? []
  const accountNames = new Map(
    (accountsQuery.data?.items ?? []).map((account) => [
      account.id,
      account.alias ?? account.institutionName ?? 'Conta',
    ]),
  )

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Movimentos
        </p>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Transações</h1>
          <Link
            to="/transactions/new"
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
          >
            Novo lançamento
          </Link>
        </div>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Movimentos importados e manuais. Pendentes aparecem separados dos totais oficiais.
        </p>
      </header>

      <section
        aria-labelledby="filters-heading"
        className="rounded-card border border-line bg-surface p-4"
      >
        <h2 id="filters-heading" className="sr-only">
          Filtros de transações
        </h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <label className="block text-sm">
            <span className="font-medium text-ink">Conta</span>
            <select
              value={accountId}
              onChange={(event) => setAccountId(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Todas</option>
              {(accountsQuery.data?.items ?? []).map((account) => (
                <option key={account.id} value={account.id}>
                  {account.alias ?? account.institutionName ?? account.id}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm">
            <span className="font-medium text-ink">Estado</span>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Todos</option>
              <option value="PENDING">Pendente</option>
              <option value="POSTED">Efetivada</option>
              <option value="RECONCILED">Reconciliada</option>
              <option value="SOURCE_REMOVED">Removida na origem</option>
            </select>
          </label>

          <label className="block text-sm">
            <span className="font-medium text-ink">Tipo</span>
            <select
              value={type}
              onChange={(event) => setType(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Todos</option>
              <option value="DEBIT">Despesa</option>
              <option value="CREDIT">Receita</option>
              <option value="TRANSFER">Transferência</option>
              <option value="REFUND">Reembolso</option>
              <option value="CHARGEBACK">Chargeback</option>
              <option value="ADJUSTMENT">Ajuste</option>
            </select>
          </label>

          <label className="block text-sm">
            <span className="font-medium text-ink">Origem</span>
            <select
              value={origin}
              onChange={(event) => setOrigin(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Todas</option>
              <option value="IMPORTED">Importada</option>
              <option value="MANUAL">Manual</option>
            </select>
          </label>

          <label className="block text-sm">
            <span className="font-medium text-ink">De</span>
            <input
              type="date"
              value={from}
              onChange={(event) => setFrom(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>

          <label className="block text-sm">
            <span className="font-medium text-ink">Até</span>
            <input
              type="date"
              value={to}
              onChange={(event) => setTo(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
        </div>
      </section>

      {transactionsQuery.isLoading ? (
        <p role="status" aria-live="polite" className="text-sm text-ink-muted">
          Carregando transações…
        </p>
      ) : null}

      {transactionsQuery.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {transactionsQuery.error instanceof ApiError
            ? transactionsQuery.error.message
            : 'Não foi possível carregar as transações.'}
        </p>
      ) : null}

      {transactionsQuery.isSuccess && transactions.length === 0 ? (
        <p className="rounded-card border border-line bg-surface p-6 text-sm text-ink-muted">
          Nenhuma transação encontrada com os filtros atuais.
        </p>
      ) : null}

      {transactions.length > 0 ? (
        <>
          <p role="status" className="text-xs text-ink-muted">
            {transactions.length} transação(ões) carregada(s)
          </p>
          <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
            {transactions.map((transaction) => (
              <li
                key={transaction.id}
                className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-ink">
                    {transaction.description?.trim() || 'Descrição não informada'}
                  </p>
                  <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-muted">
                    <span>{formatDate(transaction.postedOn)}</span>
                    <span aria-hidden="true">·</span>
                    <span>
                      {accountNames.get(transaction.accountId) ?? 'Conta não identificada'}
                    </span>
                    <span aria-hidden="true">·</span>
                    <span>{typeLabels[transaction.type] ?? transaction.type}</span>
                    {transaction.categoryName ? (
                      <>
                        <span aria-hidden="true">·</span>
                        <span>{transaction.categoryName}</span>
                      </>
                    ) : null}
                  </p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <StatusBadge status={transaction.status} />
                    <span className="rounded-card border border-line bg-surface-muted px-2 py-0.5 text-xs text-ink">
                      {transaction.source === 'IMPORTED' ? 'Importada' : 'Manual'}
                    </span>
                    {transaction.currency !== 'BRL' ? (
                      <span className="rounded-card border border-warning/40 bg-warning/5 px-2 py-0.5 text-xs text-ink">
                        Moeda {transaction.currency}
                      </span>
                    ) : null}
                  </div>
                  <button
                    type="button"
                    onClick={() => setCorrecting(transaction)}
                    className="mt-2 inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-medium text-ink transition-colors hover:bg-surface-muted"
                  >
                    Corrigir
                  </button>
                  <SourceRemovedReview transaction={transaction} />
                </div>
                <p
                  className={[
                    'numeric shrink-0 text-right text-base font-semibold',
                    amountClass(transaction.amount),
                  ].join(' ')}
                >
                  {formatMoney(transaction.amount, transaction.currency)}
                </p>
              </li>
            ))}
          </ul>

          {transactionsQuery.hasNextPage ? (
            <button
              type="button"
              onClick={() => void transactionsQuery.fetchNextPage()}
              disabled={transactionsQuery.isFetchingNextPage}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
            >
              {transactionsQuery.isFetchingNextPage ? 'Carregando…' : 'Carregar mais'}
            </button>
          ) : null}
        </>
      ) : null}

      {correcting ? (
        <CorrectionDialog transaction={correcting} onClose={() => setCorrecting(null)} />
      ) : null}
    </div>
  )
}

function StatusBadge({ status }: { status: Transaction['status'] }) {
  const warning = status === 'PENDING' || status === 'SOURCE_REMOVED'
  return (
    <span
      className={[
        'rounded-card border px-2 py-0.5 text-xs font-medium',
        warning
          ? 'border-warning/40 bg-warning/5 text-ink'
          : 'border-line bg-surface-muted text-ink',
      ].join(' ')}
    >
      {statusLabels[status] ?? status}
    </span>
  )
}

function amountClass(amount: string): string {
  const value = Number(amount)
  if (value < 0) return 'text-expense'
  if (value > 0) return 'text-income'
  return 'text-ink'
}

function formatDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}
