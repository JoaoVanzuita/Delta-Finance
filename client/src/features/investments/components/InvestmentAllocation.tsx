import { formatMoney } from '@/features/accounts/format'
import type { InvestmentAllocation } from '@/features/integration/api'

import { assetClassLabel } from '../labels'

interface InvestmentAllocationProps {
  readonly allocation: readonly InvestmentAllocation[]
  readonly providerValue: string
  readonly manualValue: string
  readonly totalValue: string
}

/** Alocação das posições importadas ativas, com posições manuais separadas. */
export function InvestmentAllocation({
  allocation,
  providerValue,
  manualValue,
  totalValue,
}: InvestmentAllocationProps) {
  const providerTotal = Number(providerValue) || 0
  const manualTotal = Number(manualValue) || 0
  const total = Number(totalValue) || 0

  if (providerTotal === 0 && manualTotal === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-sm text-ink-muted">
        Nenhuma posição ativa para exibir alocação. Posições encerradas continuam no histórico.
      </p>
    )
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-muted">
        Valor atual:{' '}
        <span className="numeric font-semibold text-ink">{formatMoney(totalValue, 'BRL')}</span>. A
        alocação abaixo separa o que veio do provedor do que foi informado manualmente.
      </p>

      <ul className="space-y-3" aria-label="Alocação por classe de investimento">
        {allocation.map((entry) => {
          const value = Number(entry.value) || 0
          const width = providerTotal > 0 ? Math.min(100, (value / providerTotal) * 100) : 0
          return (
            <li key={entry.assetClass}>
              <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                <span className="font-medium text-ink">{assetClassLabel(entry.assetClass)}</span>
                <span className="numeric text-ink">
                  {formatMoney(entry.value, 'BRL')} · {entry.percentage}%
                </span>
              </div>
              <span
                aria-hidden="true"
                className="mt-2 block h-2 overflow-hidden rounded-card bg-surface-muted"
              >
                <span
                  className="block h-full rounded-card bg-primary"
                  style={{ width: `${width}%` }}
                />
              </span>
              <span className="mt-1 block text-xs text-ink-muted">
                {entry.count} posição(ões) importada(s)
              </span>
            </li>
          )
        })}
        {manualTotal > 0 ? (
          <li>
            <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
              <span className="font-medium text-ink">Posições manuais</span>
              <span className="numeric text-ink">
                {formatMoney(manualValue, 'BRL')} ·{' '}
                {total > 0 ? ((manualTotal / total) * 100).toFixed(2) : '0.00'}%
              </span>
            </div>
            <span
              aria-hidden="true"
              className="mt-2 block h-2 overflow-hidden rounded-card bg-surface-muted"
            >
              <span
                className="block h-full rounded-card bg-action"
                style={{ width: `${total > 0 ? Math.min(100, (manualTotal / total) * 100) : 0}%` }}
              />
            </span>
            <span className="mt-1 block text-xs text-ink-muted">
              Valor de referência informado por você; não é cotação de mercado.
            </span>
          </li>
        ) : null}
      </ul>

      <details className="rounded-card border border-line">
        <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-ink">
          Ver tabela de alocação
        </summary>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Alocação de investimentos por classe</caption>
            <thead className="border-y border-line bg-surface-muted text-xs uppercase">
              <tr>
                <th scope="col" className="px-3 py-2">
                  Classe
                </th>
                <th scope="col" className="px-3 py-2 text-right">
                  Valor
                </th>
                <th scope="col" className="px-3 py-2 text-right">
                  Posições
                </th>
                <th scope="col" className="px-3 py-2 text-right">
                  % do importado
                </th>
              </tr>
            </thead>
            <tbody>
              {allocation.map((entry) => (
                <tr key={entry.assetClass} className="border-b border-line last:border-0">
                  <td className="px-3 py-2 text-ink">{assetClassLabel(entry.assetClass)}</td>
                  <td className="numeric px-3 py-2 text-right text-ink">
                    {formatMoney(entry.value, 'BRL')}
                  </td>
                  <td className="numeric px-3 py-2 text-right text-ink-muted">{entry.count}</td>
                  <td className="numeric px-3 py-2 text-right text-ink-muted">
                    {entry.percentage}%
                  </td>
                </tr>
              ))}
              {manualTotal > 0 ? (
                <tr>
                  <td className="px-3 py-2 text-ink">Posições manuais</td>
                  <td className="numeric px-3 py-2 text-right text-ink">
                    {formatMoney(manualValue, 'BRL')}
                  </td>
                  <td className="numeric px-3 py-2 text-right text-ink-muted">—</td>
                  <td className="numeric px-3 py-2 text-right text-ink-muted">—</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  )
}
