import { useMemo, useState } from 'react'

import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { useSearchParams } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/ui/page-header'
import { Tab, TabList, TabPanel, Tabs } from '@/components/ui/tabs'
import {
  dashboardApi,
  type DashboardBucket,
  type DashboardCategory,
  type DashboardFilters as DashboardFiltersValue,
  type DashboardPeriod,
} from '@/features/dashboard/api'
import {
  CategoryChart,
  type CategoryDirection,
} from '@/features/dashboard/components/CategoryChart'
import {
  DashboardLiveRegion,
  DashboardSection,
} from '@/features/dashboard/components/DashboardAccessibility'
import { DashboardAttention } from '@/features/dashboard/components/DashboardAttention'
import { DashboardCategoryDrawer } from '@/features/dashboard/components/DashboardCategoryDrawer'
import { DashboardDrillDownDrawer } from '@/features/dashboard/components/DashboardDrillDownDrawer'
import { DashboardFilters } from '@/features/dashboard/components/DashboardFilters'
import { DashboardMovements } from '@/features/dashboard/components/DashboardMovements'
import { DashboardOverview } from '@/features/dashboard/components/DashboardOverview'
import {
  DashboardEmptyPeriod,
  DashboardError,
  DashboardLoading,
  DashboardPartialError,
} from '@/features/dashboard/components/DashboardStates'
import { TimeSeriesChart } from '@/features/dashboard/components/TimeSeriesChart'
import { categoryDisplayName, formatPeriodLabel } from '@/features/dashboard/format'
import { financeApi } from '@/features/integration/api'
import { ApiError } from '@/lib/api/problem'
import { formatMoney, formatPercent, toFiniteNumber } from '@/lib/format'

interface DrillDown {
  readonly kind: 'category' | 'period'
  readonly title: string
  readonly description: string
  readonly filters: DashboardFiltersValue
}

const PERIODS: readonly DashboardPeriod[] = ['WEEK', 'MONTH', 'YEAR']

function isPeriod(value: string | null): value is DashboardPeriod {
  return value !== null && (PERIODS as readonly string[]).includes(value)
}

/**
 * Interpreta a URL. Filtros são a fonte de verdade para que voltar, avançar e
 * recarregar preservem período, contas, categoria, tipo, origem e faixa.
 */
function filtersFromSearchParams(searchParams: URLSearchParams): DashboardFiltersValue {
  const period = searchParams.get('period')
  const accountIds = searchParams.getAll('accountId')
  return {
    period: isPeriod(period) ? period : 'MONTH',
    from: searchParams.get('from') || undefined,
    to: searchParams.get('to') || undefined,
    accountIds: accountIds.length > 0 ? accountIds : undefined,
    categoryId: searchParams.get('categoryId') || undefined,
    type: searchParams.get('type') || undefined,
    origin: searchParams.get('origin') || undefined,
    minAmount: searchParams.get('minAmount') || undefined,
    maxAmount: searchParams.get('maxAmount') || undefined,
  }
}

function searchParamsFromFilters(filters: DashboardFiltersValue): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.period) params.set('period', filters.period)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  for (const accountId of filters.accountIds ?? []) params.append('accountId', accountId)
  if (filters.categoryId) params.set('categoryId', filters.categoryId)
  if (filters.type) params.set('type', filters.type)
  if (filters.origin) params.set('origin', filters.origin)
  if (filters.minAmount) params.set('minAmount', filters.minAmount)
  if (filters.maxAmount) params.set('maxAmount', filters.maxAmount)
  return params
}

function hasAdvancedFilters(filters: DashboardFiltersValue): boolean {
  return Boolean(
    (filters.accountIds ?? []).length > 0 ||
    filters.categoryId ||
    filters.type ||
    filters.origin ||
    filters.minAmount ||
    filters.maxAmount ||
    filters.from ||
    filters.to,
  )
}

/**
 * Dashboard: resultado, indicadores, atenção, evolução, categorias e últimas
 * movimentações. Cada total vem do endpoint de reporting; o frontend apenas
 * formata, organiza e abre o drill-down correspondente.
 */
export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const filters = useMemo(() => filtersFromSearchParams(searchParams), [searchParams])
  const period = filters.period ?? 'MONTH'

  const [drillDown, setDrillDown] = useState<DrillDown | null>(null)
  const [categoryDirection, setCategoryDirection] = useState<CategoryDirection>('EXPENSE')
  const [categoryDrawerOpen, setCategoryDrawerOpen] = useState(false)
  const [categoryDrawerDirection, setCategoryDrawerDirection] =
    useState<CategoryDirection>('EXPENSE')
  const [recentDrawerOpen, setRecentDrawerOpen] = useState(false)

  const summaryQuery = useQuery({
    queryKey: ['dashboard-summary', filters],
    queryFn: () => dashboardApi.summary(filters),
  })
  const categoriesQuery = useQuery({
    queryKey: ['dashboard-categories', filters, 'EXPENSE'],
    queryFn: () => dashboardApi.categories(filters, 'EXPENSE'),
  })
  const incomeCategoriesQuery = useQuery({
    queryKey: ['dashboard-categories', filters, 'INCOME'],
    queryFn: () => dashboardApi.categories(filters, 'INCOME'),
  })
  const timeseriesQuery = useQuery({
    queryKey: ['dashboard-timeseries', filters],
    queryFn: () => dashboardApi.timeseries(filters),
  })
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })
  const categoryListQuery = useQuery({ queryKey: ['categories'], queryFn: financeApi.categories })
  const syncStatusQuery = useQuery({ queryKey: ['syncStatus'], queryFn: financeApi.syncStatus })

  const recentQuery = useInfiniteQuery({
    queryKey: ['dashboard-entries', 'recent', filters],
    queryFn: ({ pageParam }) => dashboardApi.entries(filters, pageParam, 10),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore ? (last.nextCursor ?? undefined) : undefined),
  })

  const drillFilters = drillDown?.filters
  const drillQuery = useInfiniteQuery({
    queryKey: ['dashboard-entries', 'drill', drillFilters],
    queryFn: ({ pageParam }) => dashboardApi.entries(drillFilters ?? {}, pageParam, 25),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore ? (last.nextCursor ?? undefined) : undefined),
    enabled: drillDown !== null,
  })

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

  const summary = summaryQuery.data
  const categories = categoriesQuery.data ?? []
  const incomeCategories = incomeCategoriesQuery.data ?? []
  const buckets = timeseriesQuery.data ?? []
  const recentEntries = recentQuery.data?.pages.flatMap((page) => page.items) ?? []
  const drillEntries = drillQuery.data?.pages.flatMap((page) => page.items) ?? []
  const emptyPeriod =
    summary !== undefined &&
    Number(summary.income) === 0 &&
    Number(summary.expenses) === 0 &&
    categories.length === 0 &&
    (summary.pendingCount ?? 0) === 0 &&
    !recentQuery.isLoading &&
    recentEntries.length === 0
  const filtered = hasAdvancedFilters(filters)
  const periodLabel = summary
    ? formatPeriodLabel(summary.periodStart, summary.periodEnd)
    : 'Período selecionado'

  const announcement = summaryQuery.isFetching
    ? 'Atualizando dashboard…'
    : summary
      ? `Dashboard atualizado. Receitas ${formatMoney(summary.income, 'BRL')}, despesas ${formatMoney(summary.expenses, 'BRL')}, resultado ${formatMoney(summary.periodResult, 'BRL')}.`
      : 'Carregando dashboard…'

  function applyFilters(next: DashboardFiltersValue) {
    setSearchParams(searchParamsFromFilters(next), { replace: false })
    setDrillDown(null)
    setCategoryDrawerOpen(false)
    setRecentDrawerOpen(false)
  }

  function clearFilters() {
    applyFilters({ period: 'MONTH' })
  }

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-categories'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-timeseries'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard-entries'] })
    void queryClient.invalidateQueries({ queryKey: ['syncStatus'] })
  }

  function drillCategory(category: DashboardCategory, direction: CategoryDirection) {
    const source = direction === 'INCOME' ? incomeCategories : categories
    const total = source.reduce((sum, item) => sum + toFiniteNumber(item.amount), 0)
    const share = total > 0 ? (toFiniteNumber(category.amount) / total) * 100 : 0
    const noun = direction === 'INCOME' ? 'receitas' : 'despesas'
    setCategoryDrawerOpen(false)
    setDrillDown({
      kind: 'category',
      title: `Categoria: ${categoryDisplayName(category.categoryName, direction)}`,
      description: `${formatMoney(category.amount, 'BRL')} em ${category.transactionCount} lançamento(s) · ${formatPercent(share, { maximumFractionDigits: 1 })} das ${noun}`,
      filters: { ...filters, categoryId: category.categoryId, direction },
    })
  }

  function drillBucket(bucket: DashboardBucket) {
    setDrillDown({
      kind: 'period',
      title: `Período: ${bucket.label}`,
      description: `Receitas ${formatMoney(bucket.income, 'BRL')} · Despesas ${formatMoney(bucket.expenses, 'BRL')} · Resultado ${formatMoney(bucket.result, 'BRL')}`,
      filters: { ...filters, from: bucket.start, to: previousDay(bucket.end) },
    })
  }

  const header = (
    <PageHeader
      overline="Visão geral"
      title="Dashboard"
      description="Sua situação financeira no período, com cada total conferível no detalhamento."
      meta={
        <>
          <Badge variant="neutral">{periodLabel}</Badge>
          <Badge variant="neutral">Valores em BRL</Badge>
        </>
      }
      actions={
        <Button
          type="button"
          variant="secondary"
          onClick={refresh}
          loading={summaryQuery.isFetching}
          loadingLabel="Atualizando…"
          leadingIcon={<RefreshCw aria-hidden="true" className="size-4" />}
        >
          Atualizar dados
        </Button>
      }
    />
  )

  const filtersBar = (
    <DashboardFilters
      value={filters}
      period={period}
      accounts={accountsQuery.data?.items ?? []}
      categories={categoryListQuery.data?.items ?? []}
      onApply={applyFilters}
    />
  )

  if (summaryQuery.isLoading) {
    return (
      <div className="space-y-5">
        {header}
        {filtersBar}
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
      <div className="space-y-5">
        {header}
        {filtersBar}
        <DashboardError message={message} onRetry={() => void summaryQuery.refetch()} />
      </div>
    )
  }

  const nameCoverageIssue =
    categoriesQuery.isSuccess && (accountsQuery.isError || categoryListQuery.isError)

  return (
    <div className="space-y-6">
      {header}
      <DashboardLiveRegion message={announcement} />
      {filtersBar}

      {nameCoverageIssue ? (
        <DashboardPartialError
          title="Alguns nomes podem aparecer incompletos"
          message="Não foi possível carregar todas as contas ou categorias. Os totais continuam corretos; o detalhamento usa rótulos genéricos quando necessário."
          onRetry={() => {
            void accountsQuery.refetch()
            void categoryListQuery.refetch()
          }}
        />
      ) : null}

      <DashboardOverview summary={summary} periodLabel={periodLabel} />
      <DashboardAttention
        summary={summary}
        syncStatus={syncStatusQuery.data}
        syncError={syncStatusQuery.isError}
        syncLoading={syncStatusQuery.isLoading}
        onRetrySync={() => void syncStatusQuery.refetch()}
      />

      {emptyPeriod ? (
        <DashboardEmptyPeriod filtered={filtered} onClearFilters={clearFilters} />
      ) : (
        <>
          <DashboardSection
            id="timeseries"
            title="Evolução financeira"
            description="Receitas, despesas e resultado por período calendário. Selecione uma série ou um ponto para detalhar."
          >
            {timeseriesQuery.isLoading ? (
              <p role="status" className="text-body-sm text-ink-muted">
                Carregando evolução temporal…
              </p>
            ) : null}
            {timeseriesQuery.isError ? (
              <DashboardPartialError
                title="Evolução temporal indisponível"
                message="Os indicadores acima continuam válidos. Tente carregar a série novamente."
                onRetry={() => void timeseriesQuery.refetch()}
              />
            ) : null}
            {timeseriesQuery.isSuccess ? (
              <TimeSeriesChart
                buckets={buckets}
                selectedStart={drillDown?.filters.from}
                onDrillDown={drillBucket}
              />
            ) : null}
          </DashboardSection>

          <DashboardSection
            id="categories"
            title="Categorias"
            description="Composição de despesas e receitas do período. Categorias usadas nos dois sentidos aparecem como saídas ou entradas conforme a aba. Selecione uma categoria para ver os lançamentos que a compõem."
          >
            <Tabs
              value={categoryDirection}
              onValueChange={(value) => setCategoryDirection(value as CategoryDirection)}
            >
              <TabList label="Categorias de despesas e receitas">
                <Tab value="EXPENSE">Despesas</Tab>
                <Tab value="INCOME">Receitas</Tab>
              </TabList>

              <TabPanel value="EXPENSE">
                {categoriesQuery.isLoading ? (
                  <p role="status" className="text-body-sm text-ink-muted">
                    Carregando categorias…
                  </p>
                ) : null}
                {categoriesQuery.isError ? (
                  <DashboardPartialError
                    title="Distribuição de despesas indisponível"
                    message="O total de despesas continua válido. Tente carregar a distribuição novamente."
                    onRetry={() => void categoriesQuery.refetch()}
                  />
                ) : null}
                {categoriesQuery.isSuccess ? (
                  <CategoryChart
                    categories={categories}
                    direction="EXPENSE"
                    selectedCategoryId={drillDown?.filters.categoryId}
                    onDrillDown={(category) => drillCategory(category, 'EXPENSE')}
                    onShowAll={() => {
                      setCategoryDrawerDirection('EXPENSE')
                      setCategoryDrawerOpen(true)
                    }}
                  />
                ) : null}
              </TabPanel>

              <TabPanel value="INCOME">
                {incomeCategoriesQuery.isLoading ? (
                  <p role="status" className="text-body-sm text-ink-muted">
                    Carregando receitas…
                  </p>
                ) : null}
                {incomeCategoriesQuery.isError ? (
                  <DashboardPartialError
                    title="Distribuição de receitas indisponível"
                    message="O total de receitas continua válido. Tente carregar a distribuição novamente."
                    onRetry={() => void incomeCategoriesQuery.refetch()}
                  />
                ) : null}
                {incomeCategoriesQuery.isSuccess ? (
                  <CategoryChart
                    categories={incomeCategories}
                    direction="INCOME"
                    selectedCategoryId={drillDown?.filters.categoryId}
                    onDrillDown={(category) => drillCategory(category, 'INCOME')}
                    onShowAll={() => {
                      setCategoryDrawerDirection('INCOME')
                      setCategoryDrawerOpen(true)
                    }}
                  />
                ) : null}
              </TabPanel>
            </Tabs>
          </DashboardSection>

          <DashboardMovements
            entries={recentEntries}
            accountNames={accountNames}
            categoryNames={categoryNames}
            isLoading={recentQuery.isLoading}
            isError={recentQuery.isError}
            onOpenAll={() => setRecentDrawerOpen(true)}
            onRetry={() => void recentQuery.refetch()}
          />
        </>
      )}

      <DashboardCategoryDrawer
        open={categoryDrawerOpen}
        onClose={() => setCategoryDrawerOpen(false)}
        categories={categoryDrawerDirection === 'INCOME' ? incomeCategories : categories}
        direction={categoryDrawerDirection}
        onSelect={(category) => drillCategory(category, categoryDrawerDirection)}
      />

      <DashboardDrillDownDrawer
        open={Boolean(drillDown)}
        onClose={() => setDrillDown(null)}
        title={drillDown?.title ?? 'Detalhamento'}
        description={drillDown?.description}
        entries={drillEntries}
        accountNames={accountNames}
        categoryNames={categoryNames}
        isLoading={drillQuery.isLoading}
        isError={drillQuery.isError}
        isFetchingNextPage={drillQuery.isFetchingNextPage}
        hasMore={Boolean(drillQuery.hasNextPage)}
        onLoadMore={() => void drillQuery.fetchNextPage()}
        onRetry={() => void drillQuery.refetch()}
      />

      <DashboardDrillDownDrawer
        open={recentDrawerOpen}
        onClose={() => setRecentDrawerOpen(false)}
        title="Últimas movimentações"
        description={`${recentEntries.length} entrada(s) carregada(s) com os filtros atuais.`}
        entries={recentEntries}
        accountNames={accountNames}
        categoryNames={categoryNames}
        isLoading={recentQuery.isLoading}
        isError={recentQuery.isError}
        isFetchingNextPage={recentQuery.isFetchingNextPage}
        hasMore={Boolean(recentQuery.hasNextPage)}
        onLoadMore={() => void recentQuery.fetchNextPage()}
        onRetry={() => void recentQuery.refetch()}
      />
    </div>
  )
}

function previousDay(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  date.setUTCDate(date.getUTCDate() - 1)
  return date.toISOString().slice(0, 10)
}
