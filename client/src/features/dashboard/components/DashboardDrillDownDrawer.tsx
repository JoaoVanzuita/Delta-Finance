import { ArrowUpRight, RefreshCw } from 'lucide-react'
import { Link } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Drawer } from '@/components/ui/drawer'
import { Skeleton } from '@/components/ui/skeleton'
import { CategoryIcon } from '@/features/categories/CategoryIcon'
import type { ReportEntry } from '@/features/dashboard/api'
import { formatDate, formatMoney } from '@/lib/format'

import { entryHref } from '../entry-link'

interface DashboardDrillDownDrawerProps {
  readonly open: boolean
  readonly onClose: () => void
  readonly title: string
  readonly description?: string
  readonly entries: readonly ReportEntry[]
  readonly accountNames: ReadonlyMap<string, string>
  readonly categoryNames: ReadonlyMap<string, string>
  readonly isLoading: boolean
  readonly isError: boolean
  readonly isFetchingNextPage: boolean
  readonly hasMore: boolean
  readonly onLoadMore: () => void
  readonly onRetry: () => void
}

/** Detalhamento contextual sem perder período, filtros ou posição do Dashboard. */
export function DashboardDrillDownDrawer({
  open,
  onClose,
  title,
  description,
  entries,
  accountNames,
  categoryNames,
  isLoading,
  isError,
  isFetchingNextPage,
  hasMore,
  onLoadMore,
  onRetry,
}: DashboardDrillDownDrawerProps) {
  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={title}
      description={description ?? 'Entradas que compõem este total no período selecionado.'}
      width="lg"
      bodyClassName="space-y-4"
      footer={
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p role="status" className="text-caption text-ink-muted">
            {entries.length} entrada(s) carregada(s)
          </p>
          {hasMore ? (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              loading={isFetchingNextPage}
              loadingLabel="Carregando…"
              onClick={onLoadMore}
            >
              Carregar mais
            </Button>
          ) : null}
        </div>
      }
    >
      {isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <span className="sr-only">Carregando detalhamento…</span>
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="flex items-center gap-3">
              <Skeleton className="size-9 rounded-full" />
              <div className="flex-1 space-y-1.5">
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="h-3 w-1/3" />
              </div>
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      ) : null}

      {isError ? (
        <div role="alert" className="rounded-card border border-expense/30 bg-expense-soft p-4">
          <p className="text-body-sm font-semibold text-ink">Não foi possível carregar o detalhe</p>
          <p className="mt-1 text-caption text-ink-muted">
            O total permanece disponível. Tente novamente sem fechar o detalhamento.
          </p>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            className="mt-3"
            onClick={onRetry}
            leadingIcon={<RefreshCw aria-hidden="true" className="size-4" />}
          >
            Tentar novamente
          </Button>
        </div>
      ) : null}

      {!isLoading && !isError && entries.length === 0 ? (
        <p className="rounded-card border border-dashed border-line-strong p-5 text-body-sm text-ink-muted">
          Nenhuma entrada compõe este total com os filtros atuais.
        </p>
      ) : null}

      {!isLoading && !isError && entries.length > 0 ? (
        <ul className="divide-y divide-line rounded-card border border-line">
          {entries.map((entry) => {
            const categoryName = entry.categoryId
              ? (categoryNames.get(entry.categoryId) ?? 'Sem categoria')
              : 'Sem categoria'
            const accountName = entry.accountId
              ? (accountNames.get(entry.accountId) ?? 'Conta')
              : undefined
            const amount = Number(entry.amount)
            return (
              <li key={`${entry.source}-${entry.id}`} className="flex items-stretch">
                <Link
                  to={entryHref(entry)}
                  className="flex min-w-0 flex-1 items-center gap-3 px-3 py-3 transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus"
                >
                  <CategoryIcon
                    id={entry.categoryId ?? '00000000-0000-0000-0000-000000000000'}
                    name={categoryName}
                    size="sm"
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-body-sm font-medium text-ink">
                      {entry.description || 'Sem descrição'}
                    </p>
                    <p className="mt-0.5 truncate text-caption text-ink-muted">
                      {formatDate(entry.occurredOn)}
                      {accountName ? ` · ${accountName}` : ''} · {categoryName}
                    </p>
                    <div className="mt-1 flex flex-wrap gap-1">
                      <Badge variant="neutral">
                        {entry.origin === 'IMPORTED' ? 'Importada' : 'Manual'}
                      </Badge>
                      {entry.source === 'INVOICE_ITEM' ? (
                        <Badge variant="info">Item de fatura</Badge>
                      ) : null}
                    </div>
                  </div>
                  <span
                    className={[
                      'numeric shrink-0 text-right text-body-sm font-semibold',
                      amount < 0 ? 'text-expense' : 'text-income',
                    ].join(' ')}
                  >
                    {formatMoney(entry.amount, 'BRL')}
                  </span>
                  <ArrowUpRight aria-hidden="true" className="size-4 shrink-0 text-ink-muted" />
                </Link>
              </li>
            )
          })}
        </ul>
      ) : null}
    </Drawer>
  )
}
