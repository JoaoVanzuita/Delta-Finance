import { Drawer } from '@/components/ui/drawer'
import { Badge } from '@/components/ui/badge'
import { formatDateTime, formatMoney } from '@/lib/format'
import type { InvestmentHolding } from '@/features/integration/api'

import { formatQuantity, formatReferenceDate, formatUnitPrice } from '../format'
import { assetClassLabel, CLASSIFICATION_SOURCE_LABELS, HOLDING_STATUS_LABELS } from '../labels'
import { HoldingSettingsForm } from './HoldingSettingsForm'

export function InvestmentHoldingDrawer({
  holding,
  open,
  onClose,
  onChanged,
}: {
  readonly holding: InvestmentHolding | null
  readonly open: boolean
  readonly onClose: () => void
  readonly onChanged: () => void
}) {
  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={holding?.name ?? 'Detalhes da posição'}
      description="Posição importada do provedor. Valores ausentes não são tratados como zero."
      width="lg"
      bodyClassName="space-y-5"
    >
      {holding ? (
        <>
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <p className="text-body-sm text-ink-muted">
                {holding.code ?? 'Sem código'} · {assetClassLabel(holding.assetClass)}
              </p>
              <p className="numeric mt-1 text-metric-lg font-semibold text-ink">
                {formatMoney(holding.balance, holding.currency || 'BRL')}
              </p>
              <p className="text-caption text-ink-muted">Valor atual informado pelo provedor</p>
            </div>
            <Badge variant={holding.unavailableAt ? 'neutral' : 'income'}>
              {holding.unavailableAt
                ? 'Indisponível'
                : (HOLDING_STATUS_LABELS[holding.status] ?? holding.status)}
            </Badge>
          </div>

          <dl className="grid grid-cols-2 gap-3 rounded-card border border-line bg-surface-muted p-4 text-body-sm sm:grid-cols-3">
            <Value label="Quantidade" value={formatQuantity(holding.quantity)} />
            <Value
              label="Preço unitário"
              value={formatUnitPrice(holding.unitValue, holding.currency || 'BRL')}
            />
            <Value
              label="Resultado do provedor"
              value={formatMoney(holding.amountProfit, holding.currency || 'BRL')}
            />
            <Value
              label="Resultado estimado"
              value={
                holding.estimatedProfit == null
                  ? 'Informe o custo'
                  : formatMoney(holding.estimatedProfit, holding.currency || 'BRL')
              }
            />
            <Value
              label="Rentabilidade estimada"
              value={
                holding.estimatedReturnPercentage == null
                  ? 'Não calculável'
                  : `${holding.estimatedReturnPercentage}%`
              }
            />
            <Value label="Referência" value={formatReferenceDate(holding.referenceDate)} />
            <Value
              label="Classificação"
              value={
                CLASSIFICATION_SOURCE_LABELS[holding.classificationSource] ??
                holding.classificationSource
              }
            />
          </dl>

          <details className="rounded-card border border-line">
            <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
              Informações do provedor
            </summary>
            <dl className="grid grid-cols-2 gap-3 border-t border-line p-4 text-body-sm sm:grid-cols-3">
              <Value
                label="Valor bruto"
                value={formatMoney(holding.amount, holding.currency || 'BRL')}
              />
              <Value
                label="Custo original"
                value={formatMoney(holding.amountOriginal, holding.currency || 'BRL')}
              />
              <Value
                label="Custo usado"
                value={
                  holding.costBasis == null
                    ? 'Não informado'
                    : `${formatMoney(holding.costBasis, holding.currency || 'BRL')} · ${
                        holding.costBasisSource === 'MANUAL'
                          ? 'informado por você'
                          : holding.costBasisSource === 'PROVIDER'
                            ? 'provedor'
                            : 'sem custo'
                      }`
                }
              />
              <Value
                label="Proventos considerados"
                value={formatMoney(holding.incomeTotal, holding.currency || 'BRL')}
              />
              <Value
                label="Impostos informados"
                value={formatMoney(holding.taxes, holding.currency || 'BRL')}
              />
              <Value
                label="IOF informado"
                value={formatMoney(holding.taxes2, holding.currency || 'BRL')}
              />
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
                  holding.fixedAnnualRate == null
                    ? 'Não informada'
                    : `${holding.fixedAnnualRate}% a.a.`
                }
              />
              <Value label="Última leitura" value={formatDateTime(holding.syncedAt)} />
            </dl>
          </details>

          <HoldingSettingsForm holding={holding} onChanged={onChanged} />
        </>
      ) : null}
    </Drawer>
  )
}

function Value({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-caption text-ink-muted">{label}</dt>
      <dd className="numeric mt-0.5 text-ink">{value}</dd>
    </div>
  )
}
