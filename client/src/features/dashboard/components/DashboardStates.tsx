import { RefreshCw, SearchX, WalletCards } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime, isStale } from '@/features/dashboard/format'

export function DashboardLoading() {
  return (
    <div role="status" aria-live="polite" className="space-y-4">
      <span className="sr-only">Carregando dashboard…</span>
      <div className="rounded-panel border border-line bg-surface p-6">
        <Skeleton className="h-3 w-28" />
        <Skeleton className="mt-4 h-12 w-64 max-w-full" />
        <Skeleton className="mt-3 h-4 w-44" />
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index} className="rounded-card border border-line bg-surface p-4">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="mt-3 h-7 w-32 max-w-full" />
            <Skeleton className="mt-3 h-3 w-full" />
          </div>
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-44 rounded-card" />
        <Skeleton className="h-44 rounded-card" />
      </div>
      <Skeleton className="h-72 rounded-card" />
    </div>
  )
}

export function DashboardError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <section role="alert" className="rounded-card border border-expense/30 bg-expense-soft p-5">
      <h2 className="text-section-title font-semibold text-ink">
        Não foi possível carregar o dashboard
      </h2>
      <p className="mt-1 max-w-prose text-body-sm text-ink-muted">{message}</p>
      {onRetry ? (
        <Button
          type="button"
          className="mt-4"
          onClick={onRetry}
          leadingIcon={<RefreshCw aria-hidden="true" className="size-4" />}
        >
          Tentar novamente
        </Button>
      ) : null}
    </section>
  )
}

export function DashboardPartialError({
  title,
  message,
  onRetry,
}: {
  title: string
  message: string
  onRetry?: () => void
}) {
  return (
    <div role="alert" className="rounded-card border border-warning/40 bg-warning-soft p-4">
      <p className="text-body-sm font-semibold text-ink">{title}</p>
      <p className="mt-1 text-caption text-ink-muted">{message}</p>
      {onRetry ? (
        <Button type="button" variant="secondary" size="sm" className="mt-3" onClick={onRetry}>
          Tentar novamente
        </Button>
      ) : null}
    </div>
  )
}

export function DashboardEmptyPeriod({
  filtered,
  previousPeriodLabel,
  onPreviousPeriod,
  onClearFilters,
}: {
  readonly filtered: boolean
  readonly previousPeriodLabel?: string
  readonly onPreviousPeriod?: () => void
  readonly onClearFilters?: () => void
}) {
  const action = filtered && onClearFilters ? (
    <Button type="button" variant="secondary" onClick={onClearFilters}>
      Limpar filtros
    </Button>
  ) : previousPeriodLabel && onPreviousPeriod ? (
    <Button type="button" variant="secondary" onClick={onPreviousPeriod}>
      Ver {previousPeriodLabel}
    </Button>
  ) : null
  return (
    <EmptyState
      icon={filtered ? <SearchX className="size-5" /> : <WalletCards className="size-5" />}
      title={filtered ? 'Nenhum resultado para estes filtros' : 'Nenhuma movimentação no período'}
      description={
        filtered
          ? 'Os totais oficiais estão zerados porque nenhuma entrada corresponde aos filtros atuais. Remova os filtros ou amplie o período.'
          : previousPeriodLabel
            ? 'Não há receitas, despesas ou itens de fatura efetivados neste período. O mês atual pode ainda não ter lançamentos; abra o período anterior ou escolha outra data.'
            : 'Não há receitas, despesas ou itens de fatura efetivados neste período. Escolha outra semana, mês ou ano para consultar.'
      }
      action={action}
    />
  )
}

export function BalanceReference({ referenceAt }: { referenceAt?: string }) {
  const stale = isStale(referenceAt)
  return (
    <span>
      Referência dos saldos: {referenceAt ? formatDateTime(referenceAt) : 'não informada'}
      {stale ? (
        <span className="ml-1 inline-flex rounded-pill border border-warning/40 bg-warning-soft px-1.5 py-0.5 font-medium text-ink">
          {referenceAt ? 'desatualizado' : 'ausente'}
        </span>
      ) : null}
    </span>
  )
}
