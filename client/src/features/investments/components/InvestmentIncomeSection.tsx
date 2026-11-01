import { useState } from 'react'

import { useMutation } from '@tanstack/react-query'
import { Plus } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { ApiError } from '@/lib/api/problem'
import { addDecimals, formatMoney, formatPercent } from '@/lib/format'
import {
  financeApi,
  type InvestmentIncome,
  type InvestmentIncomeItem,
} from '@/features/integration/api'

import { formatReferenceDate } from '../format'
import { InvestmentIncomeChart } from './InvestmentIncomeChart'

const INCOME_TYPE_LABELS: Record<string, string> = {
  DIVIDEND: 'Dividendo',
  INTEREST: 'Juro/rendimento',
  JCP: 'JCP',
  RENT: 'Aluguel de FII',
  COUPON: 'Cupom',
  OTHER: 'Outro',
}

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

interface InvestmentIncomeSectionProps {
  readonly income: InvestmentIncome
  readonly onCreate: () => void
  readonly onEdit: (item: InvestmentIncomeItem) => void
  readonly onChanged: () => void
}

/** Proventos do provedor e manuais; cadastro/edição acontecem em drawer. */
export function InvestmentIncomeSection({
  income,
  onCreate,
  onEdit,
  onChanged,
}: InvestmentIncomeSectionProps) {
  const [confirmingId, setConfirmingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const remove = useMutation({
    mutationFn: (id: string) => financeApi.deleteInvestmentIncome(id),
    onSuccess: () => {
      setConfirmingId(null)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível excluir o provento.'))
    },
  })

  const byAsset = new Map<
    string,
    { name: string; code?: string | null; amount: string; count: number }
  >()
  for (const item of income.items) {
    const key = item.investmentId ?? item.investmentName ?? 'sem-posicao'
    const current = byAsset.get(key)
    if (current) {
      current.amount = addDecimals([current.amount, item.amount])
      current.count += 1
    } else {
      byAsset.set(key, {
        name: item.investmentName ?? 'Posição sem nome',
        code: item.code,
        amount: item.amount,
        count: 1,
      })
    }
  }
  const assetGroups = Array.from(byAsset.values()).sort(
    (left, right) => Number(right.amount) - Number(left.amount),
  )
  const total = income.total

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="overline text-ink-muted">Proventos recebidos</p>
          <p className="numeric mt-1 text-metric-lg font-semibold text-ink">
            {formatMoney(total, 'BRL')}
          </p>
          <p className="mt-1 text-caption text-ink-muted">
            {formatReferenceDate(income.from)} a {formatReferenceDate(income.to)} ·{' '}
            {income.source === 'PROVIDER'
              ? 'provedor'
              : income.source === 'MANUAL'
                ? 'manual'
                : income.source === 'MIXED'
                  ? 'provedor e manual'
                  : 'origem não informada'}
          </p>
        </div>
        <Button type="button" onClick={onCreate} leadingIcon={<Plus className="size-4" />}>
          Registrar provento
        </Button>
      </div>

      <Alert variant="info" title="Proventos não entram no Dashboard">
        Proventos importados e manuais aparecem neste painel e no resultado estimado da posição
        quando há custo conhecido. Eles não são somados automaticamente às receitas do Dashboard.
      </Alert>

      {error ? (
        <p role="alert" className="text-body-sm text-expense">
          {error}
        </p>
      ) : null}

      {income.items.length === 0 ? (
        <div className="rounded-card border border-line bg-surface-muted p-4 text-body-sm leading-relaxed text-ink-muted">
          Nenhum provento informado pelo provedor ou manualmente no período. O sistema não estima
          dividendos, JCP, aluguel de FII ou cupons sem um lançamento.
        </div>
      ) : (
        <>
          <InvestmentIncomeChart byMonth={income.byMonth} />

          <section aria-labelledby="income-by-asset-heading" className="space-y-2">
            <h3 id="income-by-asset-heading" className="text-body-sm font-semibold text-ink">
              Por ativo
            </h3>
            <ul className="divide-y divide-line rounded-card border border-line bg-surface">
              {assetGroups.map((group) => (
                <li
                  key={`${group.name}-${group.code ?? ''}`}
                  className="flex items-center justify-between gap-3 px-3 py-2.5"
                >
                  <span className="min-w-0">
                    <span className="block truncate text-body-sm font-medium text-ink">
                      {group.name}
                    </span>
                    <span className="text-caption text-ink-muted">
                      {group.code ? `${group.code} · ` : ''}
                      {group.count} lançamento(s) ·{' '}
                      {formatPercent(
                        Number(total) > 0 ? (Number(group.amount) / Number(total)) * 100 : 0,
                        { maximumFractionDigits: 1 },
                      )}{' '}
                      do período
                    </span>
                  </span>
                  <span className="numeric text-body-sm font-semibold text-ink">
                    {formatMoney(group.amount, 'BRL')}
                  </span>
                </li>
              ))}
            </ul>
          </section>

          <Table caption="Proventos de investimento por mês">
            <THead>
              <TR>
                <TH>Mês</TH>
                <TH className="text-right">Proventos</TH>
                <TH className="text-right">Lançamentos</TH>
              </TR>
            </THead>
            <TBody>
              {income.byMonth.map((month) => (
                <TR key={month.month}>
                  <TH scope="row" className="text-left font-medium text-ink">
                    {month.month}
                  </TH>
                  <TD className="numeric text-right text-income">
                    {formatMoney(month.amount, 'BRL')}
                  </TD>
                  <TD className="numeric text-right text-ink-muted">{month.count}</TD>
                </TR>
              ))}
            </TBody>
          </Table>

          <div className="hidden sm:block">
            <Table caption="Proventos de investimento por lançamento">
              <THead>
                <TR>
                  <TH>Data</TH>
                  <TH>Ativo</TH>
                  <TH>Tipo</TH>
                  <TH>Origem</TH>
                  <TH className="text-right">Valor</TH>
                  <TH className="text-right">Ações</TH>
                </TR>
              </THead>
              <TBody>
                {income.items.map((item) => (
                  <TR key={`${item.id ?? 'provider'}-${item.date}-${item.investmentId}`}>
                    <TD className="text-ink-muted">{formatReferenceDate(item.date)}</TD>
                    <TD>
                      <span className="block text-ink">
                        {item.investmentName ?? 'Posição sem nome'}
                      </span>
                      {item.code ? (
                        <span className="text-caption text-ink-muted">{item.code}</span>
                      ) : null}
                    </TD>
                    <TD>{INCOME_TYPE_LABELS[item.incomeType] ?? item.incomeType}</TD>
                    <TD className="text-caption text-ink-muted">
                      {item.source === 'PROVIDER' ? 'Provedor' : 'Manual'}
                    </TD>
                    <TD className="numeric text-right text-ink">
                      {formatMoney(item.amount, 'BRL')}
                    </TD>
                    <TD className="text-right">
                      {item.editable && item.id ? (
                        confirmingId === item.id ? (
                          <span className="inline-flex flex-wrap justify-end gap-2">
                            <Button
                              type="button"
                              size="sm"
                              variant="danger"
                              loading={remove.isPending}
                              loadingLabel="Excluindo…"
                              onClick={() => remove.mutate(item.id ?? '')}
                            >
                              Confirmar
                            </Button>
                            <Button
                              type="button"
                              size="sm"
                              variant="secondary"
                              onClick={() => setConfirmingId(null)}
                            >
                              Cancelar
                            </Button>
                          </span>
                        ) : (
                          <span className="inline-flex flex-wrap justify-end gap-2">
                            <Button
                              type="button"
                              size="sm"
                              variant="secondary"
                              onClick={() => onEdit(item)}
                            >
                              Editar
                            </Button>
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              onClick={() => setConfirmingId(item.id ?? null)}
                            >
                              Excluir
                            </Button>
                          </span>
                        )
                      ) : (
                        <span className="text-caption text-ink-muted">—</span>
                      )}
                    </TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </div>
          <ul className="space-y-2 sm:hidden" aria-label="Proventos por lançamento">
            {income.items.map((item) => (
              <li
                key={`${item.id ?? 'provider'}-${item.date}-${item.investmentId}`}
                className="rounded-card border border-line bg-surface p-3"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-body-sm font-medium text-ink">
                      {item.investmentName ?? 'Posição sem nome'}
                      {item.code ? (
                        <span className="ml-1 text-caption text-ink-muted">{item.code}</span>
                      ) : null}
                    </p>
                    <p className="mt-0.5 text-caption text-ink-muted">
                      {formatReferenceDate(item.date)} ·{' '}
                      {INCOME_TYPE_LABELS[item.incomeType] ?? item.incomeType} ·{' '}
                      {item.source === 'PROVIDER' ? 'Provedor' : 'Manual'}
                    </p>
                  </div>
                  <p className="numeric shrink-0 text-body-sm font-semibold text-ink">
                    {formatMoney(item.amount, 'BRL')}
                  </p>
                </div>
                <div className="mt-2 flex flex-wrap justify-end gap-2">
                  {item.editable && item.id ? (
                    confirmingId === item.id ? (
                      <>
                        <Button
                          type="button"
                          size="sm"
                          variant="danger"
                          loading={remove.isPending}
                          loadingLabel="Excluindo…"
                          onClick={() => remove.mutate(item.id ?? '')}
                        >
                          Confirmar
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          onClick={() => setConfirmingId(null)}
                        >
                          Cancelar
                        </Button>
                      </>
                    ) : (
                      <>
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          onClick={() => onEdit(item)}
                        >
                          Editar
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => setConfirmingId(item.id ?? null)}
                        >
                          Excluir
                        </Button>
                      </>
                    )
                  ) : (
                    <span className="text-caption text-ink-muted">Sem ações</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
