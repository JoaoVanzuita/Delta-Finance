import { useState } from 'react'

import { useMutation } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import {
  financeApi,
  type InvestmentHolding,
  type InvestmentIncome,
  type InvestmentIncomeItem,
} from '@/features/integration/api'

import { formatReferenceDate } from '../format'
import { InvestmentIncomeForm } from './InvestmentIncomeForm'

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
  readonly holdings: readonly InvestmentHolding[]
  readonly onChanged: () => void
}

/** Proventos do provedor e manuais, com origem explícita e edição apenas dos manuais. */
export function InvestmentIncomeSection({
  income,
  holdings,
  onChanged,
}: InvestmentIncomeSectionProps) {
  const [editing, setEditing] = useState<InvestmentIncomeItem | null>(null)
  const [confirmingId, setConfirmingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const max = Math.max(...income.byMonth.map((month) => Math.abs(Number(month.amount))), 0)

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

  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-muted">
        Período: {formatReferenceDate(income.from)} a {formatReferenceDate(income.to)} · Total:{' '}
        <span className="numeric font-semibold text-ink">{formatMoney(income.total, 'BRL')}</span> ·
        Origem:{' '}
        <span className="font-medium text-ink">
          {income.source === 'PROVIDER'
            ? 'provedor'
            : income.source === 'MANUAL'
              ? 'manual'
              : income.source === 'MIXED'
                ? 'provedor e manual'
                : 'não informada'}
        </span>
      </p>

      {editing ? (
        <InvestmentIncomeForm
          holdings={holdings}
          initial={editing}
          onSaved={() => {
            setEditing(null)
            onChanged()
          }}
          onCancel={() => setEditing(null)}
        />
      ) : (
        <details className="rounded-card border border-line">
          <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-ink">
            Registrar provento manual
          </summary>
          <div className="border-t border-line p-3">
            <InvestmentIncomeForm holdings={holdings} onSaved={onChanged} />
          </div>
        </details>
      )}

      {error ? (
        <p role="alert" className="text-sm text-expense">
          {error}
        </p>
      ) : null}

      {income.items.length === 0 ? (
        <div className="rounded-card border border-line bg-surface-muted p-4 text-sm leading-relaxed text-ink-muted">
          Nenhum provento informado pelo provedor ou manualmente no período. O sistema não estima
          dividendos, JCP, aluguel de FII ou cupons sem um lançamento.
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[34rem] text-left text-sm">
              <caption className="sr-only">Proventos de investimento por mês</caption>
              <thead className="border-b border-line bg-surface-muted text-xs uppercase">
                <tr>
                  <th scope="col" className="px-3 py-2">
                    Mês
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Proventos
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Lançamentos
                  </th>
                </tr>
              </thead>
              <tbody>
                {income.byMonth.map((month) => (
                  <tr key={month.month} className="border-b border-line last:border-0">
                    <th scope="row" className="px-3 py-2 font-medium text-ink">
                      {month.month}
                    </th>
                    <td className="px-3 py-2 text-right">
                      <span className="numeric block text-income">
                        {formatMoney(month.amount, 'BRL')}
                      </span>
                      <span
                        aria-hidden="true"
                        className="mt-1 block h-1.5 overflow-hidden rounded-card bg-surface-muted"
                      >
                        <span
                          className="block h-full rounded-card bg-income"
                          style={{
                            width: `${max > 0 ? Math.min(100, (Math.abs(Number(month.amount)) / max) * 100) : 0}%`,
                          }}
                        />
                      </span>
                    </td>
                    <td className="numeric px-3 py-2 text-right text-ink-muted">{month.count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[42rem] text-left text-sm">
              <caption className="sr-only">Proventos de investimento por ativo</caption>
              <thead className="border-b border-line bg-surface-muted text-xs uppercase">
                <tr>
                  <th scope="col" className="px-3 py-2">
                    Data
                  </th>
                  <th scope="col" className="px-3 py-2">
                    Ativo
                  </th>
                  <th scope="col" className="px-3 py-2">
                    Tipo
                  </th>
                  <th scope="col" className="px-3 py-2">
                    Origem
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Valor
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Ações
                  </th>
                </tr>
              </thead>
              <tbody>
                {income.items.map((item) => (
                  <tr
                    key={`${item.id ?? 'provider'}-${item.date}-${item.investmentId}`}
                    className="border-b border-line last:border-0"
                  >
                    <td className="px-3 py-2 text-ink">{formatReferenceDate(item.date)}</td>
                    <td className="px-3 py-2 text-ink">
                      <span className="block">
                        {item.investmentName ?? 'Posição sem nome'}
                        {item.code ? (
                          <span className="ml-2 text-xs text-ink-muted">{item.code}</span>
                        ) : null}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-ink">
                      {INCOME_TYPE_LABELS[item.incomeType] ?? item.incomeType}
                    </td>
                    <td className="px-3 py-2 text-xs text-ink-muted">
                      {item.source === 'PROVIDER' ? 'Provedor' : 'Manual'}
                    </td>
                    <td className="numeric px-3 py-2 text-right text-ink">
                      {formatMoney(item.amount, 'BRL')}
                    </td>
                    <td className="px-3 py-2 text-right">
                      {item.editable && item.id ? (
                        confirmingId === item.id ? (
                          <span className="inline-flex flex-wrap justify-end gap-2">
                            <button
                              type="button"
                              disabled={remove.isPending}
                              onClick={() => remove.mutate(item.id ?? '')}
                              className="inline-flex min-h-9 items-center rounded-card border border-expense/50 bg-expense/5 px-2 text-xs font-semibold text-expense disabled:opacity-60"
                            >
                              Confirmar
                            </button>
                            <button
                              type="button"
                              onClick={() => setConfirmingId(null)}
                              className="inline-flex min-h-9 items-center rounded-card border border-line bg-surface px-2 text-xs font-medium text-ink"
                            >
                              Cancelar
                            </button>
                          </span>
                        ) : (
                          <span className="inline-flex flex-wrap justify-end gap-2">
                            <button
                              type="button"
                              onClick={() => setEditing(item)}
                              className="inline-flex min-h-9 items-center rounded-card border border-line bg-surface px-2 text-xs font-medium text-ink hover:bg-surface-muted"
                            >
                              Editar
                            </button>
                            <button
                              type="button"
                              onClick={() => setConfirmingId(item.id ?? null)}
                              className="inline-flex min-h-9 items-center rounded-card border border-line bg-surface px-2 text-xs font-medium text-ink hover:bg-surface-muted"
                            >
                              Excluir
                            </button>
                          </span>
                        )
                      ) : (
                        <span className="text-xs text-ink-muted">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <p className="text-xs leading-relaxed text-ink-muted">
        Proventos manuais não são somados automaticamente às receitas do dashboard principal. Eles
        aparecem neste painel e no resultado estimado da posição quando há custo conhecido.
      </p>
    </div>
  )
}
