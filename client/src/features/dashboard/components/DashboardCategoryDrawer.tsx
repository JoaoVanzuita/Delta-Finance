import { Button } from '@/components/ui/button'
import { Drawer } from '@/components/ui/drawer'
import { CategoryIcon } from '@/features/categories/CategoryIcon'
import type { DashboardCategory } from '@/features/dashboard/api'
import { categoryDisplayName } from '@/features/dashboard/format'
import type { CategoryDirection } from './CategoryChart'
import { addDecimals, formatMoney, formatPercent, toFiniteNumber } from '@/lib/format'

interface DashboardCategoryDrawerProps {
  readonly open: boolean
  readonly onClose: () => void
  readonly categories: readonly DashboardCategory[]
  readonly direction?: CategoryDirection
  readonly onSelect: (category: DashboardCategory) => void
}

/** Lista completa de categorias, preservando o drill-down e os filtros atuais. */
export function DashboardCategoryDrawer({
  open,
  onClose,
  categories,
  direction = 'EXPENSE',
  onSelect,
}: DashboardCategoryDrawerProps) {
  const total = addDecimals(categories.map((category) => category.amount))
  const totalNumber = toFiniteNumber(total)
  const income = direction === 'INCOME'

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={income ? 'Receitas por categoria' : 'Despesas por categoria'}
      description={`${categories.length} categoria(s) no período · ${formatMoney(total, 'BRL')} no total`}
      width="md"
      footer={
        <Button type="button" variant="secondary" onClick={onClose}>
          Fechar
        </Button>
      }
    >
      <ul className="divide-y divide-line rounded-card border border-line">
        {categories.map((category) => (
          <li key={category.categoryId}>
            <button
              type="button"
              onClick={() => onSelect(category)}
              className="flex min-h-16 w-full items-center gap-3 px-3 py-2.5 text-left transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus"
            >
              <CategoryIcon id={category.categoryId} name={category.categoryName} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-body-sm font-medium text-ink">
                  {categoryDisplayName(category.categoryName, direction)}
                </p>
                <p className="text-caption text-ink-muted">
                  {category.transactionCount} lançamento(s) ·{' '}
                  {formatPercent(
                    totalNumber > 0 ? (toFiniteNumber(category.amount) / totalNumber) * 100 : 0,
                    { maximumFractionDigits: 1 },
                  )}{' '}
                  das {income ? 'receitas' : 'despesas'}
                </p>
              </div>
              <span className="numeric text-body-sm font-semibold text-ink">
                {formatMoney(category.amount, 'BRL')}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </Drawer>
  )
}
