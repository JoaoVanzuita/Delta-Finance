import { useMemo, useState } from 'react'

import { useQuery } from '@tanstack/react-query'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { ApiError } from '@/lib/api/problem'
import { formatPercent, toFiniteNumber } from '@/lib/format'
import { queryKeys } from '@/lib/query-keys'
import { financeApi, type InvestmentHistoryPoint } from '@/features/integration/api'
import { WealthSnapshotDrawer } from '@/features/wealth/components/WealthSnapshotDrawer'
import { COMPLETENESS_LABELS } from '@/features/wealth/format'

import { assetClassLabel } from '../labels'
import { investmentVisual } from '../visuals'
import { formatMoney, formatReferenceDate } from '../format'

const RANGES = [
  { months: 12, label: '12 meses' },
  { months: 24, label: '24 meses' },
  { months: 60, label: '60 meses' },
] as const

function localDate(monthsAgo: number): string {
  const date = new Date()
  date.setMonth(date.getMonth() - monthsAgo)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** Evolução do valor investido e da composição por classe, derivada dos snapshots. */
export function InvestmentHistorySection() {
  const [range, setRange] = useState<(typeof RANGES)[number]['months']>(12)
  const [snapshotId, setSnapshotId] = useState<string | null>(null)
  const historyQuery = useQuery({
    queryKey: [...queryKeys.investmentHistory, range],
    queryFn: () => financeApi.investmentHistory(localDate(range - 1)),
  })
  const points = useMemo(
    () => (Array.isArray(historyQuery.data) ? historyQuery.data : []),
    [historyQuery.data],
  )
  const classes = useMemo(() => {
    const values = new Map<string, number>()
    for (const point of points) {
      for (const slice of point.allocation) {
        values.set(
          slice.assetClass,
          Math.max(values.get(slice.assetClass) ?? 0, toFiniteNumber(slice.value)),
        )
      }
    }
    return Array.from(values.entries())
      .sort((left, right) => right[1] - left[1])
      .map(([assetClass]) => assetClass)
  }, [points])

  return (
    <section aria-labelledby="investment-history-heading" className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="investment-history-heading" className="text-section-title font-semibold text-ink">
            Evolução dos investimentos
          </h2>
          <p className="text-caption text-ink-muted">
            Posição mensal por classe, derivada dos snapshots patrimoniais. Não é rentabilidade nem
            cotação em tempo real.
          </p>
        </div>
        <div className="flex flex-wrap gap-1" aria-label="Período do histórico de investimentos">
          {RANGES.map((option) => (
            <Button
              key={option.months}
              type="button"
              size="sm"
              variant={range === option.months ? 'primary' : 'secondary'}
              aria-pressed={range === option.months}
              onClick={() => setRange(option.months)}
            >
              {option.label}
            </Button>
          ))}
        </div>
      </div>

      {historyQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <span className="sr-only">Carregando histórico de investimentos…</span>
          <Skeleton className="h-64 rounded-card" />
        </div>
      ) : null}
      {historyQuery.isError ? (
        <Alert variant="danger" title="Histórico de investimentos indisponível">
          {historyQuery.error instanceof ApiError
            ? historyQuery.error.message
            : 'Não foi possível carregar o histórico.'}
        </Alert>
      ) : null}
      {historyQuery.isSuccess && points.length === 0 ? (
        <p className="rounded-card border border-dashed border-line-strong bg-surface p-4 text-body-sm text-ink-muted">
          Ainda não há snapshots com posições de investimento. O histórico começa no primeiro
          fechamento mensal após a ativação.
        </p>
      ) : null}

      {points.length > 0 ? (
        <>
          <InvestmentHistoryChart points={points} classes={classes} />
          <details className="rounded-card border border-line bg-surface">
            <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
              Ver tabela do histórico de investimentos
            </summary>
            <div className="overflow-x-auto">
              <Table caption="Histórico mensal de investimentos">
                <THead>
                  <TR>
                    <TH>Mês</TH>
                    <TH className="text-right">Total</TH>
                    <TH>Composição por classe</TH>
                    <TH>Completude</TH>
                    <TH />
                  </TR>
                </THead>
                <TBody>
                  {points.map((point) => (
                    <TR key={point.snapshotId}>
                      <TD>{formatReferenceDate(point.referenceDate)}</TD>
                      <TD className="numeric text-right font-semibold">
                        {formatMoney(point.totalValue)}
                      </TD>
                      <TD className="text-caption text-ink-muted">
                        {point.allocation
                          .map(
                            (slice) =>
                              `${assetClassLabel(slice.assetClass)} ${formatPercent(slice.percentage, { maximumFractionDigits: 1 })}`,
                          )
                          .join(' · ')}
                      </TD>
                      <TD>
                        <Badge
                          variant={
                            point.completeness === 'COMPLETE'
                              ? 'income'
                              : point.completeness === 'PARTIAL'
                                ? 'warning'
                                : 'info'
                          }
                        >
                          {COMPLETENESS_LABELS[point.completeness] ?? point.completeness}
                        </Badge>
                      </TD>
                      <TD className="text-right">
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => setSnapshotId(point.snapshotId)}
                        >
                          Detalhe
                        </Button>
                      </TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </div>
          </details>
        </>
      ) : null}

      <WealthSnapshotDrawer
        open={snapshotId != null}
        snapshotId={snapshotId}
        onClose={() => setSnapshotId(null)}
      />
    </section>
  )
}

function InvestmentHistoryChart({
  points,
  classes,
}: {
  readonly points: readonly InvestmentHistoryPoint[]
  readonly classes: readonly string[]
}) {
  const width = 640
  const height = 220
  const left = 58
  const right = 16
  const top = 16
  const bottom = 28
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const valueByClass = (point: InvestmentHistoryPoint, assetClass: string) =>
    toFiniteNumber(point.allocation.find((slice) => slice.assetClass === assetClass)?.value ?? '0')
  const max = Math.max(
    1,
    ...points.flatMap((point) => classes.map((assetClass) => valueByClass(point, assetClass))),
  )
  const y = (value: number) => top + (1 - value / max) * plotHeight
  const x = (index: number) =>
    points.length === 1 ? left + plotWidth / 2 : left + (index / (points.length - 1)) * plotWidth
  const labelStep = Math.max(1, Math.ceil(points.length / 6))

  return (
    <figure className="rounded-card border border-line bg-surface p-4">
      <div className="flex flex-wrap gap-x-4 gap-y-2 text-caption text-ink-muted">
        {classes.map((assetClass) => {
          const visual = investmentVisual(assetClass)
          return (
            <span key={assetClass} className="inline-flex items-center gap-1.5">
              <span className={`size-2.5 rounded-full ${visual.dotClass}`} aria-hidden="true" />
              {assetClassLabel(assetClass)}
            </span>
          )
        })}
      </div>
      <svg
        role="img"
        aria-label={`Evolução do valor investido por classe em ${points.length} mês(es).`}
        viewBox={`0 0 ${width} ${height}`}
        className="mt-3 h-auto w-full"
      >
        {[0, 0.5, 1].map((fraction) => {
          const lineY = top + fraction * plotHeight
          return (
            <line
              key={fraction}
              x1={left}
              x2={width - right}
              y1={lineY}
              y2={lineY}
              stroke="var(--df-line)"
              strokeDasharray={fraction === 1 ? undefined : '3 5'}
            />
          )
        })}
        {classes.map((assetClass) => {
          const visual = investmentVisual(assetClass)
          const path = points
            .map(
              (point, index) =>
                `${index === 0 ? 'M' : 'L'} ${x(index)} ${y(valueByClass(point, assetClass))}`,
            )
            .join(' ')
          return (
            <path
              key={assetClass}
              d={path}
              fill="none"
              stroke={visual.colorVar}
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              vectorEffect="non-scaling-stroke"
            />
          )
        })}
        {points.map((point, index) => (
          <g key={point.snapshotId}>
            {index % labelStep === 0 ? (
              <text
                x={x(index)}
                y={height - 8}
                textAnchor="middle"
                className="fill-[var(--df-ink-muted)] text-[10px]"
              >
                {point.referenceDate.slice(5, 7)}/{point.referenceDate.slice(2, 4)}
              </text>
            ) : null}
            {classes.map((assetClass) => (
              <circle
                key={assetClass}
                cx={x(index)}
                cy={y(valueByClass(point, assetClass))}
                r={2}
                fill={investmentVisual(assetClass).colorVar}
              >
                <title>
                  {formatReferenceDate(point.referenceDate)} · {assetClassLabel(assetClass)}:{' '}
                  {formatMoney(String(valueByClass(point, assetClass)))}
                </title>
              </circle>
            ))}
          </g>
        ))}
      </svg>
      <figcaption className="sr-only">
        Linhas por classe de investimento. A tabela abaixo repete os valores e percentuais.
      </figcaption>
    </figure>
  )
}
