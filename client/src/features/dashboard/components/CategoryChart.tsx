import { formatMoney } from '@/features/accounts/format'
import type { DashboardCategory } from '@/features/dashboard/api'
import { formatPercentage } from '@/features/dashboard/format'

interface CategoryChartProps {
  readonly categories: readonly DashboardCategory[]
  readonly selectedCategoryId?: string
  readonly onDrillDown: (category: DashboardCategory) => void
}

/**
 * Distribuição de despesas por categoria.
 *
 * O gráfico é uma lista de barras com rótulos e valores; a mesma informação está
 * disponível em tabela textual logo abaixo, atendendo leitores de tela e
 * navegação por teclado. Cada categoria abre o drill-down correspondente.
 */
export function CategoryChart({ categories, selectedCategoryId, onDrillDown }: CategoryChartProps) {
  if (categories.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-sm text-ink-muted">
        Sem despesas por categoria no período.
      </p>
    )
  }

  const total = categories.reduce((sum, category) => sum + Math.abs(Number(category.amount)), 0)
  const max = Math.max(...categories.map((category) => Math.abs(Number(category.amount))), 0)

  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-muted">
        Total de despesas no período:{' '}
        <span className="numeric font-semibold text-ink">{formatMoney(String(total), 'BRL')}</span>
      </p>

      <ul className="space-y-3" aria-label="Despesas por categoria">
        {categories.map((category) => {
          const width = max > 0 ? formatPercentage(category.amount, max) : 0
          const selected = selectedCategoryId === category.categoryId
          return (
            <li key={category.categoryId}>
              <button
                type="button"
                aria-pressed={selected}
                onClick={() => onDrillDown(category)}
                className={[
                  'w-full rounded-card border px-3 py-2 text-left transition-colors',
                  selected
                    ? 'border-action bg-action/5'
                    : 'border-transparent hover:border-line hover:bg-surface-muted',
                ].join(' ')}
              >
                <span className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="text-sm font-medium text-ink">{category.categoryName}</span>
                  <span className="numeric text-sm font-semibold text-ink">
                    {formatMoney(category.amount, 'BRL')}
                  </span>
                </span>
                <span
                  aria-hidden="true"
                  className="mt-2 block h-2 overflow-hidden rounded-card bg-surface-muted"
                >
                  <span
                    className="block h-full rounded-card bg-expense"
                    style={{ width: `${width}%` }}
                  />
                </span>
                <span className="mt-1 block text-xs text-ink-muted">
                  {category.transactionCount} lançamento(s) ·{' '}
                  {formatPercentage(category.amount, total)}% das despesas
                </span>
              </button>
            </li>
          )
        })}
      </ul>

      <details className="rounded-card border border-line">
        <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-ink">
          Ver tabela de despesas por categoria
        </summary>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Despesas por categoria no período</caption>
            <thead className="border-y border-line bg-surface-muted text-xs uppercase">
              <tr>
                <th scope="col" className="px-3 py-2">
                  Categoria
                </th>
                <th scope="col" className="px-3 py-2 text-right">
                  Valor
                </th>
                <th scope="col" className="px-3 py-2 text-right">
                  Lançamentos
                </th>
              </tr>
            </thead>
            <tbody>
              {categories.map((category) => (
                <tr key={category.categoryId} className="border-b border-line last:border-0">
                  <td className="px-3 py-2 text-ink">{category.categoryName}</td>
                  <td className="numeric px-3 py-2 text-right text-ink">
                    {formatMoney(category.amount, 'BRL')}
                  </td>
                  <td className="numeric px-3 py-2 text-right text-ink-muted">
                    {category.transactionCount}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  )
}
