import { ChevronRight } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Drawer } from '@/components/ui/drawer'
import { Skeleton } from '@/components/ui/skeleton'
import { formatMoney, formatPercent } from '@/lib/format'
import type { InvestmentAllocation, InvestmentHolding } from '@/features/integration/api'

import { assetClassLabel, HOLDING_STATUS_LABELS, positionCountLabel } from '../labels'
import { investmentVisual } from '../visuals'

interface InvestmentClassDrawerProps {
  readonly open: boolean
  readonly entry: InvestmentAllocation | null
  readonly holdings: readonly InvestmentHolding[]
  readonly loading: boolean
  readonly error: boolean
  readonly onClose: () => void
  readonly onOpenHolding: (holding: InvestmentHolding) => void
}

/** Detalhe de uma classe de investimento: posições ativas que compõem a fatia. */
export function InvestmentClassDrawer({
  open,
  entry,
  holdings,
  loading,
  error,
  onClose,
  onOpenHolding,
}: InvestmentClassDrawerProps) {
  const visual = investmentVisual(entry?.assetClass)
  const label = assetClassLabel(entry?.assetClass)
  const sorted = [...holdings].sort((left, right) => Number(right.balance) - Number(left.balance))

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={label}
      description={
        entry
          ? `${positionCountLabel(entry.count)} · ${formatMoney(entry.value, 'BRL')} · ${formatPercent(entry.percentage, { maximumFractionDigits: 1 })} do portfólio importado`
          : undefined
      }
      width="md"
      bodyClassName="space-y-4"
    >
      <div className="flex items-center gap-3 rounded-card border border-line bg-surface-muted p-4">
        <span
          className={`flex size-10 shrink-0 items-center justify-center rounded-full bg-surface-muted ${visual.textClass}`}
          aria-hidden="true"
        >
          <visual.icon className="size-5" />
        </span>
        <div className="min-w-0">
          <p className="text-body-sm font-semibold text-ink">{label}</p>
          <p className="text-caption text-ink-muted">
            {entry
              ? `${positionCountLabel(entry.count)} ${entry.count === 1 ? 'ativa' : 'ativas'} · ${formatMoney(entry.value, 'BRL')}`
              : 'Classe sem posição ativa.'}
          </p>
        </div>
      </div>

      {loading ? (
        <div className="space-y-2" role="status" aria-live="polite">
          <span className="sr-only">Carregando posições da classe…</span>
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} className="h-16 rounded-card" />
          ))}
        </div>
      ) : null}

      {!loading && error ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense-soft p-4 text-body-sm text-ink"
        >
          Não foi possível carregar as posições desta classe. O valor da alocação continua
          disponível.
        </p>
      ) : null}

      {!loading && !error && sorted.length === 0 ? (
        <p className="rounded-card border border-dashed border-line-strong p-4 text-body-sm text-ink-muted">
          O provedor informou esta classe na alocação, mas não devolveu os detalhes das posições
          nesta leitura.
        </p>
      ) : null}

      {!loading && !error && sorted.length > 0 ? (
        <ul
          className="divide-y divide-line rounded-card border border-line"
          aria-label={`Posições em ${label}`}
        >
          {sorted.map((holding) => {
            const unavailable = holding.unavailableAt != null
            const status = unavailable
              ? 'Indisponível'
              : (HOLDING_STATUS_LABELS[holding.status] ?? holding.status)
            return (
              <li key={holding.id}>
                <button
                  type="button"
                  onClick={() => onOpenHolding(holding)}
                  className="flex min-h-16 w-full items-center gap-3 px-3 py-2.5 text-left transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus"
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-body-sm font-medium text-ink">
                      {holding.name ?? 'Posição sem nome'}
                    </p>
                    <p className="text-caption text-ink-muted">
                      {holding.code ?? 'Sem código'} · {status}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="numeric text-body-sm font-semibold text-ink">
                      {formatMoney(holding.balance, holding.currency || 'BRL')}
                    </p>
                    {holding.estimatedProfit != null ? (
                      <p className="numeric text-caption text-ink-muted">
                        {formatMoney(holding.estimatedProfit, holding.currency || 'BRL')} estimado
                      </p>
                    ) : null}
                  </div>
                  <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-ink-muted" />
                </button>
              </li>
            )
          })}
        </ul>
      ) : null}

      <p className="text-caption text-ink-muted">
        Valores do último saldo importado. Clique em uma posição para abrir o detalhe completo.
      </p>

      <Badge variant="neutral">Somente posições importadas ativas</Badge>
    </Drawer>
  )
}
