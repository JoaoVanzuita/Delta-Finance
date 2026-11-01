import { formatDateTime, formatMoney } from '@/lib/format'
import type { InvestmentOverview } from '@/features/integration/api'

import { formatReferenceDate } from '../format'

interface InvestmentSummaryProps {
  readonly overview: InvestmentOverview
}

function incomeSourceLabel(source: InvestmentOverview['incomeSource']): string {
  if (source === 'PROVIDER') return 'proventos informados pelo provedor'
  if (source === 'MANUAL') return 'proventos manuais'
  if (source === 'MIXED') return 'provedor e manuais'
  return 'origem não informada'
}

/** Resumo do topo: valor atual em destaque e indicadores secundários sem equivalência. */
export function InvestmentSummary({ overview }: InvestmentSummaryProps) {
  const profitHint =
    overview.profitReported == null
      ? 'O provedor não informou resultado para as posições ativas.'
      : overview.profitCoverageCount < overview.activeCount
        ? `Informado para ${overview.profitCoverageCount} de ${overview.activeCount} posições ativas.`
        : 'Informado pelo provedor para todas as posições ativas.'

  return (
    <section aria-labelledby="investment-summary-heading" className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h2 id="investment-summary-heading" className="text-section-title font-semibold text-ink">
            Resumo
          </h2>
          <p className="text-caption text-ink-muted">
            Referência do provedor: {formatReferenceDate(overview.referenceDate)}
          </p>
        </div>
        <p className="text-caption text-ink-muted">
          {overview.activeCount} posição(ões) ativa(s) · {overview.closedCount} encerrada(s)
        </p>
      </div>

      {overview.stale ? (
        <p className="rounded-control border border-warning/40 bg-warning-soft p-3 text-body-sm text-ink">
          Há posição ativa com referência antiga. O valor é o último informado pelo provedor, não
          uma cotação em tempo real.
        </p>
      ) : null}

      <div className="rounded-panel border border-primary/25 bg-primary-soft p-5 sm:p-6">
        <p className="overline text-ink-muted">Valor atual</p>
        <p className="numeric mt-2 text-metric-xl font-bold tracking-tight text-ink">
          {overview.activeCount === 0
            ? 'Sem posições ativas'
            : formatMoney(overview.totalValue, 'BRL')}
        </p>
        <p className="mt-1 text-body-sm text-ink-muted">
          Último saldo importado das posições ativas. Não é cotação em tempo real nem histórico de
          rentabilidade.
        </p>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryTile
          label="Proventos recebidos (12 meses)"
          value={
            overview.incomeSource === 'UNAVAILABLE'
              ? 'Não informado'
              : formatMoney(overview.incomeLast12Months, 'BRL')
          }
          hint={`Inclui ${incomeSourceLabel(overview.incomeSource)}. Não são receita do Dashboard.`}
        />
        <SummaryTile
          label="Resultado informado pelo provedor"
          value={formatMoney(overview.profitReported, 'BRL')}
          hint={profitHint}
        />
        <SummaryTile
          label="Resultado estimado"
          value={formatMoney(overview.estimatedProfit, 'BRL')}
          hint={
            overview.estimatedProfit == null
              ? 'Informe o custo de uma posição para estimar o resultado.'
              : `Estimativa com custo e proventos, cobertura de ${overview.estimatedProfitCoverageCount} de ${overview.activeCount} posição(ões) ativa(s).`
          }
        />
        <SummaryTile
          label="Posições não disponíveis"
          value={String(overview.unavailableCount)}
          hint="Posições que o provedor não devolveu na última leitura. Não são tratadas como saldo zero."
        />
      </dl>

      <p className="text-caption text-ink-muted">
        Última sincronização:{' '}
        <span className="font-medium text-ink">{formatDateTime(overview.lastSyncedAt)}</span>. O
        resultado estimado não é rendimento realizado e não substitui o resultado do provedor.
      </p>
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
      <dt className="text-caption font-semibold tracking-[0.04em] text-ink-muted uppercase">
        {label}
      </dt>
      <dd className="numeric mt-2 text-metric-sm font-semibold text-ink">{value}</dd>
      <dd className="mt-2 text-caption leading-relaxed text-ink-muted">{hint}</dd>
    </div>
  )
}
