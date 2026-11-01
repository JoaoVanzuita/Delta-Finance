import { ArrowRight, RefreshCw, ReceiptText } from 'lucide-react'
import { Link } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { CategoryIcon } from '@/features/categories/CategoryIcon'
import type { ReportEntry } from '@/features/dashboard/api'
import { formatDate, formatMoney } from '@/lib/format'

import { entryHref } from '../entry-link'

interface DashboardMovementsProps {
  readonly entries: readonly ReportEntry[]
  readonly accountNames: ReadonlyMap<string, string>
  readonly categoryNames: ReadonlyMap<string, string>
  readonly isLoading: boolean
  readonly isError: boolean
  readonly onOpenAll: () => void
  readonly onRetry: () => void
}

const sourceLabels: Record<string, string> = {
  TRANSACTION: 'Transação',
  INVOICE_ITEM: 'Item de fatura',
}

/**
 * Últimas movimentações do período com os mesmos filtros do Dashboard.
 * A lista é compacta; a origem e o detalhamento não competem com os valores.
 */
export function DashboardMovements({
  entries,
  accountNames,
  categoryNames,
  isLoading,
  isError,
  onOpenAll,
  onRetry,
}: DashboardMovementsProps) {
  return (
    <section aria-labelledby="movements-heading" className="space-y-3">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h2 id="movements-heading" className="text-section-title font-semibold text-ink">
            Últimas movimentações
          </h2>
          <p className="text-caption text-ink-muted">
            Mesmos filtros e período dos indicadores acima.
          </p>
        </div>
        <button
          type="button"
          onClick={onOpenAll}
          className="inline-flex min-h-11 items-center gap-1 rounded-control px-2 text-body-sm font-semibold text-action underline underline-offset-4 hover:text-action-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
        >
          Ver todas
          <ArrowRight aria-hidden="true" className="size-4" />
        </button>
      </div>

      {isLoading ? (
        <div className="space-y-1" role="status" aria-live="polite">
          <span className="sr-only">Carregando movimentações…</span>
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="flex items-center gap-3 py-3">
              <Skeleton className="size-9 rounded-full" />
              <div className="flex-1 space-y-1.5">
                <Skeleton className="h-4 w-2/5" />
                <Skeleton className="h-3 w-1/3" />
              </div>
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      ) : null}

      {isError ? (
        <div role="alert" className="rounded-card border border-expense/30 bg-expense-soft p-4">
          <p className="text-body-sm font-semibold text-ink">
            Não foi possível carregar as movimentações
          </p>
          <p className="mt-1 text-caption text-ink-muted">
            Os indicadores continuam disponíveis; tente novamente para ver os lançamentos.
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
        <p className="rounded-card border border-dashed border-line-strong bg-surface p-5 text-body-sm text-ink-muted">
          Nenhuma movimentação compõe os totais com os filtros atuais. Ajuste o período ou remova
          filtros.
        </p>
      ) : null}

      {!isLoading && !isError && entries.length > 0 ? (
        <ul className="divide-y divide-line rounded-card border border-line bg-surface">
          {entries.slice(0, 8).map((entry) => {
            const categoryName = entry.categoryId
              ? (categoryNames.get(entry.categoryId) ?? 'Sem categoria')
              : 'Sem categoria'
            const accountName = entry.accountId
              ? (accountNames.get(entry.accountId) ?? 'Conta')
              : undefined
            const amount = Number(entry.amount)
            return (
              <li key={`${entry.source}-${entry.id}`}>
                <Link
                  to={entryHref(entry)}
                  className="flex min-h-16 items-center gap-3 px-3 py-2.5 transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus sm:px-4"
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
                      {categoryName}
                      {accountName ? ` · ${accountName}` : ''}
                      {' · '}
                      {formatDate(entry.occurredOn)}
                    </p>
                  </div>
                  <div className="hidden shrink-0 sm:block">
                    {entry.source === 'INVOICE_ITEM' ? (
                      <Badge variant="info">
                        <ReceiptText aria-hidden="true" className="size-3" />
                        {sourceLabels[entry.source]}
                      </Badge>
                    ) : (
                      <Badge variant="neutral">
                        {entry.origin === 'IMPORTED' ? 'Importada' : 'Manual'}
                      </Badge>
                    )}
                  </div>
                  <span
                    className={[
                      'numeric shrink-0 text-right text-body-sm font-semibold',
                      amount < 0 ? 'text-expense' : 'text-income',
                    ].join(' ')}
                  >
                    {formatMoney(entry.amount, 'BRL')}
                  </span>
                </Link>
              </li>
            )
          })}
        </ul>
      ) : null}
    </section>
  )
}
