import { useState } from 'react'

import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { formatMoney, formatPercent, toFiniteNumber } from '@/lib/format'
import { buildDonutGradient } from '@/lib/donut'
import type {
  InvestmentAllocation as AllocationEntry,
  InvestmentHolding,
} from '@/features/integration/api'

import { assetClassCountLabel, assetClassLabel } from '../labels'
import { investmentVisual } from '../visuals'
import { InvestmentClassDrawer } from './InvestmentClassDrawer'

interface InvestmentAllocationProps {
  readonly allocation: readonly AllocationEntry[]
  readonly providerValue: string
  readonly totalValue: string
  readonly holdings: readonly InvestmentHolding[]
  readonly holdingsLoading: boolean
  readonly holdingsError: boolean
  readonly onOpenHolding: (holding: InvestmentHolding) => void
}

/**
 * Alocação por classe das posições importadas ativas.
 *
 * Donut com o total no centro e legenda interativa: cada classe abre um drawer
 * com as posições que compõem a fatia. A tabela recolhível é a alternativa
 * acessível e repete os mesmos valores do backend.
 */
export function InvestmentAllocation({
  allocation,
  providerValue,
  totalValue,
  holdings,
  holdingsLoading,
  holdingsError,
  onOpenHolding,
}: InvestmentAllocationProps) {
  const [selectedAssetClass, setSelectedAssetClass] = useState<string | null>(null)

  if (Number(providerValue) === 0 || allocation.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-body-sm text-ink-muted">
        Nenhuma posição ativa para exibir alocação. Posições encerradas continuam no histórico.
      </p>
    )
  }

  const totalNumber = Math.max(
    0,
    toFiniteNumber(totalValue) ||
      allocation.reduce((sum, entry) => sum + toFiniteNumber(entry.value), 0),
  )
  const donutSegments = buildDonutGradient(
    allocation.map((entry) => ({
      value: toFiniteNumber(entry.value),
      color: investmentVisual(entry.assetClass).colorVar,
    })),
    totalNumber,
  )
  const selectedEntry = selectedAssetClass
    ? (allocation.find((entry) => entry.assetClass === selectedAssetClass) ?? null)
    : null
  const selectedHoldings = selectedAssetClass
    ? holdings.filter((holding) => holding.assetClass === selectedAssetClass)
    : []

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-caption text-ink-muted">Valor atual das posições importadas</p>
          <p className="numeric text-metric-md font-semibold text-ink">
            {formatMoney(totalValue, 'BRL')}
          </p>
        </div>
        <p className="text-caption text-ink-muted">
          {assetClassCountLabel(allocation.length)} · clique para ver as posições
        </p>
      </div>

      <div className="grid gap-5 md:grid-cols-[minmax(0,15rem)_minmax(0,1fr)] md:items-center">
        <figure className="mx-auto w-full max-w-[15rem]">
          <div
            role="img"
            aria-label={`Alocação por classe de investimento. Total ${formatMoney(totalValue, 'BRL')}.`}
            className="relative aspect-square w-full rounded-full"
            style={{ background: donutSegments }}
          >
            <div className="absolute inset-[22%] flex flex-col items-center justify-center rounded-full bg-surface text-center">
              <span className="text-caption text-ink-muted">Portfólio</span>
              <span className="numeric text-body-sm font-semibold text-ink">
                {formatMoney(totalValue, 'BRL')}
              </span>
            </div>
          </div>
          <figcaption className="sr-only">
            Distribuição do valor atual por classe de investimento. A lista e a tabela abaixo
            repetem os mesmos valores.
          </figcaption>
        </figure>

        <ul className="space-y-1" aria-label="Alocação por classe de investimento">
          {allocation.map((entry) => {
            const visual = investmentVisual(entry.assetClass)
            const label = assetClassLabel(entry.assetClass)
            const percentage = Number(entry.percentage)
            return (
              <li key={entry.assetClass}>
                <button
                  type="button"
                  aria-haspopup="dialog"
                  aria-label={`${label}: ${formatMoney(entry.value, 'BRL')}, ${formatPercent(percentage, { maximumFractionDigits: 1 })}, ${entry.count} ${entry.count === 1 ? 'ativo' : 'ativos'}. Abrir detalhes da classe.`}
                  onClick={() => setSelectedAssetClass(entry.assetClass)}
                  className="flex min-h-11 w-full items-center gap-2 rounded-control px-2 text-left transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                >
                  <span
                    className={`size-2.5 shrink-0 rounded-full ${visual.dotClass}`}
                    aria-hidden="true"
                  />
                  <visual.icon
                    aria-hidden="true"
                    className={`size-4 shrink-0 ${visual.textClass}`}
                  />
                  <span className="min-w-0 flex-1 truncate text-body-sm font-medium text-ink">
                    {label}
                  </span>
                  <span className="numeric text-body-sm font-semibold text-ink">
                    {formatMoney(entry.value, 'BRL')}
                  </span>
                  <span className="w-12 text-right text-caption text-ink-muted">
                    {formatPercent(percentage, { maximumFractionDigits: 1 })}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      </div>

      <details className="rounded-card border border-line">
        <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
          Ver tabela de alocação
        </summary>
        <Table caption="Alocação de investimentos por classe">
          <THead>
            <TR>
              <TH>Classe</TH>
              <TH className="text-right">Valor</TH>
              <TH className="text-right">Posições</TH>
              <TH className="text-right">% do importado</TH>
            </TR>
          </THead>
          <TBody>
            {allocation.map((entry) => (
              <TR key={entry.assetClass}>
                <TD>{assetClassLabel(entry.assetClass)}</TD>
                <TD className="numeric text-right">{formatMoney(entry.value, 'BRL')}</TD>
                <TD className="numeric text-right text-ink-muted">{entry.count}</TD>
                <TD className="numeric text-right text-ink-muted">
                  {formatPercent(entry.percentage, { maximumFractionDigits: 1 })}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </details>

      <InvestmentClassDrawer
        open={selectedEntry != null}
        entry={selectedEntry}
        holdings={selectedHoldings}
        loading={holdingsLoading}
        error={holdingsError}
        onClose={() => setSelectedAssetClass(null)}
        onOpenHolding={(holding) => {
          setSelectedAssetClass(null)
          onOpenHolding(holding)
        }}
      />
    </div>
  )
}
