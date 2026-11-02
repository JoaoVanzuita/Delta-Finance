import { wealthApi } from '@/lib/api/finance'
import { useQuery } from '@tanstack/react-query'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Drawer } from '@/components/ui/drawer'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { formatDateTime, formatMoney as formatMoneyInCurrency } from '@/lib/format'
import { queryKeys } from '@/lib/query-keys'

import { ApiError } from '@/lib/api/problem'
import { assetClassLabel } from '@/features/investments/labels'

import { COMPLETENESS_LABELS, SOURCE_LABELS, formatDate, formatMoney } from '../format'

interface WealthSnapshotDrawerProps {
  readonly open: boolean
  readonly snapshotId: string | null
  readonly onClose: () => void
}

/** Detalhe de um snapshot: totais, composição e itens fora do cálculo. */
export function WealthSnapshotDrawer({ open, snapshotId, onClose }: WealthSnapshotDrawerProps) {
  const query = useQuery({
    queryKey: queryKeys.wealthSnapshot(snapshotId),
    queryFn: () => wealthApi.wealthSnapshot(snapshotId as string),
    enabled: open && Boolean(snapshotId),
  })
  const snapshot = query.data
  const included = snapshot?.items.filter((item) => item.included) ?? []
  const excluded = snapshot?.items.filter((item) => !item.included) ?? []

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={snapshot ? `Posição de ${formatDate(snapshot.referenceDate)}` : 'Snapshot patrimonial'}
      description={
        snapshot
          ? `Capturado em ${formatDateTime(snapshot.capturedAt)} · ${snapshot.itemCount} item(ns)`
          : 'Composição datada do patrimônio.'
      }
      width="lg"
      bodyClassName="space-y-5"
    >
      {query.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <span className="sr-only">Carregando snapshot…</span>
          <Skeleton className="h-28 rounded-card" />
          <Skeleton className="h-52 rounded-card" />
        </div>
      ) : null}

      {query.isError ? (
        <Alert variant="danger" title="Snapshot indisponível">
          {query.error instanceof ApiError
            ? query.error.message
            : 'Não foi possível carregar este snapshot.'}
        </Alert>
      ) : null}

      {snapshot ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="text-caption text-ink-muted">Patrimônio líquido</p>
              <p className="numeric text-metric-lg font-semibold text-ink">
                {formatMoney(snapshot.netWorth)}
              </p>
            </div>
            <Badge
              variant={
                snapshot.completeness === 'COMPLETE'
                  ? 'income'
                  : snapshot.completeness === 'PARTIAL'
                    ? 'warning'
                    : 'info'
              }
            >
              {COMPLETENESS_LABELS[snapshot.completeness] ?? snapshot.completeness}
            </Badge>
          </div>

          <dl className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-card border border-line bg-surface p-4">
              <dt className="text-caption text-ink-muted">Ativos brutos</dt>
              <dd className="numeric mt-1 text-metric-sm font-semibold text-ink">
                {formatMoney(snapshot.grossAssets)}
              </dd>
            </div>
            <div className="rounded-card border border-line bg-surface p-4">
              <dt className="text-caption text-ink-muted">Passivos</dt>
              <dd className="numeric mt-1 text-metric-sm font-semibold text-expense">
                {formatMoney(snapshot.liabilities)}
              </dd>
            </div>
          </dl>

          {snapshot.hasPartialData ? (
            <Alert variant="warning" title="Snapshot parcial">
              Alguns itens não compõem os totais. O motivo aparece na tabela abaixo.
            </Alert>
          ) : null}

          <section aria-labelledby="snapshot-included-heading" className="space-y-2">
            <h3 id="snapshot-included-heading" className="text-body-sm font-semibold text-ink">
              Composição incluída
            </h3>
            {included.length === 0 ? (
              <p className="rounded-card border border-dashed border-line-strong p-4 text-body-sm text-ink-muted">
                Nenhum item incluído neste snapshot.
              </p>
            ) : (
              <Table caption="Itens incluídos no snapshot patrimonial">
                <THead>
                  <TR>
                    <TH>Item</TH>
                    <TH>Fonte</TH>
                    <TH className="text-right">Valor</TH>
                    <TH>Referência</TH>
                  </TR>
                </THead>
                <TBody>
                  {included.map((item) => (
                    <TR key={`${item.sourceType}-${item.sourceId ?? item.description}`}>
                      <TD>
                        {item.description}
                        {item.assetClass ? (
                          <span className="block text-caption text-ink-muted">
                            {assetClassLabel(item.assetClass)}
                          </span>
                        ) : null}
                      </TD>
                      <TD className="text-ink-muted">
                        {SOURCE_LABELS[item.sourceType] ?? item.sourceType}
                      </TD>
                      <TD className="numeric text-right">
                        {formatMoneyInCurrency(item.value, item.currency)}
                      </TD>
                      <TD className="text-ink-muted">{formatDate(item.referenceDate)}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            )}
          </section>

          {excluded.length > 0 ? (
            <section aria-labelledby="snapshot-excluded-heading" className="space-y-2">
              <h3 id="snapshot-excluded-heading" className="text-body-sm font-semibold text-ink">
                Fora do cálculo ({excluded.length})
              </h3>
              <ul className="divide-y divide-line rounded-card border border-line bg-surface-muted">
                {excluded.map((item) => (
                  <li
                    key={`${item.sourceType}-${item.sourceId ?? item.description}`}
                    className="flex flex-wrap items-start justify-between gap-2 px-3 py-2.5"
                  >
                    <div>
                      <p className="text-body-sm font-medium text-ink">
                        {item.description}
                        {item.assetClass ? ` · ${assetClassLabel(item.assetClass)}` : ''}
                      </p>
                      <p className="text-caption text-ink-muted">
                        {SOURCE_LABELS[item.sourceType] ?? item.sourceType} ·{' '}
                        {item.exclusionReason ?? 'Motivo não informado.'}
                      </p>
                    </div>
                    <span className="numeric text-caption text-ink-muted">
                      {formatMoneyInCurrency(item.value, item.currency)}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </>
      ) : null}
    </Drawer>
  )
}
