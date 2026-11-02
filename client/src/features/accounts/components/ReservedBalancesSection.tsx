import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { DisplayNameEditor } from '@/components/DisplayNameEditor'
import { formatDate, formatMoney, formatRatePercent } from '@/lib/format'
import type {
  Account,
  ReservedBalance,
  ReservedBalanceAmount,
  ReservedBalanceTreatment,
} from '@/lib/api/finance'

import { ReservedBalanceHistory } from './ReservedBalanceHistory'

const TREATMENT_LABELS: Record<ReservedBalanceTreatment, string> = {
  UNKNOWN: 'Herdar da conexão / não definido',
  INCLUDED_IN_BALANCE: 'Já incluído no saldo da conta',
  SEPARATE_ASSET: 'Ativo separado no patrimônio',
}

const TREATMENT_HINTS: Record<ReservedBalanceTreatment, string> = {
  UNKNOWN: 'Usa o padrão da conexão; se não houver, o valor não entra no patrimônio.',
  INCLUDED_IN_BALANCE:
    'O saldo da conta já contém a reserva; ela aparece apenas como detalhamento.',
  SEPARATE_ASSET:
    'O saldo da conta não contém a reserva; ela é somada ao patrimônio como ativo separado.',
}

const SOURCE_LABELS: Record<string, string> = {
  ACCOUNT: 'Definido nesta conta.',
  CONNECTION: 'Herdado da conexão.',
  UNKNOWN: 'Não definido.',
}

function formatFraction(value: string | null | undefined): string | null {
  if (!value || !Number.isFinite(Number(value))) return null
  return formatRatePercent(value)
}

function formatRemuneration(amount: ReservedBalanceAmount): string {
  const rate =
    formatFraction(amount.postFixedIndexerPercentage) ?? formatFraction(amount.preFixedRate)
  const parts = [
    amount.indexer,
    rate,
    amount.rateType,
    amount.ratePeriodicity,
    amount.calculation,
  ].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : 'Informada pelo provedor'
}

interface ReservedBalancesSectionProps {
  readonly account: Account
  readonly items: readonly ReservedBalance[]
  readonly loading: boolean
  readonly error: boolean
  readonly onRename: (id: string, displayName: string) => Promise<unknown>
  readonly onReset: (id: string) => Promise<unknown>
  readonly onTreatmentChange: (treatment: ReservedBalanceTreatment) => Promise<unknown>
}

/** Saldos reservados da conta, com nome local e tratamento no patrimônio. */
export function ReservedBalancesSection({
  account,
  items,
  loading,
  error,
  onRename,
  onReset,
  onTreatmentChange,
}: ReservedBalancesSectionProps) {
  const treatment = (account.reservedBalanceTreatment ?? 'UNKNOWN') as ReservedBalanceTreatment
  const source = account.reservedBalanceTreatmentSource ?? 'UNKNOWN'
  const selectedTreatment = source === 'CONNECTION' ? 'UNKNOWN' : treatment
  const treatmentHint =
    source === 'CONNECTION'
      ? `Herdado da conexão: ${TREATMENT_LABELS[treatment]}.`
      : `${TREATMENT_HINTS[treatment]} ${SOURCE_LABELS[source]}`

  return (
    <Card className="p-5">
      <section aria-labelledby="reserved-balances-heading" className="space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2
              id="reserved-balances-heading"
              className="text-section-title font-semibold text-ink"
            >
              Saldos reservados
            </h2>
            <p className="mt-1 text-caption text-ink-muted">
              Valores reservados pelo banco ou pela carteira, como cofrinhos, caixinhas e bloqueios.
              Não são contas nem investimentos.
            </p>
          </div>
          <div className="text-right">
            <p className="text-caption text-ink-muted">Total reservado</p>
            <p className="numeric text-metric-sm font-semibold text-ink">
              {formatMoney(account.reservedBalanceTotal, account.currency || 'BRL')}
            </p>
          </div>
        </div>

        <div className="rounded-card border border-line bg-surface-muted p-3">
          <label htmlFor="reserved-balance-treatment" className="text-body-sm font-medium text-ink">
            Tratamento no patrimônio
          </label>
          <Select
            id="reserved-balance-treatment"
            value={selectedTreatment}
            className="mt-2 max-w-sm"
            onChange={(event) =>
              void onTreatmentChange(event.target.value as ReservedBalanceTreatment)
            }
          >
            {Object.entries(TREATMENT_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
          <p className="mt-2 text-caption leading-relaxed text-ink-muted">{treatmentHint}</p>
        </div>

        {loading ? (
          <p role="status" className="text-body-sm text-ink-muted">
            Carregando saldos reservados…
          </p>
        ) : null}

        {error ? (
          <Alert variant="danger" title="Saldos reservados indisponíveis">
            Não foi possível carregar o detalhe dos valores reservados.
          </Alert>
        ) : null}

        {!loading && !error && items.length === 0 ? (
          <p className="rounded-card border border-line bg-surface-muted p-4 text-body-sm text-ink-muted">
            O provedor sinalizou saldo reservado, mas não enviou o detalhe. O valor não foi
            estimado.
          </p>
        ) : null}

        {items.length > 0 ? (
          <ul className="divide-y divide-line rounded-card border border-line bg-surface">
            {items.map((item) => (
              <li key={item.id} className="space-y-3 px-3 py-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-body-sm font-semibold text-ink">{item.name}</p>
                    <p className="text-caption text-ink-muted">
                      {item.nameCustomized && item.providerName
                        ? `Nome original: ${item.providerName} · `
                        : ''}
                      Referência: {formatDate(item.referenceDate)}
                    </p>
                    {item.amounts && item.amounts.length > 0 ? (
                      <p className="mt-0.5 text-caption text-ink-muted">
                        Remuneração: {item.amounts.map(formatRemuneration).join(' · ')}
                      </p>
                    ) : null}
                  </div>
                  <div className="flex items-center gap-2">
                    {item.stale ? <Badge variant="warning">Referência antiga</Badge> : null}
                    <span className="numeric text-body-sm font-semibold text-ink">
                      {formatMoney(item.amount, item.currency || account.currency || 'BRL')}
                    </span>
                  </div>
                </div>
                <DisplayNameEditor
                  currentName={item.name}
                  originalName={item.providerName}
                  customized={item.nameCustomized}
                  onRename={(displayName) => onRename(item.id, displayName)}
                  onReset={() => onReset(item.id)}
                />
                <ReservedBalanceHistory reservedBalanceId={item.id} />
              </li>
            ))}
          </ul>
        ) : null}
      </section>
    </Card>
  )
}
