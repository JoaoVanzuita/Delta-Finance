import { formatMoney } from '@/features/accounts/format'
import type { InvestmentIncome } from '@/features/integration/api'

import { formatReferenceDate } from '../format'

interface InvestmentIncomeSectionProps {
  readonly income: InvestmentIncome
}

/** Proventos do provedor, com origem explícita e alternativa textual. */
export function InvestmentIncomeSection({ income }: InvestmentIncomeSectionProps) {
  const max = Math.max(...income.byMonth.map((month) => Math.abs(Number(month.amount))), 0)

  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-muted">
        Período: {formatReferenceDate(income.from)} a {formatReferenceDate(income.to)} · Total:{' '}
        <span className="numeric font-semibold text-ink">{formatMoney(income.total, 'BRL')}</span> ·
        Origem:{' '}
        <span className="font-medium text-ink">
          {income.source === 'PROVIDER'
            ? 'provedor'
            : income.source === 'MANUAL'
              ? 'manual'
              : income.source === 'MIXED'
                ? 'provedor e manual'
                : 'não informada'}
        </span>
      </p>

      {income.items.length === 0 ? (
        <div className="rounded-card border border-line bg-surface-muted p-4 text-sm leading-relaxed text-ink-muted">
          Nenhum provento informado pelo provedor no período. O sistema não estima dividendos, JCP,
          aluguel de FII ou cupons sem uma transação `INTEREST`.
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[32rem] text-left text-sm">
              <caption className="sr-only">Proventos de investimento por mês</caption>
              <thead className="border-b border-line bg-surface-muted text-xs uppercase">
                <tr>
                  <th scope="col" className="px-3 py-2">
                    Mês
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Proventos
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Lançamentos
                  </th>
                </tr>
              </thead>
              <tbody>
                {income.byMonth.map((month) => (
                  <tr key={month.month} className="border-b border-line last:border-0">
                    <th scope="row" className="px-3 py-2 font-medium text-ink">
                      {month.month}
                    </th>
                    <td className="px-3 py-2 text-right">
                      <span className="numeric block text-income">
                        {formatMoney(month.amount, 'BRL')}
                      </span>
                      <span
                        aria-hidden="true"
                        className="mt-1 block h-1.5 overflow-hidden rounded-card bg-surface-muted"
                      >
                        <span
                          className="block h-full rounded-card bg-income"
                          style={{
                            width: `${max > 0 ? Math.min(100, (Math.abs(Number(month.amount)) / max) * 100) : 0}%`,
                          }}
                        />
                      </span>
                    </td>
                    <td className="numeric px-3 py-2 text-right text-ink-muted">{month.count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <details className="rounded-card border border-line">
            <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-ink">
              Ver proventos por ativo
            </summary>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[36rem] text-left text-sm">
                <caption className="sr-only">Proventos de investimento por ativo</caption>
                <thead className="border-y border-line bg-surface-muted text-xs uppercase">
                  <tr>
                    <th scope="col" className="px-3 py-2">
                      Data
                    </th>
                    <th scope="col" className="px-3 py-2">
                      Ativo
                    </th>
                    <th scope="col" className="px-3 py-2">
                      Origem
                    </th>
                    <th scope="col" className="px-3 py-2 text-right">
                      Valor
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {income.items.map((item, index) => (
                    <tr
                      key={`${item.investmentId}-${item.date}-${index}`}
                      className="border-b border-line last:border-0"
                    >
                      <td className="px-3 py-2 text-ink">{formatReferenceDate(item.date)}</td>
                      <td className="px-3 py-2 text-ink">
                        {item.investmentName ?? 'Posição sem nome'}
                        {item.code ? (
                          <span className="ml-2 text-xs text-ink-muted">{item.code}</span>
                        ) : null}
                      </td>
                      <td className="px-3 py-2 text-xs text-ink-muted">
                        {item.source === 'PROVIDER' ? 'Provedor' : 'Manual'}
                      </td>
                      <td className="numeric px-3 py-2 text-right text-ink">
                        {formatMoney(item.amount, 'BRL')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </>
      )}
    </div>
  )
}
