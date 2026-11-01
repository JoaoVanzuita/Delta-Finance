import { useMemo, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Camera } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { ApiError } from '@/lib/api/problem'
import { invalidateWealthViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'
import { toFiniteNumber } from '@/lib/format'
import { financeApi, type WealthSnapshotSummary } from '@/features/integration/api'

import { COMPLETENESS_LABELS, formatDate, formatMoney } from '../format'
import { WealthSnapshotDrawer } from './WealthSnapshotDrawer'

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

/** Histórico mensal de patrimônio; só existe a partir do primeiro snapshot real. */
export function WealthHistorySection() {
  const queryClient = useQueryClient()
  const [range, setRange] = useState<(typeof RANGES)[number]['months']>(12)
  const [snapshotId, setSnapshotId] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const historyQuery = useQuery({
    queryKey: [...queryKeys.wealthHistory, range],
    queryFn: () => financeApi.wealthHistory(localDate(range - 1)),
  })
  const capture = useMutation({
    mutationFn: () => financeApi.createWealthSnapshot({}),
    onSuccess: async () => {
      setMessage('Snapshot registrado.')
      await invalidateWealthViews(queryClient)
    },
    onError: (caught) => {
      setMessage(
        caught instanceof ApiError ? caught.message : 'Não foi possível registrar o snapshot.',
      )
    },
  })

  const points = useMemo(() => [...(historyQuery.data ?? [])].reverse(), [historyQuery.data])

  return (
    <section aria-labelledby="wealth-history-heading" className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="wealth-history-heading" className="text-section-title font-semibold text-ink">
            Evolução patrimonial
          </h2>
          <p className="text-caption text-ink-muted">
            Posições mensais imutáveis. O histórico começa no primeiro snapshot; meses anteriores
            não são reconstruídos.
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <div className="flex flex-wrap gap-1" aria-label="Período do histórico">
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
          <Button
            type="button"
            size="sm"
            variant="secondary"
            loading={capture.isPending}
            loadingLabel="Registrando…"
            leadingIcon={<Camera aria-hidden="true" className="size-4" />}
            onClick={() => capture.mutate()}
          >
            Registrar snapshot
          </Button>
        </div>
      </div>

      {message ? (
        <Alert variant="info" role="status">
          {message}
        </Alert>
      ) : null}
      {historyQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <span className="sr-only">Carregando histórico patrimonial…</span>
          <Skeleton className="h-64 rounded-card" />
        </div>
      ) : null}
      {historyQuery.isError ? (
        <Alert variant="danger" title="Histórico patrimonial indisponível">
          {historyQuery.error instanceof ApiError
            ? historyQuery.error.message
            : 'Não foi possível carregar o histórico.'}
        </Alert>
      ) : null}
      {historyQuery.isSuccess && points.length === 0 ? (
        <p className="rounded-card border border-dashed border-line-strong bg-surface p-4 text-body-sm text-ink-muted">
          Ainda não há snapshots. O primeiro será criado no próximo fechamento mensal ou em
          “Registrar snapshot”.
        </p>
      ) : null}

      {points.length > 0 ? (
        <>
          <WealthHistoryChart points={points} />
          <details className="rounded-card border border-line bg-surface">
            <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
              Ver tabela do histórico
            </summary>
            <div className="overflow-x-auto">
              <Table caption="Histórico mensal de patrimônio">
                <THead>
                  <TR>
                    <TH>Mês</TH>
                    <TH className="text-right">Ativos</TH>
                    <TH className="text-right">Passivos</TH>
                    <TH className="text-right">Patrimônio líquido</TH>
                    <TH>Completude</TH>
                    <TH />
                  </TR>
                </THead>
                <TBody>
                  {[...points].reverse().map((point) => (
                    <TR key={point.id}>
                      <TD>{formatDate(point.referenceDate)}</TD>
                      <TD className="numeric text-right">{formatMoney(point.grossAssets)}</TD>
                      <TD className="numeric text-right text-expense">
                        {formatMoney(point.liabilities)}
                      </TD>
                      <TD className="numeric text-right font-semibold">
                        {formatMoney(point.netWorth)}
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
                          onClick={() => setSnapshotId(point.id)}
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

function WealthHistoryChart({ points }: { readonly points: readonly WealthSnapshotSummary[] }) {
  const width = 640
  const height = 220
  const left = 58
  const right = 16
  const top = 16
  const bottom = 28
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const values = points.flatMap((point) => [
    toFiniteNumber(point.grossAssets),
    toFiniteNumber(point.liabilities),
    toFiniteNumber(point.netWorth),
  ])
  const max = Math.max(1, ...values)
  const y = (value: number) => top + (1 - value / max) * plotHeight
  const x = (index: number) =>
    points.length === 1 ? left + plotWidth / 2 : left + (index / (points.length - 1)) * plotWidth
  const path = (key: 'grossAssets' | 'liabilities' | 'netWorth') =>
    points
      .map(
        (point, index) => `${index === 0 ? 'M' : 'L'} ${x(index)} ${y(toFiniteNumber(point[key]))}`,
      )
      .join(' ')
  const labelStep = Math.max(1, Math.ceil(points.length / 6))
  const last = points.at(-1)

  return (
    <figure className="rounded-card border border-line bg-surface p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap gap-4 text-caption text-ink-muted">
          <Legend color="var(--df-primary)" label="Ativos" />
          <Legend color="var(--df-expense)" label="Passivos" />
          <Legend color="var(--df-income)" label="Patrimônio líquido" />
        </div>
        {last ? (
          <p className="text-caption text-ink-muted">Último: {formatDate(last.referenceDate)}</p>
        ) : null}
      </div>
      <svg
        role="img"
        aria-label={`Evolução patrimonial em ${points.length} mês(es). ${last ? `Patrimônio líquido mais recente: ${formatMoney(last.netWorth)}.` : ''}`}
        viewBox={`0 0 ${width} ${height}`}
        className="mt-3 h-auto w-full"
      >
        {[0, 0.5, 1].map((fraction) => {
          const lineY = top + fraction * plotHeight
          return (
            <g key={fraction}>
              <line
                x1={left}
                x2={width - right}
                y1={lineY}
                y2={lineY}
                stroke="var(--df-line)"
                strokeDasharray={fraction === 1 ? undefined : '3 5'}
              />
              <text
                x={left - 8}
                y={lineY + 4}
                textAnchor="end"
                className="fill-[var(--df-ink-muted)] text-[10px]"
              >
                {compactMoney(max * (1 - fraction))}
              </text>
            </g>
          )
        })}
        <path
          d={path('grossAssets')}
          fill="none"
          stroke="var(--df-primary)"
          strokeWidth={2}
          vectorEffect="non-scaling-stroke"
        />
        <path
          d={path('liabilities')}
          fill="none"
          stroke="var(--df-expense)"
          strokeWidth={2}
          vectorEffect="non-scaling-stroke"
        />
        <path
          d={path('netWorth')}
          fill="none"
          stroke="var(--df-income)"
          strokeWidth={2.5}
          vectorEffect="non-scaling-stroke"
        />
        {points.map((point, index) => (
          <g key={point.id}>
            <circle
              cx={x(index)}
              cy={y(toFiniteNumber(point.netWorth))}
              r={2.5}
              fill="var(--df-income)"
            >
              <title>
                {formatDate(point.referenceDate)}: patrimônio líquido {formatMoney(point.netWorth)}
              </title>
            </circle>
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
          </g>
        ))}
      </svg>
      <figcaption className="sr-only">
        Gráfico de linhas com ativos, passivos e patrimônio líquido. A tabela abaixo repete os
        valores exatos.
      </figcaption>
    </figure>
  )
}

function Legend({ color, label }: { readonly color: string; readonly label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className="size-2.5 rounded-full"
        style={{ backgroundColor: color }}
        aria-hidden="true"
      />
      {label}
    </span>
  )
}

function compactMoney(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)
}
