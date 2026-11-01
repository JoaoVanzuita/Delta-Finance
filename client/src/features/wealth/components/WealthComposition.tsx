import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { addDecimals, formatMoney, formatPercent, toFiniteNumber } from '@/lib/format'
import type { WealthItem } from '@/features/integration/api'

import { ORIGIN_LABELS, SOURCE_LABELS, formatDate } from '../format'

const ASSET_TYPES = new Set(['ACCOUNT', 'INVESTMENT', 'MANUAL_ASSET'])
const LIABILITY_TYPES = new Set(['CARD', 'MANUAL_LIABILITY'])

interface Group {
  readonly type: string
  readonly label: string
  readonly amount: string
  readonly count: number
  readonly items: readonly WealthItem[]
}

function groupBySource(items: readonly WealthItem[], allowed: Set<string>): Group[] {
  const groups = new Map<string, WealthItem[]>()
  for (const item of items) {
    if (!item.included || !allowed.has(item.sourceType)) continue
    const current = groups.get(item.sourceType) ?? []
    current.push(item)
    groups.set(item.sourceType, current)
  }
  return Array.from(groups.entries())
    .map(([type, groupItems]) => ({
      type,
      label: SOURCE_LABELS[type] ?? type,
      amount: addDecimals(groupItems.map((item) => item.value)),
      count: groupItems.length,
      items: groupItems,
    }))
    .sort((left, right) => toFiniteNumber(right.amount) - toFiniteNumber(left.amount))
}

/** Composição patrimonial em ativos e passivos separados, com tabela acessível. */
export function WealthComposition({ items }: { readonly items: readonly WealthItem[] }) {
  const included = items.filter((item) => item.included)
  const excluded = items.filter((item) => !item.included)
  const assets = groupBySource(included, ASSET_TYPES)
  const liabilities = groupBySource(included, LIABILITY_TYPES)
  const assetTotal = addDecimals(
    included.filter((item) => ASSET_TYPES.has(item.sourceType)).map((item) => item.value),
  )
  const liabilityTotal = addDecimals(
    included.filter((item) => LIABILITY_TYPES.has(item.sourceType)).map((item) => item.value),
  )

  return (
    <section aria-labelledby="wealth-composition-heading" className="space-y-4">
      <div>
        <h2 id="wealth-composition-heading" className="text-section-title font-semibold text-ink">
          Composição
        </h2>
        <p className="text-caption text-ink-muted">
          Ativos e passivos separados. Itens fora do cálculo aparecem com o motivo.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <CompositionGroup
          title="Ativos por fonte"
          total={assetTotal}
          groups={assets}
          empty="Nenhum ativo incluído no patrimônio."
          tone="asset"
        />
        <CompositionGroup
          title="Passivos por fonte"
          total={liabilityTotal}
          groups={liabilities}
          empty="Nenhum passivo incluído no patrimônio."
          tone="liability"
        />
      </div>

      <details className="rounded-card border border-line bg-surface">
        <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
          Ver tabela de composição
        </summary>
        <Table caption="Composição do patrimônio por fonte">
          <THead>
            <TR>
              <TH>Fonte</TH>
              <TH>Grupo</TH>
              <TH>Origem</TH>
              <TH className="text-right">Valor</TH>
              <TH>Referência</TH>
            </TR>
          </THead>
          <TBody>
            {included.map((item) => (
              <TR key={`${item.sourceType}-${item.sourceId}`}>
                <TD>{item.description}</TD>
                <TD>
                  <Badge variant="neutral">
                    {SOURCE_LABELS[item.sourceType] ?? item.sourceType}
                  </Badge>
                </TD>
                <TD className="text-ink-muted">{ORIGIN_LABELS[item.origin] ?? item.origin}</TD>
                <TD className="numeric text-right">{formatMoney(item.value)}</TD>
                <TD className="text-ink-muted">{formatDate(item.referenceDate)}</TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </details>

      {excluded.length > 0 ? (
        <section aria-labelledby="wealth-excluded-heading" className="space-y-2">
          <h3 id="wealth-excluded-heading" className="text-body-sm font-semibold text-ink">
            Fora do cálculo ({excluded.length})
          </h3>
          <ul className="divide-y divide-line rounded-card border border-line bg-surface-muted">
            {excluded.map((item) => (
              <li
                key={`${item.sourceType}-${item.sourceId}`}
                className="flex flex-wrap items-start justify-between gap-2 px-3 py-2.5"
              >
                <div>
                  <p className="text-body-sm font-medium text-ink">{item.description}</p>
                  <p className="text-caption text-ink-muted">
                    {SOURCE_LABELS[item.sourceType] ?? item.sourceType} ·{' '}
                    {item.exclusionReason ?? 'Motivo não informado.'}
                  </p>
                </div>
                <span className="text-caption font-medium text-ink-muted">Não somado</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </section>
  )
}

function CompositionGroup({
  title,
  total,
  groups,
  empty,
  tone,
}: {
  readonly title: string
  readonly total: string
  readonly groups: readonly Group[]
  readonly empty: string
  readonly tone: 'asset' | 'liability'
}) {
  const totalNumber = toFiniteNumber(total)
  return (
    <div className="rounded-card border border-line bg-surface p-4">
      <div className="flex items-baseline justify-between gap-2">
        <h3 className="text-body-sm font-semibold text-ink">{title}</h3>
        <p
          className={
            tone === 'asset'
              ? 'numeric font-semibold text-ink'
              : 'numeric font-semibold text-expense'
          }
        >
          {formatMoney(total)}
        </p>
      </div>
      {groups.length === 0 ? (
        <p className="mt-3 text-body-sm text-ink-muted">{empty}</p>
      ) : (
        <ul className="mt-4 space-y-3" aria-label={title}>
          {groups.map((group) => {
            const percentage =
              totalNumber > 0 ? (toFiniteNumber(group.amount) / totalNumber) * 100 : 0
            return (
              <li key={group.type}>
                <div className="flex items-baseline justify-between gap-2 text-caption">
                  <span className="font-medium text-ink">{group.label}</span>
                  <span className="numeric text-ink">
                    {formatMoney(group.amount)} · {formatPercent(percentage)}
                  </span>
                </div>
                <span
                  aria-hidden="true"
                  className="mt-1.5 block h-2 overflow-hidden rounded-pill bg-surface-muted"
                >
                  <span
                    className={
                      tone === 'asset'
                        ? 'block h-full rounded-pill bg-primary'
                        : 'block h-full rounded-pill bg-expense'
                    }
                    style={{ width: `${Math.min(100, Math.max(0, percentage))}%` }}
                  />
                </span>
                <ul className="mt-1.5 space-y-0.5 text-caption text-ink-muted">
                  {group.items.map((item) => (
                    <li key={item.sourceId} className="flex justify-between gap-2">
                      <span className="truncate">{item.description}</span>
                      <span className="numeric shrink-0">{formatMoney(item.value)}</span>
                    </li>
                  ))}
                </ul>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
