import { useMemo, useState } from 'react'

import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { MoreHorizontal, Plus, Search, SlidersHorizontal, X } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { buttonVariants } from '@/components/ui/button-variants'
import { IconButton } from '@/components/ui/icon-button'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/ui/page-header'
import { Popover } from '@/components/ui/popover'
import { Skeleton } from '@/components/ui/skeleton'
import { CategoryIcon } from '@/features/categories/CategoryIcon'
import {
  amountRangeError,
  filterCount,
  filtersFromSearchParams,
  normalizeAmountInput,
  searchParamsFromFilters,
  type TransactionFilterState,
} from '@/features/transactions/filters'
import { CorrectionDialog } from '@/features/transactions/components/CorrectionDialog'
import { TransactionDetailDrawer } from '@/features/transactions/components/TransactionDetailDrawer'
import { TransactionFiltersDrawer } from '@/features/transactions/components/TransactionFiltersDrawer'
import { formatDate, formatMoney, formatRelativeDate } from '@/lib/format'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'
import { ApiError } from '@/lib/api/problem'

import { financeApi, type Transaction } from '@/features/integration/api'

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  POSTED: 'Efetivada',
  SOURCE_REMOVED: 'Removida na origem',
  RECONCILED: 'Reconciliada',
}

const TYPE_LABELS: Record<string, string> = {
  DEBIT: 'Despesa',
  CREDIT: 'Receita',
  TRANSFER: 'Transferência',
  REFUND: 'Reembolso',
  CHARGEBACK: 'Chargeback',
  ADJUSTMENT: 'Ajuste',
}

const ORIGIN_LABELS: Record<string, string> = {
  IMPORTED: 'Importada',
  MANUAL: 'Manual',
}

/** Lista operacional de transações, com filtros na URL e detalhe em drawer. */
export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = useMemo(() => filtersFromSearchParams(searchParams), [searchParams])
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [detail, setDetail] = useState<Transaction | null>(null)
  const [correcting, setCorrecting] = useState<Transaction | null>(null)

  const accountsQuery = useQuery({ queryKey: queryKeys.accounts, queryFn: financeApi.accounts })
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories,
    queryFn: financeApi.categories,
  })
  const amountError = amountRangeError(filters.minAmount, filters.maxAmount)
  const normalizedFilters = useMemo(
    () => ({
      ...filters,
      minAmount: filters.minAmount ? normalizeAmountInput(filters.minAmount) : undefined,
      maxAmount: filters.maxAmount ? normalizeAmountInput(filters.maxAmount) : undefined,
    }),
    [filters],
  )

  const transactionsQuery = useInfiniteQuery({
    queryKey: queryKeys.transactionFilters(normalizedFilters),
    queryFn: ({ pageParam }) =>
      financeApi.transactions({ ...normalizedFilters, cursor: pageParam, limit: 50 }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore ? (last.nextCursor ?? undefined) : undefined),
    enabled: !amountError,
  })

  const accounts = accountsQuery.data?.items ?? []
  const categories = categoriesQuery.data?.items ?? []
  const accountNames = new Map(
    accounts.map((account) => [account.id, account.alias ?? account.institutionName ?? 'Conta']),
  )
  const categoryNames = new Map(categories.map((category) => [category.id, category.name] as const))
  const loaded = transactionsQuery.data?.pages.flatMap((page) => page.items) ?? []
  const searchTerm = search.trim().toLocaleLowerCase('pt-BR')
  const transactions = searchTerm
    ? loaded.filter((transaction) => {
        const category = transaction.categoryId
          ? (categoryNames.get(transaction.categoryId) ?? '')
          : 'Sem categoria'
        return [transaction.description, category, accountNames.get(transaction.accountId)]
          .filter(Boolean)
          .some((value) => String(value).toLocaleLowerCase('pt-BR').includes(searchTerm))
      })
    : loaded

  const ordered = [...transactions].sort(
    (left, right) => right.postedOn.localeCompare(left.postedOn) || right.id.localeCompare(left.id),
  )

  const groups = new Map<string, Transaction[]>()
  for (const transaction of ordered) {
    const current = groups.get(transaction.postedOn) ?? []
    current.push(transaction)
    groups.set(transaction.postedOn, current)
  }

  const activeFilters = filterCount(filters)
  const hasAnyFilter = activeFilters > 0

  function applyFilters(next: TransactionFilterState) {
    setSearchParams(
      searchParamsFromFilters({
        ...next,
        minAmount: next.minAmount ? normalizeAmountInput(next.minAmount) : undefined,
        maxAmount: next.maxAmount ? normalizeAmountInput(next.maxAmount) : undefined,
      }),
      { replace: false },
    )
    setDetail(null)
  }

  function removeFilter(key: keyof TransactionFilterState) {
    applyFilters({ ...filters, [key]: undefined })
  }

  function openCorrection(transaction: Transaction) {
    setDetail(null)
    setCorrecting(transaction)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Meu dinheiro"
        title="Transações"
        description="Movimentos importados e manuais. Pendentes aparecem no estado próprio e não entram nos totais oficiais."
        actions={
          <Link to="/transactions/new" className={cn(buttonVariants({ variant: 'primary' }))}>
            <Plus aria-hidden="true" className="size-4" />
            Novo lançamento
          </Link>
        }
      />

      <div className="space-y-3 rounded-panel border border-line bg-surface p-3 shadow-[var(--shadow-card)] sm:p-4">
        <div className="flex flex-wrap items-center gap-2">
          <label className="min-w-0 flex-1 text-body-sm">
            <span className="sr-only">Buscar nas transações carregadas</span>
            <span className="relative block">
              <Search
                aria-hidden="true"
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-muted"
              />
              <Input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Buscar carregadas"
                className="pl-9"
              />
            </span>
          </label>
          <Button
            type="button"
            variant="secondary"
            onClick={() => setFiltersOpen(true)}
            leadingIcon={<SlidersHorizontal aria-hidden="true" className="size-4" />}
          >
            Filtros
            {activeFilters > 0 ? (
              <span className="rounded-pill bg-primary px-2 py-0.5 text-caption font-semibold text-on-primary">
                {activeFilters}
                <span className="sr-only"> filtros ativos</span>
              </span>
            ) : null}
          </Button>
          {hasAnyFilter ? (
            <Button type="button" variant="ghost" onClick={() => applyFilters({})}>
              Limpar filtros
            </Button>
          ) : null}
        </div>

        {searchTerm ? (
          <p className="text-caption text-ink-muted" role="status">
            Busca local em {loaded.length} transação(ões) carregada(s)
            {transactionsQuery.hasNextPage ? '; carregue mais para ampliar o conjunto' : ''}.
            {transactions.length} resultado(s).
          </p>
        ) : null}

        {activeFilters > 0 ? (
          <div className="flex flex-wrap items-center gap-2 border-t border-line pt-3">
            <span className="text-caption font-medium text-ink-muted">
              {activeFilters} filtro(s) ativo(s)
            </span>
            {filters.accountId ? (
              <FilterChip
                label={`Conta: ${accountNames.get(filters.accountId) ?? 'Conta'}`}
                onRemove={() => removeFilter('accountId')}
              />
            ) : null}
            {filters.categoryId ? (
              <FilterChip
                label={`Categoria: ${categoryNames.get(filters.categoryId) ?? 'Categoria'}`}
                onRemove={() => removeFilter('categoryId')}
              />
            ) : null}
            {filters.status ? (
              <FilterChip
                label={`Estado: ${STATUS_LABELS[filters.status] ?? filters.status}`}
                onRemove={() => removeFilter('status')}
              />
            ) : null}
            {filters.type ? (
              <FilterChip
                label={`Tipo: ${TYPE_LABELS[filters.type] ?? filters.type}`}
                onRemove={() => removeFilter('type')}
              />
            ) : null}
            {filters.origin ? (
              <FilterChip
                label={`Origem: ${ORIGIN_LABELS[filters.origin] ?? filters.origin}`}
                onRemove={() => removeFilter('origin')}
              />
            ) : null}
            {filters.from ? (
              <FilterChip
                label={`De: ${formatDate(filters.from)}`}
                onRemove={() => removeFilter('from')}
              />
            ) : null}
            {filters.to ? (
              <FilterChip
                label={`Até: ${formatDate(filters.to)}`}
                onRemove={() => removeFilter('to')}
              />
            ) : null}
            {filters.minAmount ? (
              <FilterChip
                label={`Mínimo: ${formatMoney(filters.minAmount, 'BRL')}`}
                onRemove={() => removeFilter('minAmount')}
              />
            ) : null}
            {filters.maxAmount ? (
              <FilterChip
                label={`Máximo: ${formatMoney(filters.maxAmount, 'BRL')}`}
                onRemove={() => removeFilter('maxAmount')}
              />
            ) : null}
          </div>
        ) : null}

        {amountError ? (
          <p role="alert" className="text-body-sm text-expense">
            {amountError}
          </p>
        ) : null}
      </div>

      {transactionsQuery.isLoading ? (
        <div className="space-y-2" role="status" aria-live="polite">
          <span className="sr-only">Carregando transações…</span>
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="h-16 rounded-card" />
          ))}
        </div>
      ) : null}

      {transactionsQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar as transações">
          {transactionsQuery.error instanceof ApiError
            ? transactionsQuery.error.message
            : 'Tente novamente em instantes.'}
        </Alert>
      ) : null}

      {transactionsQuery.isSuccess && transactions.length === 0 ? (
        <div className="rounded-card border border-dashed border-line-strong bg-surface p-6">
          <h2 className="text-section-title font-semibold text-ink">
            {searchTerm
              ? 'Nenhum resultado na busca local'
              : hasAnyFilter
                ? 'Nenhuma transação com estes filtros'
                : 'Nenhuma transação ainda'}
          </h2>
          <p className="mt-1 max-w-prose text-body-sm text-ink-muted">
            {searchTerm
              ? 'A busca considera somente os registros já carregados. Limpe a busca ou carregue mais transações.'
              : hasAnyFilter
                ? 'Remova os filtros para consultar todos os movimentos disponíveis.'
                : 'Importe uma conexão bancária ou registre um lançamento manual para começar.'}
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            {hasAnyFilter ? (
              <Button type="button" variant="secondary" onClick={() => applyFilters({})}>
                Limpar filtros
              </Button>
            ) : (
              <Link to="/transactions/new" className={cn(buttonVariants({ variant: 'primary' }))}>
                Novo lançamento
              </Link>
            )}
          </div>
        </div>
      ) : null}

      {transactions.length > 0 ? (
        <>
          <p role="status" className="text-caption text-ink-muted">
            {transactions.length} transação(ões) exibida(s)
            {searchTerm ? ` de ${loaded.length} carregada(s)` : ''}.
          </p>
          <div className="space-y-5">
            {Array.from(groups.entries()).map(([date, dayTransactions]) => (
              <section key={date} aria-labelledby={`day-${date}`} className="space-y-2">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h2 id={`day-${date}`} className="text-body-sm font-semibold text-ink">
                    {formatRelativeDate(date)}
                    <span className="ml-2 font-normal text-ink-muted">{formatDate(date)}</span>
                  </h2>
                  <span className="text-caption text-ink-muted">
                    {dayTransactions.length} lançamento(s)
                  </span>
                </div>
                <ul className="divide-y divide-line rounded-card border border-line bg-surface">
                  {dayTransactions.map((transaction) => (
                    <TransactionRow
                      key={transaction.id}
                      transaction={transaction}
                      accountName={accountNames.get(transaction.accountId) ?? 'Conta'}
                      categoryName={
                        transaction.categoryId
                          ? (categoryNames.get(transaction.categoryId) ?? 'Sem categoria')
                          : 'Sem categoria'
                      }
                      onDetails={() => setDetail(transaction)}
                      onCorrect={() => openCorrection(transaction)}
                    />
                  ))}
                </ul>
              </section>
            ))}
          </div>

          {transactionsQuery.hasNextPage ? (
            <Button
              type="button"
              variant="secondary"
              onClick={() => void transactionsQuery.fetchNextPage()}
              loading={transactionsQuery.isFetchingNextPage}
              loadingLabel="Carregando…"
            >
              Carregar mais
            </Button>
          ) : null}
        </>
      ) : null}

      {filtersOpen ? (
        <TransactionFiltersDrawer
          open
          onClose={() => setFiltersOpen(false)}
          value={filters}
          accounts={accounts}
          categories={categories}
          onApply={applyFilters}
        />
      ) : null}

      <TransactionDetailDrawer
        open={Boolean(detail)}
        transaction={detail}
        onClose={() => setDetail(null)}
        accountName={detail ? (accountNames.get(detail.accountId) ?? 'Conta') : undefined}
        categoryName={
          detail?.categoryId
            ? (categoryNames.get(detail.categoryId) ?? 'Sem categoria')
            : 'Sem categoria'
        }
        onCorrect={openCorrection}
      />

      {correcting ? (
        <CorrectionDialog transaction={correcting} onClose={() => setCorrecting(null)} />
      ) : null}
    </div>
  )
}

function TransactionRow({
  transaction,
  accountName,
  categoryName,
  onDetails,
  onCorrect,
}: {
  readonly transaction: Transaction
  readonly accountName: string
  readonly categoryName: string
  readonly onDetails: () => void
  readonly onCorrect: () => void
}) {
  return (
    <li className="flex items-center gap-2 px-2 py-1.5 sm:px-3">
      <button
        type="button"
        onClick={onDetails}
        className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-control px-1 text-left transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:px-2"
      >
        <CategoryIcon
          id={transaction.categoryId ?? '00000000-0000-0000-0000-000000000000'}
          name={categoryName}
          size="sm"
        />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-body-sm font-medium text-ink">
            {transaction.description || 'Sem descrição'}
          </span>
          <span className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-caption text-ink-muted">
            <span>{categoryName}</span>
            <span aria-hidden="true">·</span>
            <span>{accountName}</span>
            <span aria-hidden="true">·</span>
            <span>{TYPE_LABELS[transaction.type] ?? transaction.type}</span>
          </span>
        </span>
        <span className="hidden shrink-0 sm:block">
          <Badge variant={transaction.status === 'PENDING' ? 'warning' : 'neutral'}>
            {STATUS_LABELS[transaction.status] ?? transaction.status}
          </Badge>
        </span>
        <span
          className={cn(
            'numeric shrink-0 text-right text-body-sm font-semibold',
            Number(transaction.amount) < 0 ? 'text-expense' : 'text-income',
          )}
        >
          {formatMoney(transaction.amount, transaction.currency)}
        </span>
      </button>

      <Popover
        label={`Ações de ${transaction.description || 'transação'}`}
        align="end"
        trigger={({ toggle, triggerRef, open, id }) => (
          <IconButton
            ref={triggerRef}
            label={`Ações de ${transaction.description || 'transação'}`}
            onClick={toggle}
            aria-expanded={open}
            aria-controls={id}
          >
            <MoreHorizontal aria-hidden="true" className="size-5" />
          </IconButton>
        )}
      >
        {({ close }) => (
          <ul className="space-y-1">
            <li>
              <button
                type="button"
                onClick={() => {
                  close()
                  onDetails()
                }}
                className="flex min-h-11 w-full items-center rounded-control px-3 text-left text-body-sm text-ink hover:bg-surface-muted"
              >
                Ver detalhes
              </button>
            </li>
            <li>
              <button
                type="button"
                onClick={() => {
                  close()
                  onCorrect()
                }}
                className="flex min-h-11 w-full items-center rounded-control px-3 text-left text-body-sm text-ink hover:bg-surface-muted"
              >
                Corrigir transação
              </button>
            </li>
          </ul>
        )}
      </Popover>
    </li>
  )
}

function FilterChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex min-h-11 items-center gap-1 rounded-pill border border-line bg-surface-muted py-1 pr-1 pl-3 text-caption font-medium text-ink sm:min-h-9">
      {label}
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Remover filtro ${label}`}
        className="inline-flex size-11 items-center justify-center rounded-pill text-ink-muted hover:bg-surface hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:size-7"
      >
        <X aria-hidden="true" className="size-3.5" />
      </button>
    </span>
  )
}
