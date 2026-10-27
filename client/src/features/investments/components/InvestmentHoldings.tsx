import { formatDateTime, formatMoney } from '@/features/accounts/format'
import type { InvestmentHolding } from '@/features/integration/api'

import { formatQuantity, formatReferenceDate, formatUnitPrice } from '../format'
import { assetClassLabel, CLASSIFICATION_SOURCE_LABELS, HOLDING_STATUS_LABELS } from '../labels'

interface InvestmentHoldingsProps {
  readonly items: readonly InvestmentHolding[]
}

/** Lista de posições importadas, com detalhes sob demanda e sem recomendação. */
export function InvestmentHoldings({ items }: InvestmentHoldingsProps) {
  if (items.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-4 text-sm text-ink-muted">
        Nenhuma posição neste filtro. Posições encerradas e indisponíveis podem ser consultadas em
        “Todas”.
      </p>
    )
  }

  return (
    <ul className="space-y-3" aria-label="Posições importadas de investimento">
      {items.map((holding) => (
        <InvestmentHoldingCard key={holding.id} holding={holding} />
      ))}
    </ul>
  )
}

function InvestmentHoldingCard({ holding }: { readonly holding: InvestmentHolding }) {
  const currency = holding.currency || 'BRL'
  const closed = holding.status === 'CLOSED'
  const unavailable = holding.unavailableAt != null
  const statusLabel = unavailable
    ? 'Indisponível'
    : (HOLDING_STATUS_LABELS[holding.status] ?? holding.status)

  return (
    <li className="space-y-3 rounded-card border border-line bg-surface p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="truncate text-base font-semibold text-ink">
              {holding.name ?? 'Posição sem nome'}
            </h3>
            {holding.code ? (
              <span className="rounded-card border border-line bg-surface-muted px-2 py-0.5 text-xs font-semibold tracking-wide text-ink-muted">
                {holding.code}
              </span>
            ) : null}
          </div>
          <p className="text-sm text-ink-muted">
            {assetClassLabel(holding.assetClass)} · {holding.type}
            {holding.subtype ? ` / ${holding.subtype}` : ''}
          </p>
          <p className="text-xs text-ink-muted">
            {CLASSIFICATION_SOURCE_LABELS[holding.classificationSource] ??
              holding.classificationSource}
            {holding.stale ? ' · referência antiga' : ''}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2 sm:justify-end">
          <span
            className={
              closed || unavailable
                ? 'rounded-card border border-line bg-surface-muted px-2 py-1 text-xs font-medium text-ink-muted'
                : 'rounded-card border border-income/30 bg-income/5 px-2 py-1 text-xs font-medium text-income'
            }
          >
            {statusLabel}
          </span>
          <span className="rounded-card border border-line bg-surface-muted px-2 py-1 text-xs font-medium text-ink">
            {assetClassLabel(holding.assetClass)}
          </span>
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3 xl:grid-cols-5">
        <Value label="Valor atual" value={formatMoney(holding.balance, currency)} strong />
        <Value label="Quantidade" value={formatQuantity(holding.quantity)} />
        <Value label="Preço unitário" value={formatUnitPrice(holding.unitValue, currency)} />
        <Value
          label="Resultado informado"
          value={
            holding.amountProfit == null
              ? 'Não informado'
              : formatMoney(holding.amountProfit, currency)
          }
        />
        <Value label="Referência" value={formatReferenceDate(holding.referenceDate)} />
      </dl>

      <details className="rounded-card border border-line bg-surface-muted p-3">
        <summary className="cursor-pointer text-sm font-medium text-ink">
          Detalhes do provedor
        </summary>
        <dl className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
          <Value label="Valor bruto" value={formatMoney(holding.amount, currency)} />
          <Value label="Custo original" value={formatMoney(holding.amountOriginal, currency)} />
          <Value
            label="Disponível para saque"
            value={formatMoney(holding.amountWithdrawal, currency)}
          />
          <Value label="IR informado" value={formatMoney(holding.taxes, currency)} />
          <Value label="IOF informado" value={formatMoney(holding.taxes2, currency)} />
          <Value
            label="Remuneração contratada"
            value={
              holding.rate == null
                ? 'Não informada'
                : `${holding.rate}${holding.rateType ? ` · ${holding.rateType}` : ''}`
            }
          />
          <Value
            label="Taxa fixa anual"
            value={
              holding.fixedAnnualRate == null ? 'Não informada' : `${holding.fixedAnnualRate}% a.a.`
            }
          />
          <Value
            label="Último mês"
            value={holding.lastMonthRate == null ? 'Não informado' : `${holding.lastMonthRate}%`}
          />
          <Value
            label="12 meses"
            value={
              holding.lastTwelveMonthsRate == null
                ? 'Não informado'
                : `${holding.lastTwelveMonthsRate}%`
            }
          />
          <Value label="Atualizado na origem" value={formatDateTime(holding.providerUpdatedAt)} />
          <Value label="Última leitura" value={formatDateTime(holding.syncedAt)} />
          {holding.unavailableAt ? (
            <Value label="Indisponível desde" value={formatDateTime(holding.unavailableAt)} />
          ) : null}
        </dl>
        <p className="mt-3 text-xs leading-relaxed text-ink-muted">
          “Não informado” significa que o provedor não enviou o campo. O sistema não estima
          rentabilidade a partir de histórico incompleto.
        </p>
      </details>
    </li>
  )
}

function Value({
  label,
  value,
  strong = false,
}: {
  readonly label: string
  readonly value: string
  readonly strong?: boolean
}) {
  return (
    <div>
      <dt className="text-xs text-ink-muted">{label}</dt>
      <dd className={`numeric mt-1 ${strong ? 'text-base font-semibold text-ink' : 'text-ink'}`}>
        {value}
      </dd>
    </div>
  )
}
