import { formatMoney } from '@/lib/format'
import type { InvestmentIncome } from '@/features/integration/api'

/**
 * Gráfico mensal de proventos. A tabela da seção é a representação acessível
 * com os mesmos valores; o gráfico não cria pontos para meses sem lançamento.
 */
export function InvestmentIncomeChart({
  byMonth,
}: {
  readonly byMonth: InvestmentIncome['byMonth']
}) {
  if (byMonth.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-body-sm text-ink-muted">
        Sem proventos mensais para representar no período.
      </p>
    )
  }

  const max = Math.max(...byMonth.map((month) => Math.abs(Number(month.amount))), 0)

  return (
    <figure className="rounded-card border border-line bg-surface p-4">
      <figcaption className="text-caption text-ink-muted">
        Proventos por mês no período selecionado.
      </figcaption>
      <div
        role="img"
        aria-label={`Proventos mensais: ${byMonth
          .map((month) => `${month.month}, ${formatMoney(month.amount, 'BRL')}`)
          .join('; ')}.`}
        className="mt-4 flex h-40 items-end gap-2 overflow-x-auto pb-1"
      >
        {byMonth.map((month) => {
          const height = max > 0 ? Math.max(2, (Math.abs(Number(month.amount)) / max) * 100) : 2
          return (
            <div
              key={month.month}
              className="flex h-full min-w-10 flex-1 flex-col items-center justify-end gap-1"
              aria-hidden="true"
            >
              <span className="numeric text-[10px] text-ink-muted">
                {formatMoney(month.amount, 'BRL')}
              </span>
              <span
                className="w-full max-w-12 rounded-t-control bg-income"
                style={{ height: `${height}%` }}
              />
              <span className="whitespace-nowrap text-caption text-ink-muted">{month.month}</span>
            </div>
          )
        })}
      </div>
    </figure>
  )
}
