import { useId, useMemo, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import type { DashboardBucket } from '@/features/dashboard/api'
import { addDecimals, formatMoney, toFiniteNumber } from '@/lib/format'
import { useMediaQuery } from '@/lib/useMediaQuery'

interface TimeSeriesChartProps {
  readonly buckets: readonly DashboardBucket[]
  readonly selectedStart?: string
  readonly onDrillDown: (bucket: DashboardBucket) => void
}

type SeriesKey = 'income' | 'expenses' | 'result'

interface SeriesDefinition {
  readonly key: SeriesKey
  readonly label: string
  readonly stroke: string
  readonly dash?: string
  readonly fill?: string
}

const SERIES: readonly SeriesDefinition[] = [
  {
    key: 'income',
    label: 'Receitas',
    stroke: 'var(--df-income)',
    fill: 'color-mix(in srgb, var(--df-income) 16%, transparent)',
  },
  {
    key: 'expenses',
    label: 'Despesas',
    stroke: 'var(--df-expense)',
    dash: '7 4',
    fill: 'color-mix(in srgb, var(--df-expense) 12%, transparent)',
  },
  {
    key: 'result',
    label: 'Resultado',
    stroke: 'var(--df-ink)',
    dash: '2 4',
  },
]

const DESKTOP_GEOMETRY = {
  width: 820,
  height: 300,
  margin: { top: 18, right: 18, bottom: 38, left: 64 },
  ticks: 4,
  labelSize: 11,
  maxLabels: 6,
} as const

const COMPACT_GEOMETRY = {
  width: 380,
  height: 250,
  margin: { top: 14, right: 8, bottom: 32, left: 54 },
  ticks: 3,
  labelSize: 12,
  maxLabels: 3,
} as const
const MAX_VISUAL_POINTS = 120

interface ChartBucket extends DashboardBucket {
  readonly aggregated: boolean
}

/**
 * Evolução temporal em SVG, com seleção de séries, tooltip por teclado e tabela
 * equivalente. Acima de 120 pontos, a visualização agrega por mês; a tabela
 * mantém todos os buckets originais e os totais.
 */
export function TimeSeriesChart({ buckets, selectedStart, onDrillDown }: TimeSeriesChartProps) {
  const [visible, setVisible] = useState<Record<SeriesKey, boolean>>({
    income: true,
    expenses: true,
    result: true,
  })
  const [activeIndex, setActiveIndex] = useState<number | null>(null)
  const tooltipId = useId()
  const compact = useMediaQuery('(max-width: 639px)')
  const geometry = compact ? COMPACT_GEOMETRY : DESKTOP_GEOMETRY
  const { width: WIDTH, height: HEIGHT, margin: MARGIN } = geometry
  const PLOT_WIDTH = WIDTH - MARGIN.left - MARGIN.right
  const PLOT_HEIGHT = HEIGHT - MARGIN.top - MARGIN.bottom

  const chartBuckets = useMemo(() => aggregateForDisplay(buckets), [buckets])
  const selectedSeries = SERIES.filter((series) => visible[series.key])

  if (buckets.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-body-sm text-ink-muted">
        Sem movimentações para montar a evolução no período selecionado.
      </p>
    )
  }

  const values = chartBuckets.flatMap((bucket) =>
    selectedSeries.map((series) => toFiniteNumber(bucket[series.key])),
  )
  const maximum = Math.max(0, ...values)
  const minimum = Math.min(0, ...values)
  const span = maximum - minimum || 1
  const yMaximum = maximum + span * 0.08
  const yMinimum = minimum - (minimum < 0 ? span * 0.08 : 0)
  const x = (index: number) =>
    MARGIN.left +
    (chartBuckets.length === 1 ? PLOT_WIDTH / 2 : (index / (chartBuckets.length - 1)) * PLOT_WIDTH)
  const y = (value: number) =>
    MARGIN.top + ((yMaximum - value) / (yMaximum - yMinimum || 1)) * PLOT_HEIGHT

  const ticks = buildTicks(yMinimum, yMaximum, geometry.ticks)
  const active = activeIndex === null ? null : chartBuckets[activeIndex]
  const activeX = activeIndex === null ? 0 : x(activeIndex)
  const labelStep = Math.max(1, Math.ceil(chartBuckets.length / geometry.maxLabels))

  function toggleSeries(key: SeriesKey) {
    setVisible((current) => {
      const selectedCount = SERIES.filter((series) => current[series.key]).length
      if (current[key] && selectedCount === 1) return current
      return { ...current, [key]: !current[key] }
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <fieldset className="flex flex-wrap items-center gap-2">
          <legend className="sr-only">Séries exibidas</legend>
          {SERIES.map((series) => {
            const checked = visible[series.key]
            const selectedCount = SERIES.filter((item) => visible[item.key]).length
            return (
              <label
                key={series.key}
                className={[
                  'inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-pill border px-3 text-caption font-semibold transition-colors sm:min-h-9',
                  checked
                    ? 'border-line bg-surface-muted text-ink'
                    : 'border-line bg-surface text-ink-muted',
                ].join(' ')}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={checked && selectedCount === 1}
                  onChange={() => toggleSeries(series.key)}
                  className="size-3.5 accent-[var(--df-primary)]"
                />
                <span
                  aria-hidden="true"
                  className="h-0.5 w-5 rounded-full"
                  style={{
                    backgroundColor: series.stroke,
                    backgroundImage: series.dash
                      ? `repeating-linear-gradient(to right, ${series.stroke} 0 3px, transparent 3px 6px)`
                      : undefined,
                  }}
                />
                {series.label}
              </label>
            )
          })}
        </fieldset>
        <p className="text-caption text-ink-muted">
          {chartBuckets.length < buckets.length
            ? `${buckets.length} períodos agrupados em ${chartBuckets.length} pontos visuais`
            : `${buckets.length} período(s)`}
        </p>
      </div>

      <div className="relative">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          className="h-auto w-full overflow-visible"
          role="img"
          aria-label={`Evolução de ${selectedSeries
            .map((series) => series.label.toLowerCase())
            .join(
              ', ',
            )} em ${chartBuckets.length} período(s). Consulte a tabela e os pontos navegáveis para os valores exatos.`}
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line
                x1={MARGIN.left}
                x2={WIDTH - MARGIN.right}
                y1={y(tick)}
                y2={y(tick)}
                stroke={tick === 0 ? 'var(--df-line-strong)' : 'var(--df-line)'}
                strokeWidth={1}
                strokeDasharray={tick === 0 ? undefined : '3 5'}
              />
              <text
                x={MARGIN.left - 10}
                y={y(tick) + 4}
                textAnchor="end"
                className="fill-[var(--df-ink-muted)]"
                style={{ fontSize: geometry.labelSize }}
              >
                {formatCompact(tick)}
              </text>
            </g>
          ))}

          {chartBuckets.map((bucket, index) => {
            if (index % labelStep !== 0 && index !== chartBuckets.length - 1) return null
            return (
              <text
                key={bucket.start}
                x={x(index)}
                y={HEIGHT - 12}
                textAnchor={
                  index === 0 ? 'start' : index === chartBuckets.length - 1 ? 'end' : 'middle'
                }
                className="fill-[var(--df-ink-muted)]"
                style={{ fontSize: geometry.labelSize }}
              >
                {bucket.label}
              </text>
            )
          })}

          {active ? (
            <line
              x1={activeX}
              x2={activeX}
              y1={MARGIN.top}
              y2={HEIGHT - MARGIN.bottom}
              stroke="var(--df-primary)"
              strokeWidth={1.5}
              strokeDasharray="4 4"
            />
          ) : null}

          {selectedSeries.map((series) => {
            const points = chartBuckets.map((bucket, index) => ({
              x: x(index),
              y: y(toFiniteNumber(bucket[series.key])),
            }))
            if (points.length === 0) return null
            const line = points
              .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
              .join(' ')
            const area = `${line} L ${points[points.length - 1]?.x ?? 0} ${y(0)} L ${points[0]?.x ?? 0} ${y(0)} Z`
            return (
              <g key={series.key}>
                {series.fill ? <path d={area} fill={series.fill} stroke="none" /> : null}
                <path
                  d={line}
                  fill="none"
                  stroke={series.stroke}
                  strokeWidth={2.25}
                  strokeDasharray={series.dash}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  vectorEffect="non-scaling-stroke"
                />
              </g>
            )
          })}
        </svg>

        <div
          className="absolute"
          style={{
            left: `${(MARGIN.left / WIDTH) * 100}%`,
            right: `${(MARGIN.right / WIDTH) * 100}%`,
            top: `${(MARGIN.top / HEIGHT) * 100}%`,
            bottom: `${(MARGIN.bottom / HEIGHT) * 100}%`,
          }}
        >
          <div
            className="grid h-full"
            style={{ gridTemplateColumns: `repeat(${chartBuckets.length}, minmax(0, 1fr))` }}
          >
            {chartBuckets.map((bucket, index) => (
              <button
                key={bucket.start}
                type="button"
                aria-label={`${bucket.label}: receitas ${formatMoney(bucket.income, 'BRL')}, despesas ${formatMoney(bucket.expenses, 'BRL')}, resultado ${formatMoney(bucket.result, 'BRL')}. Ativar detalhamento.`}
                aria-describedby={activeIndex === index ? tooltipId : undefined}
                onFocus={() => setActiveIndex(index)}
                onMouseEnter={() => setActiveIndex(index)}
                onMouseLeave={() =>
                  setActiveIndex((current) => (current === index ? null : current))
                }
                onBlur={() => setActiveIndex((current) => (current === index ? null : current))}
                onClick={() => onDrillDown(bucket)}
                className="h-full w-full rounded-sm focus-visible:bg-primary-soft/60 focus-visible:outline-2 focus-visible:outline-focus"
              />
            ))}
          </div>
        </div>

        {active ? (
          <div
            id={tooltipId}
            role="status"
            className="pointer-events-none absolute top-0 z-10 w-48 -translate-x-1/2 rounded-card border border-line bg-surface-raised p-2.5 text-caption shadow-[var(--shadow-overlay)]"
            style={{
              left: `${Math.min(86, Math.max(14, (activeX / WIDTH) * 100))}%`,
            }}
          >
            <p className="font-semibold text-ink">{active.label}</p>
            <dl className="mt-1 space-y-0.5">
              <div className="flex justify-between gap-2">
                <dt className="text-income">Receitas</dt>
                <dd className="numeric text-ink">{formatMoney(active.income, 'BRL')}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-expense">Despesas</dt>
                <dd className="numeric text-ink">{formatMoney(active.expenses, 'BRL')}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-ink-muted">Resultado</dt>
                <dd className="numeric font-semibold text-ink">
                  {formatMoney(active.result, 'BRL')}
                </dd>
              </div>
            </dl>
            {active.aggregated ? (
              <p className="mt-1 text-ink-muted">Ponto agregado para leitura</p>
            ) : null}
          </div>
        ) : null}
      </div>

      <details className="rounded-card border border-line">
        <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
          Ver tabela da evolução temporal
        </summary>
        <Table caption="Evolução temporal de receitas, despesas e resultado por período">
          <THead>
            <TR>
              <TH>Período</TH>
              <TH className="text-right">Receitas</TH>
              <TH className="text-right">Despesas</TH>
              <TH className="text-right">Resultado</TH>
              <TH className="text-right">Detalhar</TH>
            </TR>
          </THead>
          <TBody>
            {buckets.map((bucket) => {
              const selected = selectedStart === bucket.start
              return (
                <TR key={bucket.start} className={selected ? 'bg-primary-soft' : undefined}>
                  <TH scope="row" className="text-left font-medium text-ink">
                    {bucket.label}
                  </TH>
                  <TD className="numeric text-right text-income">
                    {formatMoney(bucket.income, 'BRL')}
                  </TD>
                  <TD className="numeric text-right text-expense">
                    {formatMoney(bucket.expenses, 'BRL')}
                  </TD>
                  <TD className="numeric text-right font-semibold text-ink">
                    {formatMoney(bucket.result, 'BRL')}
                  </TD>
                  <TD className="text-right">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      aria-pressed={selected}
                      aria-label={`Detalhar ${bucket.label}`}
                      onClick={() => onDrillDown(bucket)}
                    >
                      Detalhar
                    </Button>
                  </TD>
                </TR>
              )
            })}
          </TBody>
        </Table>
      </details>
    </div>
  )
}

function aggregateForDisplay(buckets: readonly DashboardBucket[]): ChartBucket[] {
  if (buckets.length <= MAX_VISUAL_POINTS) {
    return buckets.map((bucket) => ({ ...bucket, aggregated: false }))
  }

  const groups = new Map<string, DashboardBucket[]>()
  for (const bucket of buckets) {
    const key = bucket.start.slice(0, 7)
    const current = groups.get(key) ?? []
    current.push(bucket)
    groups.set(key, current)
  }

  return Array.from(groups.values()).map((group) => {
    const first = group[0]
    const last = group[group.length - 1]
    const start = first?.start ?? ''
    const end = last?.end ?? start
    const reference = start || end
    const label = reference
      ? new Intl.DateTimeFormat('pt-BR', {
          month: 'short',
          year: 'numeric',
          timeZone: 'UTC',
        }).format(new Date(`${reference}T12:00:00Z`))
      : 'Período'
    return {
      label,
      start,
      end,
      income: addDecimals(group.map((bucket) => bucket.income)),
      expenses: addDecimals(group.map((bucket) => bucket.expenses)),
      result: addDecimals(group.map((bucket) => bucket.result)),
      aggregated: true,
    }
  })
}

function buildTicks(minimum: number, maximum: number, count: number): number[] {
  if (maximum === minimum) return [minimum]
  return Array.from(
    { length: count + 1 },
    (_, index) => minimum + ((maximum - minimum) / count) * index,
  )
}

const compactFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  maximumFractionDigits: 1,
})

function formatCompact(value: number): string {
  return compactFormatter.format(value)
}
