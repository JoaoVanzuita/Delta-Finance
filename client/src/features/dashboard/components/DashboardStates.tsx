import { Link } from 'react-router'

import { formatMoney } from '@/features/accounts/format'
import type { DashboardSummary } from '@/features/dashboard/api'
import { formatDateTime, isStale } from '@/features/dashboard/format'

export function DashboardLoading() {
  return (
    <div role="status" aria-live="polite" className="space-y-3">
      <div className="h-6 w-48 animate-pulse rounded-card bg-surface-muted" />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index} className="h-28 animate-pulse rounded-card bg-surface-muted" />
        ))}
      </div>
    </div>
  )
}

export function DashboardError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <section
      role="alert"
      className="space-y-3 rounded-card border border-expense/30 bg-expense/5 p-5"
    >
      <h1 className="text-lg font-semibold text-ink">Não foi possível carregar o dashboard</h1>
      <p className="text-sm text-ink-muted">{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
        >
          Tentar novamente
        </button>
      ) : null}
    </section>
  )
}

export function DashboardEmptyPeriod() {
  return (
    <p className="rounded-card border border-line bg-surface-muted p-4 text-sm text-ink-muted">
      Nenhum lançamento efetivado no período e com os filtros atuais. Ajuste o período ou remova
      filtros para ver mais dados.
    </p>
  )
}

export function SyncState({ lastSuccessfulSyncAt }: { lastSuccessfulSyncAt?: string }) {
  const hasSync = Boolean(lastSuccessfulSyncAt)
  return (
    <p className="text-xs leading-relaxed text-ink-muted">
      <span className="font-medium text-ink">Última sincronização relevante:</span>{' '}
      {hasSync ? formatDateTime(lastSuccessfulSyncAt) : 'sem sincronização no período'}
    </p>
  )
}

export function BalanceReference({ referenceAt }: { referenceAt?: string }) {
  const stale = isStale(referenceAt)
  return (
    <p className="text-xs leading-relaxed text-ink-muted">
      <span className="font-medium text-ink">Data de referência do saldo:</span>{' '}
      {referenceAt ? formatDateTime(referenceAt) : 'não informada'}
      {stale ? (
        <span className="ml-2 inline-flex rounded-card border border-warning/40 bg-warning/5 px-2 py-0.5 text-xs font-medium text-ink">
          {referenceAt ? 'Saldo desatualizado' : 'Saldo ausente'}
        </span>
      ) : null}
    </p>
  )
}

export function PendingTransactionsBlock({ summary }: { summary: DashboardSummary }) {
  const count = summary.pendingCount ?? 0
  return (
    <section
      aria-labelledby="pending-heading"
      className="rounded-card border border-warning/40 bg-warning/5 p-4 sm:p-5"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="pending-heading" className="text-base font-semibold text-ink">
          Transações pendentes
        </h2>
        <span className="text-xs text-ink-muted">Não compõem os totais oficiais</span>
      </div>
      {count === 0 ? (
        <p className="mt-2 text-sm text-ink-muted">Nenhuma transação pendente no período.</p>
      ) : (
        <>
          <dl className="mt-3 grid gap-3 sm:grid-cols-3">
            <div>
              <dt className="text-xs text-ink-muted">Quantidade</dt>
              <dd className="numeric text-lg font-semibold text-ink">{count}</dd>
            </div>
            <div>
              <dt className="text-xs text-ink-muted">Entradas pendentes</dt>
              <dd className="numeric text-lg font-semibold text-income">
                {formatMoney(summary.pendingIncome ?? '0.00', 'BRL')}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-ink-muted">Saídas pendentes</dt>
              <dd className="numeric text-lg font-semibold text-expense">
                {formatMoney(summary.pendingExpenses ?? '0.00', 'BRL')}
              </dd>
            </div>
          </dl>
          <p className="mt-3 text-xs leading-relaxed text-ink-muted">
            Pendentes não possuem total líquido e passam a compor receitas ou despesas uma única vez
            após a efetivação, mantendo o mesmo registro.
          </p>
        </>
      )}
    </section>
  )
}

export function InternalMatchesNotice({ count }: { count?: number }) {
  if (!count || count <= 0) return null
  return (
    <p className="rounded-card border border-line bg-surface-muted p-4 text-sm text-ink">
      {count} movimento(s) interno(s) aguardando confirmação.{' '}
      <Link to="/reconciliation" className="font-semibold text-action underline">
        Revisar reconciliação
      </Link>
    </p>
  )
}
