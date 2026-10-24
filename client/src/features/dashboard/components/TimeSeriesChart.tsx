import { formatMoney } from '@/features/accounts/format'
import type { DashboardBucket } from '@/features/dashboard/api'

interface TimeSeriesChartProps {
  readonly buckets: readonly DashboardBucket[]
  readonly selectedStart?: string
  readonly onDrillDown: (bucket: DashboardBucket) => void
}

/**
 * Evolução temporal com alternativa textual e drill-down.
 *
 * Cada bucket expõe receitas, despesas e resultado em uma linha focável, com
 * barras proporcionais ao maior valor do período. A tabela é a representação
 * acessível e também funciona como fallback visual em telas estreitas.
 */
export function TimeSeriesChart({ buckets, selectedStart, onDrillDown }: TimeSeriesChartProps) {
  if (buckets.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-sm text-ink-muted">
        Sem evolução temporal para o período selecionado.
      </p>
    )
  }

  const max = Math.max(
    ...buckets.flatMap((bucket) => [
      Math.abs(Number(bucket.income)),
      Math.abs(Number(bucket.expenses)),
    ]),
    0,
  )

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[34rem] text-left text-sm">
        <caption className="sr-only">
          Evolução temporal de receitas, despesas e resultado por período
        </caption>
        <thead className="border-b border-line bg-surface-muted text-xs uppercase">
          <tr>
            <th scope="col" className="px-3 py-2">
              Período
            </th>
            <th scope="col" className="px-3 py-2 text-right">
              Receitas
            </th>
            <th scope="col" className="px-3 py-2 text-right">
              Despesas
            </th>
            <th scope="col" className="px-3 py-2 text-right">
              Resultado
            </th>
            <th scope="col" className="px-3 py-2 text-right">
              Detalhar
            </th>
          </tr>
        </thead>
        <tbody>
          {buckets.map((bucket) => {
            const selected = selectedStart === bucket.start
            return (
              <tr
                key={bucket.start}
                className={selected ? 'bg-action/5' : 'border-b border-line last:border-0'}
              >
                <th scope="row" className="px-3 py-2 font-medium text-ink">
                  {bucket.label}
                </th>
                <td className="px-3 py-2 text-right">
                  <span className="numeric block text-income">
                    {formatMoney(bucket.income, 'BRL')}
                  </span>
                  <span
                    aria-hidden="true"
                    className="mt-1 block h-1.5 overflow-hidden rounded-card bg-surface-muted"
                  >
                    <span
                      className="block h-full rounded-card bg-income"
                      style={{ width: `${barWidth(bucket.income, max)}%` }}
                    />
                  </span>
                </td>
                <td className="px-3 py-2 text-right">
                  <span className="numeric block text-expense">
                    {formatMoney(bucket.expenses, 'BRL')}
                  </span>
                  <span
                    aria-hidden="true"
                    className="mt-1 block h-1.5 overflow-hidden rounded-card bg-surface-muted"
                  >
                    <span
                      className="block h-full rounded-card bg-expense"
                      style={{ width: `${barWidth(bucket.expenses, max)}%` }}
                    />
                  </span>
                </td>
                <td
                  className={[
                    'numeric px-3 py-2 text-right font-semibold',
                    Number(bucket.result) < 0 ? 'text-expense' : 'text-ink',
                  ].join(' ')}
                >
                  {formatMoney(bucket.result, 'BRL')}
                </td>
                <td className="px-3 py-2 text-right">
                  <button
                    type="button"
                    onClick={() => onDrillDown(bucket)}
                    aria-pressed={selected}
                    aria-label={`Detalhar ${bucket.label}`}
                    className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-xs font-medium text-ink transition-colors hover:bg-surface-muted"
                  >
                    Detalhar
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function barWidth(value: string, max: number): number {
  if (max <= 0) return 0
  const number = Math.abs(Number(value))
  if (!Number.isFinite(number)) return 0
  return Math.max(0, Math.min(100, Math.round((number / max) * 100)))
}
