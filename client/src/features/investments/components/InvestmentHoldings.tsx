import { ChevronRight } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { formatMoney } from '@/lib/format'
import type { InvestmentHolding } from '@/features/integration/api'

import { formatReferenceDate } from '../format'
import { assetClassLabel, HOLDING_STATUS_LABELS } from '../labels'

interface InvestmentHoldingsProps {
  readonly items: readonly InvestmentHolding[]
  readonly onOpen: (holding: InvestmentHolding) => void
}

/** Cartões compactos de posições; detalhes e edição ficam em drawer. */
export function InvestmentHoldings({ items, onOpen }: InvestmentHoldingsProps) {
  if (items.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-body-sm text-ink-muted">
        Nenhuma posição neste filtro. Posições encerradas e indisponíveis podem ser consultadas em
        “Todas”.
      </p>
    )
  }

  return (
    <ul className="grid gap-3 md:grid-cols-2" aria-label="Posições importadas de investimento">
      {items.map((holding) => {
        const unavailable = holding.unavailableAt != null
        const statusLabel = unavailable
          ? 'Indisponível'
          : (HOLDING_STATUS_LABELS[holding.status] ?? holding.status)
        return (
          <li key={holding.id}>
            <article className="flex h-full flex-col gap-3 rounded-card border border-line bg-surface p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <h3 className="truncate text-body font-semibold text-ink">
                    {holding.name ?? 'Posição sem nome'}
                  </h3>
                  <p className="mt-0.5 text-caption text-ink-muted">
                    {holding.code ?? 'Sem código'} · {assetClassLabel(holding.assetClass)}
                  </p>
                </div>
                <Badge variant={unavailable || holding.status === 'CLOSED' ? 'neutral' : 'income'}>
                  {statusLabel}
                </Badge>
              </div>

              <div>
                <p className="text-caption text-ink-muted">Valor atual</p>
                <p className="numeric mt-1 text-metric-md font-semibold text-ink">
                  {formatMoney(holding.balance, holding.currency || 'BRL')}
                </p>
              </div>

              <dl className="grid grid-cols-2 gap-2 text-caption">
                <div>
                  <dt className="text-ink-muted">Resultado estimado</dt>
                  <dd className="numeric mt-0.5 font-semibold text-ink">
                    {holding.estimatedProfit == null
                      ? 'Sem custo'
                      : formatMoney(holding.estimatedProfit, holding.currency || 'BRL')}
                  </dd>
                </div>
                <div>
                  <dt className="text-ink-muted">Referência</dt>
                  <dd className="mt-0.5 text-ink">{formatReferenceDate(holding.referenceDate)}</dd>
                </div>
              </dl>

              <button
                type="button"
                onClick={() => onOpen(holding)}
                className="mt-auto inline-flex min-h-11 items-center justify-between gap-2 rounded-control border border-line bg-surface px-3 text-body-sm font-semibold text-ink hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              >
                Ver detalhes
                <ChevronRight aria-hidden="true" className="size-4" />
              </button>
            </article>
          </li>
        )
      })}
    </ul>
  )
}
