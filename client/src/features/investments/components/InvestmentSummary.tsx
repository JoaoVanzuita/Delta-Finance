import { formatDateTime, formatMoney } from '@/features/accounts/format'
import type { InvestmentOverview } from '@/features/integration/api'

import { formatReferenceDate } from '../format'
import { formatPercentage } from '@/features/dashboard/format'

interface InvestmentSummaryProps {
  readonly overview: InvestmentOverview
}

/** Resumo do topo do dashboard de investimentos. */
export function InvestmentSummary({ overview }: InvestmentSummaryProps) {
  const profitHint =
    overview.profitReported == null
      ? 'O provedor não informou lucro/prejuízo para as posições ativas.'
      : overview.profitCoverageCount < overview.activeCount
        ? `Informado para ${overview.profitCoverageCount} de ${overview.activeCount} posições ativas.`
        : 'Resultado informado pelo provedor para todas as posições ativas.'

  return (
    <section aria-labelledby="investment-summary-heading" className="space-y-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="investment-summary-heading" className="text-lg font-semibold text-ink">
          Resumo
        </h2>
        <p className="text-xs text-ink-muted">
          Referência: {formatReferenceDate(overview.referenceDate)}
        </p>
      </div>
      {overview.stale ? (
        <p className="rounded-card border border-warning/40 bg-warning/5 p-3 text-sm text-ink">
          Há posição ativa com data de referência antiga. O valor é o último informado pelo
          provedor, não uma cotação em tempo real.
        </p>
      ) : null}
      <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <SummaryTile
          label="Valor atual"
          value={formatMoney(overview.totalValue, 'BRL')}
          hint={`${overview.activeCount} posição(ões) importada(s) ativa(s) em BRL.`}
        />
        <SummaryTile
          label="Proventos (12 meses)"
          value={formatMoney(overview.incomeLast12Months, 'BRL')}
          hint={
            overview.incomeSource === 'PROVIDER'
              ? 'Proventos INTEREST informados pelo provedor.'
              : 'O provedor não informou proventos no período.'
          }
        />
        <SummaryTile
          label="Resultado do provedor"
          value={formatMoney(overview.profitReported, 'BRL')}
          hint={profitHint}
        />
        <SummaryTile
          label="Resultado estimado"
          value={formatMoney(overview.estimatedProfit, 'BRL')}
          hint={
            overview.estimatedProfit == null
              ? 'Informe o custo de uma posição para estimar o resultado.'
              : `Calculado com custo e proventos para ${overview.estimatedProfitCoverageCount} de ${overview.activeCount} posição(ões) ativa(s).`
          }
        />
        <SummaryTile
          label="Posições encerradas"
          value={String(overview.closedCount)}
          hint={`${overview.unavailableCount} indisponível(is) na última leitura.`}
        />
      </dl>
      <p className="text-xs leading-relaxed text-ink-muted">
        Última sincronização:{' '}
        <span className="font-medium text-ink">{formatDateTime(overview.lastSyncedAt)}</span>.
        Percentuais de alocação usam apenas posições importadas ativas.
      </p>
      {overview.profitReported != null && overview.activeCount > 0 ? (
        <p className="text-xs text-ink-muted">
          Cobertura do resultado:{' '}
          <span className="numeric font-medium text-ink">
            {formatPercentage(String(overview.profitCoverageCount), overview.activeCount)}%
          </span>{' '}
          das posições ativas.
        </p>
      ) : null}
    </section>
  )
}

function SummaryTile({
  label,
  value,
  hint,
}: {
  readonly label: string
  readonly value: string
  readonly hint: string
}) {
  return (
    <div className="rounded-card border border-line bg-surface p-4">
      <dt className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">{label}</dt>
      <dd className="mt-2">
        <span className="numeric block text-xl font-semibold text-ink">{value}</span>
        <span className="mt-2 block text-xs leading-relaxed text-ink-muted">{hint}</span>
      </dd>
    </div>
  )
}
