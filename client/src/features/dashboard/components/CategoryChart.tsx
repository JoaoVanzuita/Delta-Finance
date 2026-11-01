import { ListFilter } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { categoryVisual, otherCategoryVisual } from '@/features/categories/visuals'
import type { DashboardCategory } from '@/features/dashboard/api'
import { categoryDisplayName } from '@/features/dashboard/format'
import { buildDonutGradient } from '@/lib/donut'
import { addDecimals, formatMoney, formatPercent, toFiniteNumber } from '@/lib/format'

export type CategoryDirection = 'EXPENSE' | 'INCOME'

interface CategoryChartProps {
  readonly categories: readonly DashboardCategory[]
  readonly direction?: CategoryDirection
  readonly selectedCategoryId?: string
  readonly onDrillDown: (category: DashboardCategory) => void
  readonly onShowAll: () => void
}

interface CategorySlice {
  readonly key: string
  readonly name: string
  readonly canonicalName: string
  readonly amount: string
  readonly transactionCount: number
  readonly category?: DashboardCategory
  readonly other?: boolean
}

const TOP_LIMIT = 8

/**
 * Despesas ou receitas por categoria: sempre donut, com top 8 + Outras quando
 * necessário. A tabela recolhível contém a lista integral e é a alternativa
 * acessível ao gráfico.
 */
export function CategoryChart({
  categories,
  direction = 'EXPENSE',
  selectedCategoryId,
  onDrillDown,
  onShowAll,
}: CategoryChartProps) {
  const income = direction === 'INCOME'
  const noun = income ? 'receitas' : 'despesas'

  if (categories.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-body-sm text-ink-muted">
        Sem {noun} por categoria no período selecionado.
      </p>
    )
  }

  const total = addDecimals(categories.map((category) => category.amount))
  const totalNumber = toFiniteNumber(total)
  const top = categories.slice(0, TOP_LIMIT)
  const remainder = categories.slice(TOP_LIMIT)
  const slices: CategorySlice[] = top.map((category) => ({
    key: category.categoryId,
    name: categoryDisplayName(category.categoryName, direction),
    canonicalName: category.categoryName,
    amount: category.amount,
    transactionCount: category.transactionCount,
    category,
  }))
  if (remainder.length > 0) {
    const label = `Outras ${remainder.length} ${remainder.length === 1 ? 'categoria' : 'categorias'}`
    slices.push({
      key: 'other',
      name: label,
      canonicalName: label,
      amount: addDecimals(remainder.map((category) => category.amount)),
      transactionCount: remainder.reduce((sum, category) => sum + category.transactionCount, 0),
      other: true,
    })
  }

  const donutSegments = buildDonutGradient(
    slices.map((slice) => ({
      value: toFiniteNumber(slice.amount),
      color: sliceColor(slice),
    })),
    totalNumber,
  )

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-caption text-ink-muted">Total de {noun} no período</p>
          <p className="numeric text-metric-md font-semibold text-ink">
            {formatMoney(total, 'BRL')}
          </p>
        </div>
        <p className="text-caption text-ink-muted">{categories.length} categoria(s)</p>
      </div>

      <div className="grid gap-5 md:grid-cols-[minmax(0,15rem)_minmax(0,1fr)] md:items-center">
        <figure className="mx-auto w-full max-w-[15rem]">
          <div
            role="img"
            aria-label={`Composição de ${noun} por categoria. Total ${formatMoney(total, 'BRL')}.`}
            className="relative aspect-square w-full rounded-full"
            style={{ background: donutSegments }}
          >
            <div className="absolute inset-[22%] flex flex-col items-center justify-center rounded-full bg-surface text-center">
              <span className="text-caption text-ink-muted">
                {income ? 'Receitas' : 'Despesas'}
              </span>
              <span className="numeric text-body-sm font-semibold text-ink">
                {formatMoney(total, 'BRL')}
              </span>
            </div>
          </div>
          <figcaption className="sr-only">
            Distribuição percentual das {noun} por categoria. A lista abaixo repete os mesmos
            valores.
          </figcaption>
        </figure>

        <ul className="space-y-1" aria-label={`${income ? 'Receitas' : 'Despesas'} por categoria`}>
          {slices.map((slice) => (
            <li key={slice.key}>
              <CategoryRow
                slice={slice}
                totalNumber={totalNumber}
                selected={
                  Boolean(slice.category) && selectedCategoryId === slice.category?.categoryId
                }
                onSelect={() => {
                  if (slice.category) onDrillDown(slice.category)
                  else onShowAll()
                }}
              />
            </li>
          ))}
        </ul>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-line pt-4">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={onShowAll}
          leadingIcon={<ListFilter aria-hidden="true" className="size-4" />}
        >
          Ver todas as categorias
        </Button>
      </div>

      <details className="rounded-card border border-line">
        <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
          Ver tabela completa de {noun} por categoria
        </summary>
        <Table caption={`${income ? 'Receitas' : 'Despesas'} por categoria no período selecionado`}>
          <THead>
            <TR>
              <TH>Categoria</TH>
              <TH className="text-right">Valor</TH>
              <TH className="text-right">Participação</TH>
              <TH className="text-right">Lançamentos</TH>
            </TR>
          </THead>
          <TBody>
            {categories.map((category) => (
              <TR key={category.categoryId}>
                <TD>
                  <span className="inline-flex items-center gap-2">
                    <CategoryIcon id={category.categoryId} name={category.categoryName} />
                    {categoryDisplayName(category.categoryName, direction)}
                  </span>
                </TD>
                <TD className="numeric text-right">{formatMoney(category.amount, 'BRL')}</TD>
                <TD className="numeric text-right text-ink-muted">
                  {formatPercent(share(category.amount, totalNumber), {
                    maximumFractionDigits: 1,
                  })}
                </TD>
                <TD className="numeric text-right text-ink-muted">{category.transactionCount}</TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </details>
    </div>
  )
}

function CategoryRow({
  slice,
  totalNumber,
  selected,
  onSelect,
}: {
  readonly slice: CategorySlice
  readonly totalNumber: number
  readonly selected: boolean
  readonly onSelect: () => void
}) {
  const visual = slice.other
    ? otherCategoryVisual()
    : categoryVisual({ id: slice.key, name: slice.canonicalName })
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onSelect}
      className={[
        'flex w-full min-h-11 items-center gap-2 rounded-control px-2 text-left transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
        selected ? 'bg-primary-soft' : 'hover:bg-surface-muted',
      ].join(' ')}
    >
      <span className={`size-2.5 shrink-0 rounded-full ${visual.dotClass}`} aria-hidden="true" />
      <visual.icon aria-hidden="true" className={`size-4 shrink-0 ${visual.textClass}`} />
      <span className="min-w-0 flex-1 truncate text-body-sm font-medium text-ink">
        {slice.name}
      </span>
      <span className="numeric text-body-sm font-semibold text-ink">
        {formatMoney(slice.amount, 'BRL')}
      </span>
      <span className="w-12 text-right text-caption text-ink-muted">
        {formatPercent(share(slice.amount, totalNumber), { maximumFractionDigits: 1 })}
      </span>
    </button>
  )
}

function CategoryIcon({ id, name }: { readonly id: string; readonly name: string }) {
  const visual = categoryVisual({ id, name })
  return <visual.icon aria-hidden="true" className={`size-4 shrink-0 ${visual.textClass}`} />
}

function share(amount: string, total: number): number {
  if (total <= 0) return 0
  return (toFiniteNumber(amount) / total) * 100
}

function sliceColor(slice: CategorySlice): string {
  return slice.other
    ? otherCategoryVisual().colorVar
    : categoryVisual({ id: slice.key, name: slice.canonicalName }).colorVar
}
