import { useMemo, useState } from 'react'

import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'

import { formatMoney } from '@/features/accounts/format'
import {
  dashboardApi,
  type DashboardBucket,
  type DashboardCategory,
  type DashboardFilters as DashboardFiltersValue,
} from '@/features/dashboard/api'
import { CategoryChart } from '@/features/dashboard/components/CategoryChart'
import {
  dashboardActionClass,
  DashboardLiveRegion,
  DashboardSection,
} from '@/features/dashboard/components/DashboardAccessibility'
import { DashboardFilters } from '@/features/dashboard/components/DashboardFilters'
import {
  BalanceReference,
  DashboardEmptyPeriod,
  DashboardError,
  DashboardLoading,
  InternalMatchesNotice,
  PendingTransactionsBlock,
  SyncState,
} from '@/features/dashboard/components/DashboardStates'
import { DashboardTable } from '@/features/dashboard/components/DashboardTable'
import { TimeSeriesChart } from '@/features/dashboard/components/TimeSeriesChart'
import { formatPeriodLabel } from '@/features/dashboard/format'
import { financeApi } from '@/features/integration/api'
import { ApiError } from '@/lib/api/problem'

interface DrillDown {
  readonly title: string
  readonly filters: DashboardFiltersValue
}

/**
 * Dashboard consolidado (US4).
 *
 * Resultado do período e saldo atual têm rótulos, fontes e datas de referência
 * distintos. Cartões aparecem como passivo separado; pendentes ficam fora dos
 * totais; categorias e série temporal abrem o mesmo drill-down auditável.
 */
export function DashboardPage() {
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<DashboardFiltersValue>(() => ({
    period: 'MONTH',
    accountIds: searchParams.getAll('accountId'),
  }))
  const [drillDown, setDrillDown] = useState<DrillDown | null>(null)

  const summaryQuery = useQuery({
    queryKey: ['dashboard-summary', filters],
    queryFn: () => dashboardApi.summary(filters),
  })
  const categoriesQuery = useQuery({
    queryKey: ['dashboard-categories', filters],
    queryFn: () => dashboardApi.categories(filters),
  })
  const timeseriesQuery = useQuery({
    queryKey: ['dashboard-timeseries', filters],
    queryFn: () => dashboardApi.timeseries(filters),
  })
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })
  const categoryListQuery = useQuery({ queryKey: ['categories'], queryFn: financeApi.categories })

  const accountNames = useMemo(
    () =>
      new Map(
        (accountsQuery.data?.items ?? []).map((account) => [
          account.id,
          account.alias ?? account.institutionName ?? 'Conta',
        ]),
      ),
    [accountsQuery.data],
  )
  const categoryNames = useMemo(
    () =>
      new Map(
        (categoryListQuery.data?.items ?? []).map((category) => [category.id, category.name]),
      ),
    [categoryListQuery.data],
  )

  const entriesQuery = useInfiniteQuery({
    queryKey: ['dashboard-entries', drillDown?.filters],
    queryFn: ({ pageParam }) => dashboardApi.entries(drillDown?.filters ?? {}, pageParam, 25),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore ? (last.nextCursor ?? undefined) : undefined),
    enabled: drillDown !== null,
  })

  const summary = summaryQuery.data
  const announcement = summaryQuery.isFetching
    ? 'Atualizando dashboard…'
    : summary
      ? `Dashboard atualizado. Receitas ${formatMoney(
          summary.income,
          'BRL',
        )}, despesas ${formatMoney(summary.expenses, 'BRL')}, resultado ${formatMoney(
          summary.periodResult,
          'BRL',
        )}.`
      : 'Carregando dashboard…'
  const categories = categoriesQuery.data ?? []
  const buckets = timeseriesQuery.data ?? []
  const entries = entriesQuery.data?.pages.flatMap((page) => page.items) ?? []
  const selectedCategoryId = drillDown?.filters.categoryId
  const selectedBucketStart = drillDown?.filters.from
  const emptyPeriod =
    summary !== undefined &&
    Number(summary.income) === 0 &&
    Number(summary.expenses) === 0 &&
    categories.length === 0

  function applyFilters(next: DashboardFiltersValue) {
    setFilters(next)
    setDrillDown(null)
  }

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-categories'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-timeseries'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-entries'] })
  }

  function drillCategory(category: DashboardCategory) {
    setDrillDown({
      title: `Categoria: ${category.categoryName}`,
      filters: { ...filters, categoryId: category.categoryId },
    })
  }

  function drillBucket(bucket: DashboardBucket) {
    setDrillDown({
      title: `Período: ${bucket.label}`,
      filters: { ...filters, from: bucket.start, to: previousDay(bucket.end) },
    })
  }

  if (summaryQuery.isLoading) {
    return (
      <div className="mx-auto w-full max-w-6xl space-y-6">
        <DashboardHeader onRefresh={refresh} />
        <DashboardLoading />
      </div>
    )
  }

  if (summaryQuery.isError || !summary) {
    const message =
      summaryQuery.error instanceof ApiError
        ? summaryQuery.error.message
        : 'Ocorreu uma falha temporária ao carregar os relatórios.'
    return (
      <div className="mx-auto w-full max-w-6xl space-y-6">
        <DashboardHeader onRefresh={refresh} />
        <DashboardError message={message} onRetry={() => void summaryQuery.refetch()} />
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-6xl space-y-6">
      <DashboardHeader onRefresh={refresh} />
      <DashboardLiveRegion message={announcement} />

      <DashboardFilters
        value={filters}
        accounts={accountsQuery.data?.items ?? []}
        categories={categoryListQuery.data?.items ?? []}
        onApply={applyFilters}
      />

      <section aria-labelledby="totals-heading" className="space-y-4">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="totals-heading" className="text-lg font-semibold text-ink">
            Visão do período
          </h2>
          <p className="text-sm text-ink-muted">
            {formatPeriodLabel(summary.periodStart, summary.periodEnd)}
          </p>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <article className="rounded-card border border-line bg-surface p-4">
            <h3 className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
              Resultado do período
            </h3>
            <p
              className={[
                'numeric mt-2 text-2xl font-semibold',
                Number(summary.periodResult) < 0 ? 'text-expense' : 'text-ink',
              ].join(' ')}
            >
              {formatMoney(summary.periodResult, 'BRL')}
            </p>
            <p className="mt-2 text-xs leading-relaxed text-ink-muted">
              Receitas menos despesas no período selecionado.
            </p>
          </article>

          <article className="rounded-card border border-line bg-surface p-4">
            <h3 className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
              Receitas
            </h3>
            <p className="numeric mt-2 text-2xl font-semibold text-income">
              {formatMoney(summary.income, 'BRL')}
            </p>
            <p className="mt-2 text-xs leading-relaxed text-ink-muted">
              Somente transações efetivadas ou reconciliadas.
            </p>
          </article>

          <article className="rounded-card border border-line bg-surface p-4">
            <h3 className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
              Despesas
            </h3>
            <p className="numeric mt-2 text-2xl font-semibold text-expense">
              {formatMoney(summary.expenses, 'BRL')}
            </p>
            <p className="mt-2 text-xs leading-relaxed text-ink-muted">
              Total único de gasto efetivo; categorias detalham este mesmo total.
            </p>
          </article>

          <article className="rounded-card border border-line bg-surface p-4">
            <h3 className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
              Saldo atual consolidado
            </h3>
            <p className="numeric mt-2 text-2xl font-semibold text-ink">
              {formatMoney(summary.currentBalance, 'BRL')}
            </p>
            <div className="mt-2 space-y-1">
              <BalanceReference referenceAt={summary.currentBalanceReferenceAt} />
              <p className="text-xs leading-relaxed text-ink-muted">
                Somente contas bancárias, carteiras e dinheiro ativos.
              </p>
            </div>
          </article>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <article className="rounded-card border border-line bg-surface p-4">
            <h3 className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
              Cartões de crédito
            </h3>
            <p className="numeric mt-2 text-2xl font-semibold text-ink">
              {formatMoney(summary.cardOutstandingBalance ?? '0.00', 'BRL')}
            </p>
            <p className="mt-2 text-xs leading-relaxed text-ink-muted">
              Saldo devedor exibido como passivo separado. Não é somado ao saldo atual.
            </p>
            <Link
              to="/cards"
              className="mt-3 inline-flex min-h-11 items-center rounded-card border border-line bg-surface px-3 text-xs font-semibold text-ink transition-colors hover:bg-surface-muted"
            >
              Acompanhar cartões
            </Link>
          </article>
          <article className="rounded-card border border-line bg-surface p-4">
            <h3 className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
              Origem e sincronização
            </h3>
            <div className="mt-2 space-y-1">
              <SyncState lastSuccessfulSyncAt={summary.lastSuccessfulSyncAt} />
              <p className="text-xs leading-relaxed text-ink-muted">
                Importado e manual aparecem identificados no detalhamento de cada total.
              </p>
            </div>
          </article>
        </div>
      </section>

      <PendingTransactionsBlock summary={summary} />
      <InternalMatchesNotice count={summary.pendingInternalMatches} />

      {emptyPeriod ? <DashboardEmptyPeriod /> : null}

      <DashboardSection
        id="categories"
        title="Despesas por categoria"
        description="Gastos é a distribuição do mesmo total de despesas, não um total paralelo. Selecione uma categoria para conferir as entradas que a compõem."
      >
        {categoriesQuery.isLoading ? (
          <p role="status" className="text-sm text-ink-muted">
            Carregando categorias…
          </p>
        ) : null}
        {categoriesQuery.isError ? (
          <p role="alert" className="text-sm text-expense">
            Não foi possível carregar a distribuição por categoria.
          </p>
        ) : null}
        {!categoriesQuery.isLoading && !categoriesQuery.isError ? (
          <CategoryChart
            categories={categories}
            selectedCategoryId={selectedCategoryId}
            onDrillDown={drillCategory}
          />
        ) : null}
      </DashboardSection>

      <DashboardSection
        id="timeseries"
        title="Evolução temporal"
        description="Receitas, despesas e resultado por período calendário. Selecione um período para abrir o detalhamento."
      >
        {timeseriesQuery.isLoading ? (
          <p role="status" className="text-sm text-ink-muted">
            Carregando evolução temporal…
          </p>
        ) : null}
        {timeseriesQuery.isError ? (
          <p role="alert" className="text-sm text-expense">
            Não foi possível carregar a evolução temporal.
          </p>
        ) : null}
        {!timeseriesQuery.isLoading && !timeseriesQuery.isError ? (
          <TimeSeriesChart
            buckets={buckets}
            selectedStart={selectedBucketStart}
            onDrillDown={drillBucket}
          />
        ) : null}
      </DashboardSection>

      {drillDown ? (
        <DashboardTable
          title={drillDown.title}
          entries={entries}
          categoryNames={categoryNames}
          accountNames={accountNames}
          isLoading={entriesQuery.isLoading}
          isFetchingNextPage={entriesQuery.isFetchingNextPage}
          isError={entriesQuery.isError}
          hasMore={Boolean(entriesQuery.hasNextPage)}
          onLoadMore={() => void entriesQuery.fetchNextPage()}
          onClose={() => setDrillDown(null)}
        />
      ) : null}
    </div>
  )
}

function DashboardHeader({ onRefresh }: { onRefresh: () => void }) {
  return (
    <header className="space-y-2">
      <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">Relatórios</p>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Dashboard</h1>
          <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
            Resultado do período e saldo atual consolidado com rótulos e datas de referência
            distintos. Cada total pode ser conferido no detalhamento.
          </p>
        </div>
        <button type="button" onClick={onRefresh} className={dashboardActionClass}>
          Atualizar dados
        </button>
      </div>
    </header>
  )
}

function previousDay(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  date.setUTCDate(date.getUTCDate() - 1)
  return date.toISOString().slice(0, 10)
}
